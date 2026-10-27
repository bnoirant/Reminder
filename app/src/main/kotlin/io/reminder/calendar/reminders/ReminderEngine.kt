package io.reminder.calendar.reminders

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationManagerCompat
import com.simplemobiletools.commons.helpers.YEAR_SECONDS
import io.reminder.calendar.extensions.*
import io.reminder.calendar.helpers.*
import io.reminder.calendar.models.Event
import io.reminder.calendar.receivers.NotificationReceiver
import io.reminder.calendar.reminders.ReminderRecord.Companion.SCHEDULED
import io.reminder.calendar.reminders.ReminderRecord.Companion.SHOWN
import io.reminder.calendar.reminders.ReminderRecord.Companion.SNOOZED
import io.reminder.calendar.reminders.ReminderRecord.Companion.DISMISSED
import io.reminder.calendar.reminders.ReminderRecord.Companion.EXPIRED
import io.reminder.calendar.reminders.ReminderRecord.Companion.CANCELLED
import java.security.MessageDigest

/** Serialized with event scheduling, dismissal, expiry and snooze to prevent resurrection races. */
class ReminderEngine internal constructor(private val context: Context) {
    val store = ReminderStore(context)
    private val notifications get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Synchronized fun schedule(event: Event, refreshShown: Boolean = true) {
        val id = event.id ?: return
        val revision = revision(event)
        store.forEvent(id).filter { !it.isTerminal() && (it.revision != revision || occurrence(it) == null) }
            .forEach { finish(it, CANCELLED) }
        val slots = listOf(event.reminder1Minutes to event.reminder1Type,
            event.reminder2Minutes to event.reminder2Type, event.reminder3Minutes to event.reminder3Type)
        if (slots.none { it.first != REMINDER_OFF && it.second == REMINDER_NOTIFICATION }) return
        val duration = event.notificationVisibilityMs ?: context.config.notificationVisibilityMs
        val now = System.currentTimeMillis()
        // Include occurrences missed while switched off, including events that have already ended.
        val lookback = duration / 1000 + slots.maxOf { kotlin.math.abs(it.first.toLong()) } * 60 + 1
        val horizon = maxOf(event.startTS + 1, now / 1000 + maxOf(YEAR_SECONDS.toLong(), event.repeatInterval * 2L) +
            slots.maxOf { it.first.coerceAtLeast(0).toLong() } * 60)
        context.eventsHelper.getEventsSync((now / 1000 - lookback).coerceAtLeast(0), horizon,
            id, false) { occurrences ->
            slots.forEachIndexed { slot, (minutes, type) ->
                if (minutes == REMINDER_OFF || type != REMINDER_NOTIFICATION) return@forEachIndexed
                for (occurrence in occurrences.sortedBy { it.startTS }) {
                    if (occurrence.isTaskCompleted()) continue
                    val planned = (occurrence.getEventStartTS() - minutes.toLong() * 60) * 1000
                    val expires = ReminderRecord.deadline(planned, duration)
                    if (expires <= now) continue
                    val key = "$id/${occurrence.startTS}/$slot/$planned/$revision"
                    store.insert(ReminderRecord(key, id, occurrence.startTS, slot, planned, expires, revision))
                    var record = store.get(key)!!
                    // Moving an event away and back may reuse a future schedule identity.
                    // Dismissed/expired reminders remain terminal; only cancelled future alarms can return.
                    if (record.state == CANCELLED && planned > now) {
                        record = record.copy(state = SCHEDULED, delivery = record.planned)
                        store.save(record)
                    }
                    if (!record.isTerminal()) {
                        if (record.state == SHOWN) {
                            // A regular reschedule must never repost a dismissed/auto-cancelled notification.
                            if (notifications.activeNotifications.none { it.tag == key }) finish(record, DISMISSED)
                            else if (refreshShown) display(record, true) else arm(record, true)
                        } else arm(record)
                    }
                    // One future occurrence per reminder; receiving it schedules the following one.
                    if (planned > now) break
                }
            }
        }
    }

    @Synchronized fun receive(key: String, expire: Boolean = false) {
        val record = store.get(key) ?: return
        if (record.isTerminal()) return
        if (!expire && record.state == SHOWN && notifications.activeNotifications.none { it.tag == key }) {
            finish(record, DISMISSED)
            return
        }
        if (expire || record.remaining(System.currentTimeMillis()) == 0L) {
            // An early/spurious expiry must not remove a still valid reminder.
            if (record.remaining(System.currentTimeMillis()) == 0L) finish(record, EXPIRED) else arm(record, true)
        } else if (record.delivery <= System.currentTimeMillis()) {
            display(record)
        } else arm(record)
        context.eventsDB.getEventOrTaskWithId(record.eventId)?.let { schedule(it, false) }
    }

    private fun occurrence(record: ReminderRecord): Event? {
        val event = context.eventsDB.getEventOrTaskWithId(record.eventId) ?: return null
        if (revision(event) != record.revision) return null
        var found: Event? = null
        context.eventsHelper.getEventsSync(record.occurrence, record.occurrence + 1, event.id!!, false) { events ->
            found = events.firstOrNull { it.startTS == record.occurrence && !it.isTaskCompleted() }
        }
        return found
    }

