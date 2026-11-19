package com.chronos.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.Embedded
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Events table — the core definition of an appointment/reminder. */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("categoryId")],
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String?,
    val categoryId: Long?,
    val priority: String,           // Priority.name
    val startTime: LocalTime,
    val endTime: LocalTime?,
    val reminders: String,          // CSV of lead minutes
    val state: String,              // SeriesState.name
    val pauseStart: LocalDate?,
    val pauseEnd: LocalDate?,
    val colorArgb: Int?,
    val createdAt: Long,
    val updatedAt: Long,
)

/** RecurrenceRules table — one row per event (1:1). */
@Entity(
    tableName = "recurrence_rules",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
)
data class RecurrenceRuleEntity(
    @PrimaryKey val eventId: Long,
    val frequency: String,          // Frequency.name
    val interval: Int,
    val daysOfWeek: Set<DayOfWeek>,
    val monthlyMode: String,        // MonthlyMode.name
    val dayOfMonth: Int,
    val weekOfMonth: Int,
    val positionalDay: DayOfWeek,
    val anchorDate: LocalDate,
    val endMode: String,            // EndMode.name
    val until: LocalDate?,
    val count: Int?,
    val missingDayPolicy: String,   // MissingDayPolicy.name
)

/**
 * Exceptions table — a single occurrence of a series that was skipped or
 * moved/edited independently of the rule (spec §5 "editar somente esta
 * ocorrência", §6 "ignorar uma ocorrência").
 */
@Entity(
    tableName = "exceptions",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["eventId", "originalDate"], unique = true)],
)
data class ExceptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val originalDate: LocalDate,    // the occurrence date in the series
    val type: String,               // SKIP or MOVE
    val newDate: LocalDate?,        // MOVE target date (wall-clock)
    val newStartTime: LocalTime?,   // MOVE target time
    val newTitle: String?,
)

/**
 * Notifications table — scheduled-alarm state. One row per armed alarm
 * (event occurrence × lead time), so the app can cancel/recalculate exactly
 * and avoid duplicates (spec §7 "identificadores estáveis").
 */
@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index("eventId"),
        Index(value = ["requestCode"], unique = true),
    ],
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val occurrenceDate: LocalDate,
    val leadMinutes: Int,
    val triggerAtMillis: Long,      // absolute instant the alarm should fire
    val requestCode: Int,           // stable PendingIntent id
    val exact: Boolean,             // whether armed as exact or inexact
)

/**
 * History table — a log of occurrences that reached a terminal/user-set
 * status (completed, skipped, cancelled). Powers statistics (spec §4).
 */
@Entity(
    tableName = "history",
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["eventId", "occurrenceDate"], unique = true),
        Index("occurrenceDate"),
    ],
)
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val occurrenceDate: LocalDate,
    val status: String,             // OccurrenceStatus.name
    val categoryId: Long?,
    val recordedAt: Long,
)

/** Categories table. */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val isDefault: Boolean,
)

/** An event joined with its 1:1 recurrence rule — the repository read shape. */
data class EventWithRule(
    @Embedded val event: EventEntity,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val rule: RecurrenceRuleEntity?,
)
