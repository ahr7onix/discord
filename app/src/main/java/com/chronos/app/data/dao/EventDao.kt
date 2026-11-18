package com.chronos.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.chronos.app.data.entities.EventEntity
import com.chronos.app.data.entities.EventWithRule
import com.chronos.app.data.entities.ExceptionEntity
import com.chronos.app.data.entities.RecurrenceRuleEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface EventDao {

    @Transaction
    @Query("SELECT * FROM events ORDER BY startTime")
    fun observeAllWithRules(): Flow<List<EventWithRule>>

    @Transaction
    @Query("SELECT * FROM events WHERE state != 'CANCELLED' ORDER BY startTime")
    fun observeActiveWithRules(): Flow<List<EventWithRule>>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    fun observeWithRule(id: Long): Flow<EventWithRule?>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getWithRule(id: Long): EventWithRule?

    @Transaction
    @Query("SELECT * FROM events WHERE state != 'CANCELLED'")
    suspend fun getActiveWithRules(): List<EventWithRule>

    @Transaction
    @Query("SELECT * FROM events")
    suspend fun getAllWithRules(): List<EventWithRule>

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: EventEntity): Long

    @Update
    suspend fun updateEvent(event: EventEntity)

    @Upsert
    suspend fun upsertRule(rule: RecurrenceRuleEntity)

    @Query("DELETE FROM events WHERE id = :id")
    suspend fun deleteEvent(id: Long)

    @Query("UPDATE events SET state = :state, pauseStart = :pauseStart, pauseEnd = :pauseEnd, updatedAt = :now WHERE id = :id")
    suspend fun updateState(id: Long, state: String, pauseStart: LocalDate?, pauseEnd: LocalDate?, now: Long)

    /** Insert an event and its rule together. Returns the new event id. */
    @Transaction
    suspend fun insertEventWithRule(event: EventEntity, ruleFor: (Long) -> RecurrenceRuleEntity): Long {
        val id = insertEvent(event)
        upsertRule(ruleFor(id))
        return id
    }

    // ---- exceptions ---------------------------------------------------------

    @Upsert
    suspend fun upsertException(exception: ExceptionEntity)

    @Query("SELECT * FROM exceptions WHERE eventId = :eventId")
    suspend fun getExceptions(eventId: Long): List<ExceptionEntity>

    @Query("SELECT * FROM exceptions WHERE eventId = :eventId")
    fun observeExceptions(eventId: Long): Flow<List<ExceptionEntity>>

    @Query("SELECT * FROM exceptions WHERE eventId = :eventId AND originalDate = :date LIMIT 1")
    suspend fun getException(eventId: Long, date: LocalDate): ExceptionEntity?

    @Delete
    suspend fun deleteException(exception: ExceptionEntity)

    @Query("DELETE FROM exceptions WHERE eventId = :eventId AND originalDate >= :from")
    suspend fun deleteExceptionsFrom(eventId: Long, from: LocalDate)
}
