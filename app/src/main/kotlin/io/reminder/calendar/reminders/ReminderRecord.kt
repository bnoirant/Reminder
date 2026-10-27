package io.reminder.calendar.reminders

/** All times are UTC epoch milliseconds. The deadline is immutable after creation. */
data class ReminderRecord(
    val key: String,
    val eventId: Long,
    val occurrence: Long,
    val slot: Int,
    val planned: Long,
    val expires: Long,
    val revision: String,
    val delivery: Long = planned,
    val state: String = SCHEDULED
) {
    fun remaining(now: Long) = if (now >= expires) 0L else expires - now
    fun isTerminal() = state in setOf(DISMISSED, EXPIRED, CANCELLED)
    fun snooze(now: Long, delay: Long): ReminderRecord? {
        if (state != SHOWN || delay <= 0 || delay >= remaining(now)) return null
        return copy(delivery = now + delay, state = SNOOZED)
    }

    companion object {
        const val SCHEDULED = "scheduled"
        const val SHOWN = "shown"
        const val SNOOZED = "snoozed"
        const val DISMISSED = "dismissed"
        const val EXPIRED = "expired"
        const val CANCELLED = "cancelled"
        fun deadline(planned: Long, duration: Long): Long {
            require(duration > 0)
            return Math.addExact(planned, duration)
        }
    }
}
