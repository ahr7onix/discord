package com.chronos.app.data.database

import androidx.room.TypeConverter
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Room converters. Dates are stored as epoch-day longs and times as
 * minute-of-day ints — compact, index-friendly, and zone-free (the stored
 * value is always wall-clock, consistent with the recurrence model).
 */
class Converters {

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(day: Long?): LocalDate? = day?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun dayOfWeekToInt(day: DayOfWeek?): Int? = day?.value

    @TypeConverter
    fun intToDayOfWeek(value: Int?): DayOfWeek? = value?.let(DayOfWeek::of)

    @TypeConverter
    fun localTimeToMinute(time: LocalTime?): Int? = time?.toSecondOfDay()?.div(60)

    @TypeConverter
    fun minuteToLocalTime(minute: Int?): LocalTime? =
        minute?.let { LocalTime.ofSecondOfDay((it * 60).toLong()) }

    /** Weekday set as a sorted CSV of ISO values (1=Mon..7=Sun). */
    @TypeConverter
    fun daysOfWeekToString(days: Set<DayOfWeek>?): String =
        days.orEmpty().map { it.value }.sorted().joinToString(",")

    @TypeConverter
    fun stringToDaysOfWeek(csv: String?): Set<DayOfWeek> =
        csv?.split(",")
            ?.filter { it.isNotBlank() }
            ?.map { DayOfWeek.of(it.trim().toInt()) }
            ?.toSet()
            ?: emptySet()
}
