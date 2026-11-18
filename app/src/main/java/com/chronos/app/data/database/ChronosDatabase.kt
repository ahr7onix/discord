package com.chronos.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.chronos.app.data.dao.CategoryDao
import com.chronos.app.data.dao.EventDao
import com.chronos.app.data.dao.HistoryDao
import com.chronos.app.data.dao.NotificationDao
import com.chronos.app.data.entities.CategoryEntity
import com.chronos.app.data.entities.EventEntity
import com.chronos.app.data.entities.ExceptionEntity
import com.chronos.app.data.entities.HistoryEntity
import com.chronos.app.data.entities.NotificationEntity
import com.chronos.app.data.entities.RecurrenceRuleEntity
import com.chronos.app.domain.model.Category

@Database(
    entities = [
        EventEntity::class,
        RecurrenceRuleEntity::class,
        ExceptionEntity::class,
        NotificationEntity::class,
        HistoryEntity::class,
        CategoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ChronosDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao
    abstract fun categoryDao(): CategoryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun historyDao(): HistoryDao

    companion object {
        private const val NAME = "chronos.db"

        @Volatile
        private var instance: ChronosDatabase? = null

        fun get(context: Context): ChronosDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }

        private fun build(context: Context): ChronosDatabase =
            Room.databaseBuilder(context, ChronosDatabase::class.java, NAME)
                // Future versions append Migration objects here. Schemas are
                // exported to app/schemas for migration tests (spec §11).
                .addMigrations(*Migrations.ALL)
                .addCallback(SeedCallback(context))
                .build()

        /** Seeds the default categories on first creation. */
        private class SeedCallback(private val context: Context) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                Category.SEED.forEach { c ->
                    db.execSQL(
                        "INSERT INTO categories (name, colorArgb, isDefault) VALUES (?, ?, ?)",
                        arrayOf(c.name, c.colorArgb, if (c.isDefault) 1 else 0),
                    )
                }
            }
        }
    }
}