    private fun display(record: ReminderRecord, silent: Boolean = false) {
        val now = System.currentTimeMillis()
        if (record.remaining(now) == 0L) { finish(record, EXPIRED); return }
        val event = occurrence(record) ?: run { finish(record, CANCELLED); return }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            // Permission refusal consumes this delivery; it must not surprise the user later.
            finish(record, DISMISSED); return
        }
        if (context.postReminderNotification(event, record, silent)) {
            store.save(record.copy(state = SHOWN))
            arm(record, true)
        } else finish(record, DISMISSED)
    }

    /** Reboot restores only durable live records; normal launches also reconcile auto-cancelled posts. */
    @Synchronized fun recover(afterReboot: Boolean) {
        val preferences = context.getSharedPreferences("reminder_boot", Context.MODE_PRIVATE)
        val boot = android.provider.Settings.Global.getInt(context.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
        val rebooted = afterReboot || preferences.getInt("boot", -2) != boot
        val now = System.currentTimeMillis()
        store.active().forEach { record ->
            when {
                record.remaining(now) == 0L -> finish(record, EXPIRED)
                occurrence(record) == null -> finish(record, CANCELLED)
                record.state == SHOWN && rebooted -> display(record, true)
                record.state == SHOWN && notifications.activeNotifications.none { it.tag == record.key } -> finish(record, DISMISSED)
                record.state == SHOWN -> display(record, true)
                else -> arm(record)
            }
        }
        context.eventsDB.getAllEvents().forEach { schedule(it) }
        preferences.edit().putInt("boot", boot).commit()
    }

    @Synchronized fun dismiss(key: String) {
        store.get(key)?.takeUnless { it.isTerminal() }?.let { finish(it, DISMISSED) }
    }

    @Synchronized fun dismissEvent(id: Long) {
        store.forEvent(id).filter { it.state == SHOWN }.forEach { finish(it, DISMISSED) }
    }

    @Synchronized fun cancelEvent(id: Long) {
        store.forEvent(id).filter { !it.isTerminal() }.forEach { finish(it, CANCELLED) }
    }

    @Synchronized fun snooze(key: String, delayMs: Long): Boolean {
        val record = store.get(key) ?: return false
        val snoozed = record.snooze(System.currentTimeMillis(), delayMs) ?: return false
        store.save(snoozed) // Persist before hiding, so a crash cannot restore the unsnoozed post.
        notifications.cancel(key, NOTIFICATION_ID)
        arm(snoozed)
        arm(snoozed, true)
        return true
    }

    private fun finish(record: ReminderRecord, state: String) {
        store.save(record.copy(state = state))
        notifications.cancel(record.key, NOTIFICATION_ID) // Also stops Android-owned looping sound/vibration.
        context.getAlarmManager().cancel(pending(record.key, ACTION_DELIVER))
        context.getAlarmManager().cancel(pending(record.key, ACTION_EXPIRE))
    }

    private fun arm(record: ReminderRecord, expiry: Boolean = false) {
        val time = if (expiry) record.expires else record.delivery
        context.setExactAlarm(time, pending(record.key, if (expiry) ACTION_EXPIRE else ACTION_DELIVER))
    }

    fun pending(key: String, action: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        intent(key, action), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun intent(key: String, action: String) = Intent(context, NotificationReceiver::class.java)
        .setAction(action).setData(identity(context, key, action)).putExtra(EXTRA_KEY, key)

    companion object {
        const val EXTRA_KEY = "reminder_identity"
        const val ACTION_DELIVER = "io.reminder.calendar.DELIVER"
        const val ACTION_EXPIRE = "io.reminder.calendar.EXPIRE"
        const val ACTION_DISMISS = "io.reminder.calendar.DISMISS"
        const val ACTION_SNOOZE = "io.reminder.calendar.SNOOZE"
        const val NOTIFICATION_ID = 1
        @Volatile private var instance: ReminderEngine? = null
        fun get(context: Context): ReminderEngine = instance ?: synchronized(this) {
            instance ?: ReminderEngine(context.applicationContext).also { instance = it }
        }
        fun identity(context: Context, key: String, action: String): Uri = Uri.Builder()
            .scheme("reminder").authority(context.packageName).appendPath(action).appendPath(key).build()

        // Do not include transient display flags or global duration: existing deadlines are snapshots.
        fun revision(e: Event): String {
            val text = listOf(e.startTS, e.endTS, e.repeatInterval, e.repeatRule, e.repeatLimit,
                e.reminder1Minutes, e.reminder2Minutes, e.reminder3Minutes,
                e.reminder1Type, e.reminder2Type, e.reminder3Type, e.notificationVisibilityMs,
                e.timeZone, e.flags and (FLAG_ALL_DAY or FLAG_TASK_COMPLETED)).joinToString("|")
            return MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
