package com.chronos.app.data.database

import androidx.room.migration.Migration

/**
 * Schema migrations. Version 1 is the initial schema, so the list is empty
 * today. Every future schema change adds a Migration here instead of using
 * destructive fallback, so local data survives upgrades (spec §11).
 *
 * Example for the next version:
 *
 * val MIGRATION_1_2 = object : Migration(1, 2) {
 *     override fun migrate(db: SupportSQLiteDatabase) {
 *         db.execSQL("ALTER TABLE events ADD COLUMN location TEXT")
 *     }
 * }
 */
object Migrations {
    val ALL: Array<Migration> = emptyArray()
}
