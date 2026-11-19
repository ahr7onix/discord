package com.chronos.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.chronos.app.data.entities.CategoryEntity
import com.chronos.app.data.entities.HistoryEntity
import com.chronos.app.data.entities.NotificationEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY isDefault DESC, name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications")
    suspend fun getAll(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE eventId = :eventId")
    suspend fun getByEvent(eventId: Long): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notifications: List<NotificationEntity>)

    @Query("DELETE FROM notifications WHERE eventId = :eventId")
    suspend fun deleteByEvent(eventId: Long)

    @Query("DELETE FROM notifications")
    suspend fun deleteAll()

    @Query("SELECT COALESCE(MAX(requestCode), 1000) FROM notifications")
    suspend fun maxRequestCode(): Int
}

@Dao
interface HistoryDao {
    @Upsert
    suspend fun upsert(entry: HistoryEntity)

    @Query("SELECT * FROM history WHERE eventId = :eventId AND occurrenceDate = :date LIMIT 1")
    suspend fun get(eventId: Long, date: LocalDate): HistoryEntity?

    @Query("DELETE FROM history WHERE eventId = :eventId AND occurrenceDate = :date")
    suspend fun delete(eventId: Long, date: LocalDate)

    @Query("SELECT * FROM history ORDER BY occurrenceDate DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history")
    suspend fun getAllOnce(): List<HistoryEntity>

    @androidx.room.Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<HistoryEntity>)

    @Query(
        """
        SELECT * FROM history
        WHERE occurrenceDate BETWEEN :from AND :to
        ORDER BY occurrenceDate DESC
        """
    )
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<HistoryEntity>>

    /** Count of a status in a date window — used by the statistics screen. */
    @Query(
        """
        SELECT COUNT(*) FROM history
        WHERE status = :status AND occurrenceDate BETWEEN :from AND :to
        """
    )
    fun countByStatusBetween(status: String, from: LocalDate, to: LocalDate): Flow<Int>

    @Query(
        """
        SELECT categoryId AS categoryId, COUNT(*) AS total
        FROM history
        WHERE status = :status AND occurrenceDate BETWEEN :from AND :to
        GROUP BY categoryId
        """
    )
    fun countByCategoryBetween(status: String, from: LocalDate, to: LocalDate): Flow<List<CategoryCount>>
}

/** Projection for grouped history counts. */
data class CategoryCount(val categoryId: Long?, val total: Int)
