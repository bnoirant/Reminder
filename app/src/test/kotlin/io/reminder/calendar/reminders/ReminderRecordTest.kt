package io.reminder.calendar.reminders

import org.junit.Assert.*
import org.junit.Test
import io.reminder.calendar.reminders.ReminderRecord.Companion.SCHEDULED
import io.reminder.calendar.reminders.ReminderRecord.Companion.SHOWN
import io.reminder.calendar.reminders.ReminderRecord.Companion.SNOOZED
import io.reminder.calendar.reminders.ReminderRecord.Companion.DISMISSED
import io.reminder.calendar.reminders.ReminderRecord.Companion.EXPIRED
import io.reminder.calendar.reminders.ReminderRecord.Companion.CANCELLED

class ReminderRecordTest {
    private val hour = 3_600_000L
    private fun record(id: Long = 1, occurrence: Long = 9, slot: Int = 0, duration: Long = 3 * hour) =
        ReminderRecord("$id/$occurrence/$slot", id, occurrence, slot, 9 * hour,
            ReminderRecord.deadline(9 * hour, duration), "revision")

    @Test fun nineOClockThreeHoursExpiresAtNoon() {
        assertEquals(12 * hour, record().expires)
        assertEquals(0L, record().remaining(12 * hour))
        assertEquals(0L, record().remaining(13 * hour))
    }
    @Test fun lateDeliveryAndRefreshUseOnlyRemainingTime() {
        val atEleven = record().copy(state = SHOWN)
        assertEquals(hour, atEleven.remaining(11 * hour))
        assertEquals(12 * hour, atEleven.copy(state = SHOWN).expires)
    }
    @Test fun snoozeKeepsInitialDeadline() {
        val snoozed = record().copy(state = SHOWN).snooze(10 * hour, hour)!!
        assertEquals(11 * hour, snoozed.delivery)
        assertEquals(12 * hour, snoozed.expires)
        assertEquals(SNOOZED, snoozed.state)
    }
    @Test fun snoozeAtOrBeyondDeadlineIsRejected() {
        val shown = record().copy(state = SHOWN)
        assertNull(shown.snooze(11 * hour, hour))
        assertNull(shown.snooze(11 * hour, 2 * hour))
        assertNull(shown.snooze(13 * hour, 1))
        assertNull(shown.snooze(10 * hour, 0))
    }
    @Test fun terminalStatesCannotBeSnoozed() {
        listOf(DISMISSED, EXPIRED, CANCELLED).forEach {
            assertTrue(record().copy(state = it).isTerminal())
            assertNull(record().copy(state = it).snooze(10 * hour, hour))
        }
    }
    @Test fun simultaneousRemindersAndRecurrencesHaveIndependentDeadlines() {
        val a = record(duration = hour)
        val b = record(id = 1L + Int.MAX_VALUE.toLong() * 2 + 2, duration = 4 * hour)
        val c = record(slot = 1, duration = 2 * hour)
        val next = record(occurrence = 33)
        assertEquals(4, setOf(a.key, b.key, c.key, next.key).size)
        assertEquals(0L, a.remaining(11 * hour))
        assertEquals(2 * hour, b.remaining(11 * hour))
    }
    @Test(expected = IllegalArgumentException::class) fun zeroDurationRejected() { ReminderRecord.deadline(0, 0) }
    @Test(expected = IllegalArgumentException::class) fun negativeDurationRejected() { ReminderRecord.deadline(0, -1) }
    @Test(expected = ArithmeticException::class) fun overflowRejected() { ReminderRecord.deadline(Long.MAX_VALUE, 1) }
}
