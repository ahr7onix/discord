package com.chronos.app.domain.model

/**
 * Base calendar unit a recurrence repeats on. Specific product rules
 * (quinzenal, "dias específicos", positional monthly) are expressed as a
 * combination of [Frequency] + interval + day selectors, not as new units.
 */
enum class Frequency {
    /** No repetition. The event is a single occurrence. */
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

/** How a monthly recurrence pins the day. */
enum class MonthlyMode {
    /** Fixed day number, e.g. day 10 or day 31. */
    DAY_OF_MONTH,
    /** Positional, e.g. "second Monday" or "last Friday". */
    DAY_OF_WEEK_POSITION,
}

/** When a series stops producing occurrences. */
enum class EndMode {
    /** Infinite — repeats until the user pauses or cancels. */
    NEVER,
    /** Stops after the inclusive [RecurrenceRule.until] date. */
    UNTIL_DATE,
    /** Stops after [RecurrenceRule.count] occurrences have been produced. */
    COUNT,
}

/**
 * Policy for a DAY_OF_MONTH rule when the target day does not exist in a
 * given month (e.g. day 31 in February). Predictable and independently
 * testable, as required by the spec.
 */
enum class MissingDayPolicy {
    /** Produce no occurrence that month (RFC 5545 semantics). Default. */
    SKIP,
    /** Clamp to the last valid day of that month. */
    LAST_DAY,
}

/** Priority of an event. Drives sorting and accent color in the UI. */
enum class Priority {
    LOW,
    NORMAL,
    HIGH,
}

/** Lifecycle state of a concrete occurrence, as recorded in History. */
enum class OccurrenceStatus {
    PENDING,
    COMPLETED,
    SKIPPED,
    CANCELLED,
    SNOOZED,
}

/** The scope a user picks when editing or deleting a recurring event. */
enum class EditScope {
    /** Only the single occurrence being edited. */
    THIS_ONLY,
    /** This occurrence and every later one (splits the series). */
    THIS_AND_FUTURE,
    /** The entire series. */
    ALL,
}
