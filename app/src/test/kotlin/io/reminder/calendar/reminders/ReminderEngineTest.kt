package io.reminder.calendar.reminders

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.reminder.calendar.databases.EventsDatabase
import io.reminder.calendar.extensions.*
import io.reminder.calendar.helpers.DAY
import io.reminder.calendar.helpers.REGULAR_EVENT_TYPE_ID
import io.reminder.calendar.models.Event
import io.reminder.calendar.models.EventType
import io.reminder.calendar.reminders.ReminderRecord.Companion.SCHEDULED
import io.reminder.calendar.reminders.ReminderRecord.Companion.SHOWN
import io.reminder.calendar.reminders.ReminderRecord.Companion.SNOOZED
import io.reminder.calendar.reminders.ReminderRecord.Companion.DISMISSED
import io.reminder.calendar.reminders.ReminderRecord.Companion.EXPIRED
import io.reminder.calendar.reminders.ReminderRecord.Companion.CANCELLED
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReminderEngineTest {
    @get:Rule val databaseThread = org.junit.rules.TestRule { statement, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
                try {
                    executor.submit { statement.evaluate() }.get()
                } catch (e: java.util.concurrent.ExecutionException) { throw e.cause!! }
                finally { executor.shutdownNow() }
            }
        }
    }

    private lateinit var context: Context
    private lateinit var engine: ReminderEngine
    private lateinit var notifications: NotificationManager
    private val hour = 3_600_000L

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        EventsDatabase.destroyInstance()
        context.deleteDatabase("events.db")
        context.deleteDatabase("reminders.db")
        engine = ReminderEngine(context)
        notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifications.cancelAll()
        context.eventsDB.getAllEvents()
        context.eventTypesDB.insertOrUpdate(EventType(REGULAR_EVENT_TYPE_ID, "Events", 0xff1565c0.toInt()))
    }
    @After fun cleanup() {
        engine.store.close()
        EventsDatabase.getInstance(context).close()
        EventsDatabase.destroyInstance()
    }
    private fun event(id: Long = 1, age: Long = hour): Event {
        val start = (System.currentTimeMillis() - age) / 1000
        return Event(id, startTS = start, endTS = start + 60, title = "Reminder", reminder1Minutes = 0,
            notificationVisibilityMs = 3 * hour).also { context.eventsDB.insertOrUpdate(it) }
    }
    private fun due(e: Event, slot: Int = 0, duration: Long = 3 * hour): ReminderRecord {
        val planned = e.startTS * 1000
        val revision = ReminderEngine.revision(e)
        return ReminderRecord("${e.id}/${e.startTS}/$slot/$planned/$revision", e.id!!, e.startTS, slot,
            planned, planned + duration, revision).also { engine.store.insert(it) }
    }

    @Test fun delayedPostAndRefreshKeepDeadline() {
        val r = due(event(age = 2 * hour))
        engine.receive(r.key)
        val first = notifications.activeNotifications.single().notification.timeoutAfter
        assertTrue(first in (hour - 10_000)..hour)
        engine.recover(false)
        assertEquals(r.expires, engine.store.get(r.key)!!.expires)
        assertTrue(notifications.activeNotifications.single().notification.timeoutAfter <= first)
    }
    @Test fun expiredReminderDoesNotReturnAtReboot() {
        val r = due(event(age = 4 * hour))
        engine.recover(true)
        assertEquals(EXPIRED, engine.store.get(r.key)!!.state)
        assertTrue(notifications.activeNotifications.isEmpty())
    }
    @Test fun manualDismissalSurvivesDatabaseReopenAndReboot() {
        val r = due(event())
        engine.receive(r.key)
        engine.dismiss(r.key)
        engine.store.close()
        engine = ReminderEngine(context)
        engine.recover(true)
        assertEquals(DISMISSED, engine.store.get(r.key)!!.state)
        assertTrue(notifications.activeNotifications.isEmpty())
    }
    @Test fun validShownReminderIsRestoredSilentlyWithRemainingTime() {
        val r = due(event())
        engine.receive(r.key)
        notifications.cancelAll() // Reboot clears the Android notification tray.
        engine.recover(true)
        assertEquals(SHOWN, engine.store.get(r.key)!!.state)
        assertTrue(notifications.activeNotifications.single().notification.timeoutAfter <= 2 * hour)
    }
    @Test fun concurrentEventsAndReminderSlotsDoNotCollide() {
        val e = event()
        val a = due(e)
        val b = due(e, 1, 2 * hour)
        val c = due(event(4_294_967_297L))
        listOf(a, b, c).forEach { engine.receive(it.key) }
        assertEquals(3, notifications.activeNotifications.size)
        assertNotEquals(engine.pending(a.key, ReminderEngine.ACTION_DELIVER), engine.pending(b.key, ReminderEngine.ACTION_DELIVER))
        engine.dismiss(a.key)
        assertEquals(2, notifications.activeNotifications.size)
    }
    @Test fun snoozeAndDuplicateDeliveryCannotExtendDeadline() {
        val r = due(event())
        engine.receive(r.key)
        assertFalse(engine.snooze(r.key, 3 * hour))
        assertTrue(engine.snooze(r.key, 60_000))
        engine.receive(r.key) // A stale original delivery must not defeat the snooze.
        assertTrue(notifications.activeNotifications.isEmpty())
        assertEquals(r.expires, engine.store.get(r.key)!!.expires)
        assertEquals(SNOOZED, engine.store.get(r.key)!!.state)
    }
    @Test fun deletedAndChangedEventsInvalidateOldAlarms() {
        val e = event()
        val r = due(e)
        context.eventsDB.deleteEvents(listOf(e.id!!))
        engine.receive(r.key)
        assertEquals(CANCELLED, engine.store.get(r.key)!!.state)
        assertTrue(notifications.activeNotifications.isEmpty())
    }
    @Test fun metadataRefreshKeepsDismissalAndInitialDeadline() {
        val e = event()
        val r = due(e)
        engine.receive(r.key)
        engine.dismiss(r.key)
        val updated = e.copy(title = "Updated title", lastUpdated = System.currentTimeMillis())
        context.eventsDB.insertOrUpdate(updated)
        engine.schedule(updated)
        engine.recover(true)
        assertEquals(r.expires, engine.store.get(r.key)!!.expires)
        assertTrue(notifications.activeNotifications.isEmpty())
    }
    @Test fun permissionRefusalDoesNotReappearAfterGrant() {
        val r = due(event())
        org.robolectric.Shadows.shadowOf(notifications).setNotificationsEnabled(false)
        engine.receive(r.key)
        assertEquals(DISMISSED, engine.store.get(r.key)!!.state)
        org.robolectric.Shadows.shadowOf(notifications).setNotificationsEnabled(true)
        engine.recover(true)
        assertTrue(notifications.activeNotifications.isEmpty())
    }
    @Test fun recurrenceExceptionCancelsVisibleOccurrence() {
        val e = event(age = 60_000).copy(repeatInterval = DAY)
        context.eventsDB.insertOrUpdate(e)
        val r = due(e)
        engine.receive(r.key)
        val changed = e.copy(repetitionExceptions = listOf(io.reminder.calendar.helpers.Formatter.getDayCodeFromTS(e.startTS)))
        context.eventsDB.insertOrUpdate(changed)
        engine.schedule(changed)
        assertEquals(CANCELLED, engine.store.get(r.key)!!.state)
        assertTrue(notifications.activeNotifications.isEmpty())
    }
    @Test fun movingFutureEventAwayAndBackRearmsItsOriginalReminder() {
        val original = event(age = -hour)
        engine.schedule(original)
        val first = engine.store.all().single()
        val moved = original.copy(startTS = original.startTS + 3600, endTS = original.endTS + 3600)
        context.eventsDB.insertOrUpdate(moved)
        engine.schedule(moved)
        assertEquals(CANCELLED, engine.store.get(first.key)!!.state)
        context.eventsDB.insertOrUpdate(original)
        engine.schedule(original)
        assertEquals(SCHEDULED, engine.store.get(first.key)!!.state)
        assertEquals(first.expires, engine.store.get(first.key)!!.expires)
    }
    @Test fun changingGlobalDurationDoesNotRewriteRegisteredDeadlines() {
        val e = event(age = -hour).copy(notificationVisibilityMs = null)
        context.eventsDB.insertOrUpdate(e)
        val initial = context.config.notificationVisibilityMs
        try {
            context.config.notificationVisibilityMs = 4 * hour
            engine.schedule(e)
            val r = engine.store.all().single()
            context.config.notificationVisibilityMs = hour
            engine.schedule(e)
            assertEquals(r.expires, engine.store.get(r.key)!!.expires)
            assertEquals(r.planned + 4 * hour, r.expires)
        } finally { context.config.notificationVisibilityMs = initial }
    }
    @Test fun expiryOfOneReminderDoesNotCancelAnotherTag() {
        val e = event(age = 2 * hour).copy(reminder2Minutes = 0)
        context.eventsDB.insertOrUpdate(e)
        val expired = due(e, 0, hour)
        val valid = due(e, 1, 3 * hour)
        engine.receive(valid.key)
        engine.receive(expired.key, true)
        assertEquals(EXPIRED, engine.store.get(expired.key)!!.state)
        assertEquals(valid.key, notifications.activeNotifications.single().tag)
    }
    @Test fun timezoneChangeKeepsAnExistingDeadline() {
        val r = due(event())
        engine.receive(r.key)
        val oldZone = org.joda.time.DateTimeZone.getDefault()
        try {
            org.joda.time.DateTimeZone.setDefault(org.joda.time.DateTimeZone.forID("Pacific/Auckland"))
            engine.recover(false)
            assertEquals(r.expires, engine.store.get(r.key)!!.expires)
            assertTrue(notifications.activeNotifications.single().notification.timeoutAfter <= 2 * hour)
        } finally { org.joda.time.DateTimeZone.setDefault(oldZone) }
    }
    @Test fun recurringEventSchedulesIndependentOccurrences() {
        val e = event(age = 60_000).copy(repeatInterval = DAY)
        context.eventsDB.insertOrUpdate(e)
        engine.schedule(e)
        val records = engine.store.all().filter { it.eventId == e.id }
        assertTrue(records.map { it.occurrence }.distinct().size >= 2)
        assertTrue(records.all { it.expires == it.planned + 3 * hour })
    }
}
