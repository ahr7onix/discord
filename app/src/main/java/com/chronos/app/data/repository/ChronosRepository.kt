package com.chronos.app.data.repository

import com.chronos.app.data.dao.CategoryDao
import com.chronos.app.data.dao.EventDao
import com.chronos.app.data.dao.HistoryDao
import com.chronos.app.data.entities.ExceptionEntity
import com.chronos.app.data.entities.HistoryEntity
import com.chronos.app.domain.model.Category
import com.chronos.app.domain.model.EditScope
import com.chronos.app.domain.model.EndMode
import com.chronos.app.domain.model.Event
import com.chronos.app.domain.model.EventOccurrence
import com.chronos.app.domain.model.OccurrenceStatus
import com.chronos.app.domain.model.SeriesState
import com.chronos.app.domain.scheduler.EventScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Single source of truth over the Room DAOs. Owns occurrence expansion
 * (engine output overlaid with exceptions and history status) and the
 * edit-scope semantics of §5, and keeps alarms in sync via [scheduler].
 */
class ChronosRepository(
    private val eventDao: EventDao,
    private val categoryDao: CategoryDao,
    private val historyDao: HistoryDao,
    private val scheduler: EventScheduler,
) {
    // ---- reads --------------------------------------------------------------

    val events: Flow<List<Event>> =
        eventDao.observeAllWithRules().map { list -> list.map(Mappers::toDomain) }

    val categories: Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map(Mappers::toDomainCategory) }

    fun observeEvent(id: Long): Flow<Event?> =
        eventDao.observeWithRule(id).map { it?.let(Mappers::toDomain) }

    suspend fun getEvent(id: Long): Event? = eventDao.getWithRule(id)?.let(Mappers::toDomain)

    /**
     * Expanded occurrences in [from, to], overlaid with exceptions (skip/move)
     * and history status, sorted by start time. This is the calendar/agenda
     * read model.
     */
    fun occurrences(from: LocalDate, to: LocalDate): Flow<List<EventOccurrence>> =
        combine(
            eventDao.observeActiveWithRules(),
            historyDao.observeBetween(from, to),
        ) { eventRows, history ->
            val historyByKey = history.associateBy { it.eventId to it.occurrenceDate }
            buildList {
                for (row in eventRows) {
                    val event = Mappers.toDomain(row)
                    val exceptions = eventDao.getExceptions(event.id)
                    val skipDates = exceptions.filter { it.type == "SKIP" }.map { it.originalDate }.toSet()
                    val moves = exceptions.filter { it.type == "MOVE" }

                    // Base occurrences from the rule, minus skipped & moved-away.
                    val movedAway = moves.map { it.originalDate }.toSet()
                    event.occurrenceDatesBetween(from, to)
                        .filterNot { it in skipDates || it in movedAway }
                        .forEach { date ->
                            val status = historyByKey[event.id to date]?.let {
                                OccurrenceStatus.valueOf(it.status)
                            } ?: OccurrenceStatus.PENDING
                            add(EventOccurrence(event, date, status))
                        }

                    // Moved occurrences that now land inside the window.
                    moves.forEach { mv ->
                        val newDate = mv.newDate ?: return@forEach
                        if (newDate in from..to) {
                            val time = mv.newStartTime ?: event.startTime
                            add(
                                EventOccurrence(
                                    event = mv.newTitle?.let { event.copy(title = it) } ?: event,
                                    date = newDate,
                                    overriddenStart = newDate.atTime(time),
                                )
                            )
                        }
                    }
                }
            }.sortedBy { it.start }
        }

    // ---- writes -------------------------------------------------------------

    /** Create or update an event with its rule, then (re)arm alarms. */
    suspend fun saveEvent(event: Event): Long {
        val id = if (event.id == 0L) {
            eventDao.insertEventWithRule(Mappers.toEntity(event)) { newId ->
                Mappers.toRuleEntity(newId, event.rule)
            }
        } else {
            eventDao.updateEvent(Mappers.toEntity(event.copy(updatedAt = System.currentTimeMillis())))
            eventDao.upsertRule(Mappers.toRuleEntity(event.id, event.rule))
            event.id
        }
        getEvent(id)?.let { scheduler.reschedule(it) }
        return id
    }

    suspend fun deleteEvent(id: Long) {
        scheduler.cancelAll(id)
        eventDao.deleteEvent(id) // cascades rules/exceptions/notifications/history
    }

    /** "Ignorar uma ocorrência" — skip a single date without ending the series. */
    suspend fun skipOccurrence(eventId: Long, date: LocalDate) {
        eventDao.upsertException(
            ExceptionEntity(eventId = eventId, originalDate = date, type = "SKIP",
                newDate = null, newStartTime = null, newTitle = null)
        )
        getEvent(eventId)?.let { scheduler.reschedule(it) }
    }

    /** Edit a single occurrence (§5 "editar somente esta ocorrência"). */
    suspend fun moveOccurrence(
        eventId: Long,
        originalDate: LocalDate,
        newDate: LocalDate,
        newStartTime: java.time.LocalTime?,
        newTitle: String?,
    ) {
        eventDao.upsertException(
            ExceptionEntity(
                eventId = eventId, originalDate = originalDate, type = "MOVE",
                newDate = newDate, newStartTime = newStartTime, newTitle = newTitle,
            )
        )
        getEvent(eventId)?.let { scheduler.reschedule(it) }
    }

    /**
     * Apply an edit at the chosen [scope]. THIS_AND_FUTURE splits the series:
     * the original is capped the day before [pivot] and a new series starts at
     * [pivot] with [updated]'s fields.
     */
    suspend fun applyEdit(original: Event, updated: Event, scope: EditScope, pivot: LocalDate): Long {
        return when (scope) {
            EditScope.ALL -> saveEvent(updated.copy(id = original.id))
            EditScope.THIS_ONLY -> {
                moveOccurrence(
                    eventId = original.id,
                    originalDate = pivot,
                    newDate = pivot,
                    newStartTime = updated.startTime,
                    newTitle = updated.title,
                )
                original.id
            }
            EditScope.THIS_AND_FUTURE -> {
                // Cap the old series the day before the pivot.
                val cappedRule = original.rule.copy(
                    endMode = EndMode.UNTIL_DATE,
                    until = pivot.minusDays(1),
                )
                saveEvent(original.copy(rule = cappedRule))
                // Start a fresh series at the pivot with the new fields.
                val newRule = updated.rule.copy(anchorDate = pivot, endMode = updated.rule.endMode)
                saveEvent(updated.copy(id = 0, rule = newRule))
            }
        }
    }

    // ---- series lifecycle (§5) ---------------------------------------------

    suspend fun pauseSeries(eventId: Long, from: LocalDate?, to: LocalDate?) {
        eventDao.updateState(eventId, SeriesState.PAUSED.name, from, to, System.currentTimeMillis())
        getEvent(eventId)?.let { scheduler.reschedule(it) }
    }

    suspend fun resumeSeries(eventId: Long) {
        eventDao.updateState(eventId, SeriesState.ACTIVE.name, null, null, System.currentTimeMillis())
        getEvent(eventId)?.let { scheduler.reschedule(it) }
    }

    suspend fun cancelSeries(eventId: Long) {
        scheduler.cancelAll(eventId)
        eventDao.updateState(eventId, SeriesState.CANCELLED.name, null, null, System.currentTimeMillis())
    }

    // ---- history / status ---------------------------------------------------

    suspend fun markStatus(event: Event, date: LocalDate, status: OccurrenceStatus) {
        historyDao.upsert(
            HistoryEntity(
                eventId = event.id,
                occurrenceDate = date,
                status = status.name,
                categoryId = event.categoryId,
                recordedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun clearStatus(eventId: Long, date: LocalDate) = historyDao.delete(eventId, date)

    // ---- categories ---------------------------------------------------------

    suspend fun saveCategory(category: Category): Long =
        categoryDao.insert(Mappers.toEntity(category))

    suspend fun deleteCategory(id: Long) = categoryDao.delete(id)

    suspend fun ensureSeedCategories() {
        if (categoryDao.count() == 0) {
            categoryDao.insertAll(Category.SEED.map(Mappers::toEntity))
        }
    }

    // ---- statistics ---------------------------------------------------------

    fun completedCount(from: LocalDate, to: LocalDate): Flow<Int> =
        historyDao.countByStatusBetween(OccurrenceStatus.COMPLETED.name, from, to)

    fun completedByCategory(from: LocalDate, to: LocalDate) =
        historyDao.countByCategoryBetween(OccurrenceStatus.COMPLETED.name, from, to)
}
