package io.reminder.calendar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.reminder.calendar.extensions.*
import io.reminder.calendar.reminders.ReminderEngine
import org.joda.time.DateTimeZone
import java.util.TimeZone

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        NotificationReceiver.executor.execute {
            try {
                context.apply {
                    TimeZone.setDefault(null)
                    DateTimeZone.setDefault(DateTimeZone.forTimeZone(TimeZone.getDefault()))
                    ReminderEngine.get(this).recover(intent.action == Intent.ACTION_BOOT_COMPLETED ||
                        intent.action?.contains("QUICKBOOT") == true)
                    recheckCalDAVCalendars(true) {}
                    scheduleNextAutomaticBackup()
                    checkAndBackupEventsOnBoot()
                }
            } finally { result.finish() }
        }
    }
}
