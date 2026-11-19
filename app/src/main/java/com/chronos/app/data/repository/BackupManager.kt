package com.chronos.app.data.repository

import com.chronos.app.data.dao.CategoryDao
import com.chronos.app.data.dao.EventDao
import com.chronos.app.data.dao.HistoryDao
import com.chronos.app.data.dao.NotificationDao
import com.chronos.app.data.database.Converters
import com.chronos.app.data.entities.CategoryEntity
import com.chronos.app.data.entities.EventEntity
import com.chronos.app.data.entities.ExceptionEntity
import com.chronos.app.data.entities.HistoryEntity
import com.chronos.app.data.entities.RecurrenceRuleEntity
import com.chronos.app.domain.scheduler.EventScheduler
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalTime

/**
 * Local JSON backup and restore (spec §11). Export/import is fully local — no
 * network, no server. Restore is transactional at the app level: it replaces
 * the current data set and then rebuilds all alarms.
 */
class BackupManager(
    private val eventDao: EventDao,
    private val categoryDao: CategoryDao,
    private val historyDao: HistoryDao,
    private val notificationDao: NotificationDao,
    private val scheduler: EventScheduler,
) {
    private val conv = Converters()

    suspend fun exportTo(out: OutputStream) {
        val root = JSONObject()
        root.put("format", "chronos-backup")
        root.put("version", VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("categories", JSONArray().apply {
            categoryDao.getAll().forEach { put(categoryJson(it)) }
        })

        val events = JSONArray()
        val exceptions = JSONArray()
        eventDao.getAllWithRules().forEach { ewr ->
            events.put(eventJson(ewr.event, ewr.rule))
            eventDao.getExceptions(ewr.event.id).forEach { exceptions.put(exceptionJson(it)) }
        }
        root.put("events", events)
        root.put("exceptions", exceptions)

        root.put("history", JSONArray().apply {
            historyDao.getAllOnce().forEach { put(historyJson(it)) }
        })

        out.bufferedWriter(Charsets.UTF_8).use { it.write(root.toString(2)) }
    }

    suspend fun importFrom(input: InputStream) {
        val text = input.bufferedReader(Charsets.UTF_8).readText()
        val root = JSONObject(text)
        require(root.optString("format") == "chronos-backup") { "Arquivo de backup inválido" }

        // Clear current data (events cascade their rules/exceptions/history).
        scheduler.rescheduleAll() // ensure no pending alarm rows block, then wipe
        notificationDao.deleteAll()
        eventDao.deleteAllEvents()

        root.optJSONArray("categories")?.let { arr ->
            for (i in 0 until arr.length()) categoryDao.insert(parseCategory(arr.getJSONObject(i)))
        }
        root.optJSONArray("events")?.let { arr ->
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val (event, rule) = parseEvent(obj)
                eventDao.insertEvent(event)
                eventDao.upsertRule(rule)
            }
        }
        root.optJSONArray("exceptions")?.let { arr ->
            for (i in 0 until arr.length()) eventDao.upsertException(parseException(arr.getJSONObject(i)))
        }
        root.optJSONArray("history")?.let { arr ->
            val list = ArrayList<HistoryEntity>(arr.length())
            for (i in 0 until arr.length()) list.add(parseHistory(arr.getJSONObject(i)))
            historyDao.insertAll(list)
        }

        scheduler.rescheduleAll()
    }

    // ---- serialization ------------------------------------------------------

    private fun categoryJson(c: CategoryEntity) = JSONObject().apply {
        put("id", c.id); put("name", c.name); put("colorArgb", c.colorArgb); put("isDefault", c.isDefault)
    }

    private fun eventJson(e: EventEntity, r: RecurrenceRuleEntity?) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("description", e.description ?: JSONObject.NULL)
        put("categoryId", e.categoryId ?: JSONObject.NULL); put("priority", e.priority)
        put("startTime", conv.localTimeToMinute(e.startTime))
        put("endTime", e.endTime?.let { conv.localTimeToMinute(it) } ?: JSONObject.NULL)
        put("reminders", e.reminders); put("state", e.state)
        put("pauseStart", e.pauseStart?.toEpochDay() ?: JSONObject.NULL)
        put("pauseEnd", e.pauseEnd?.toEpochDay() ?: JSONObject.NULL)
        put("colorArgb", e.colorArgb ?: JSONObject.NULL)
        put("createdAt", e.createdAt); put("updatedAt", e.updatedAt)
        if (r != null) put("rule", ruleJson(r))
    }

    private fun ruleJson(r: RecurrenceRuleEntity) = JSONObject().apply {
        put("frequency", r.frequency); put("interval", r.interval)
        put("daysOfWeek", conv.daysOfWeekToString(r.daysOfWeek))
        put("monthlyMode", r.monthlyMode); put("dayOfMonth", r.dayOfMonth)
        put("weekOfMonth", r.weekOfMonth); put("positionalDay", r.positionalDay.value)
        put("anchorDate", r.anchorDate.toEpochDay()); put("endMode", r.endMode)
        put("until", r.until?.toEpochDay() ?: JSONObject.NULL)
        put("count", r.count ?: JSONObject.NULL); put("missingDayPolicy", r.missingDayPolicy)
    }

    private fun exceptionJson(x: ExceptionEntity) = JSONObject().apply {
        put("id", x.id); put("eventId", x.eventId); put("originalDate", x.originalDate.toEpochDay())
        put("type", x.type); put("newDate", x.newDate?.toEpochDay() ?: JSONObject.NULL)
        put("newStartTime", x.newStartTime?.let { conv.localTimeToMinute(it) } ?: JSONObject.NULL)
        put("newTitle", x.newTitle ?: JSONObject.NULL)
    }

    private fun historyJson(h: HistoryEntity) = JSONObject().apply {
        put("id", h.id); put("eventId", h.eventId); put("occurrenceDate", h.occurrenceDate.toEpochDay())
        put("status", h.status); put("categoryId", h.categoryId ?: JSONObject.NULL)
        put("recordedAt", h.recordedAt)
    }

    // ---- parsing ------------------------------------------------------------

    private fun parseCategory(o: JSONObject) = CategoryEntity(
        id = o.getLong("id"), name = o.getString("name"),
        colorArgb = o.getInt("colorArgb"), isDefault = o.getBoolean("isDefault"),
    )

    private fun parseEvent(o: JSONObject): Pair<EventEntity, RecurrenceRuleEntity> {
        val id = o.getLong("id")
        val event = EventEntity(
            id = id, title = o.getString("title"),
            description = o.optStringOrNull("description"),
            categoryId = o.optLongOrNull("categoryId"),
            priority = o.getString("priority"),
            startTime = LocalTime.ofSecondOfDay(o.getInt("startTime") * 60L),
            endTime = o.optIntOrNull("endTime")?.let { LocalTime.ofSecondOfDay(it * 60L) },
            reminders = o.getString("reminders"), state = o.getString("state"),
            pauseStart = o.optLongOrNull("pauseStart")?.let(LocalDate::ofEpochDay),
            pauseEnd = o.optLongOrNull("pauseEnd")?.let(LocalDate::ofEpochDay),
            colorArgb = o.optIntOrNull("colorArgb"),
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )
        val ro = o.getJSONObject("rule")
        val rule = RecurrenceRuleEntity(
            eventId = id, frequency = ro.getString("frequency"), interval = ro.getInt("interval"),
            daysOfWeek = conv.stringToDaysOfWeek(ro.getString("daysOfWeek")),
            monthlyMode = ro.getString("monthlyMode"), dayOfMonth = ro.getInt("dayOfMonth"),
            weekOfMonth = ro.getInt("weekOfMonth"),
            positionalDay = java.time.DayOfWeek.of(ro.getInt("positionalDay")),
            anchorDate = LocalDate.ofEpochDay(ro.getLong("anchorDate")),
            endMode = ro.getString("endMode"),
            until = ro.optLongOrNull("until")?.let(LocalDate::ofEpochDay),
            count = ro.optIntOrNull("count"), missingDayPolicy = ro.getString("missingDayPolicy"),
        )
        return event to rule
    }

    private fun parseException(o: JSONObject) = ExceptionEntity(
        id = o.getLong("id"), eventId = o.getLong("eventId"),
        originalDate = LocalDate.ofEpochDay(o.getLong("originalDate")), type = o.getString("type"),
        newDate = o.optLongOrNull("newDate")?.let(LocalDate::ofEpochDay),
        newStartTime = o.optIntOrNull("newStartTime")?.let { LocalTime.ofSecondOfDay(it * 60L) },
        newTitle = o.optStringOrNull("newTitle"),
    )

    private fun parseHistory(o: JSONObject) = HistoryEntity(
        id = o.getLong("id"), eventId = o.getLong("eventId"),
        occurrenceDate = LocalDate.ofEpochDay(o.getLong("occurrenceDate")),
        status = o.getString("status"), categoryId = o.optLongOrNull("categoryId"),
        recordedAt = o.optLong("recordedAt", System.currentTimeMillis()),
    )

    private fun JSONObject.optStringOrNull(k: String): String? =
        if (isNull(k) || !has(k)) null else getString(k)
    private fun JSONObject.optLongOrNull(k: String): Long? =
        if (isNull(k) || !has(k)) null else getLong(k)
    private fun JSONObject.optIntOrNull(k: String): Int? =
        if (isNull(k) || !has(k)) null else getInt(k)

    companion object { const val VERSION = 1 }
}
