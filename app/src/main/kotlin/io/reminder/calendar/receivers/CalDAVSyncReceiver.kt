package io.reminder.calendar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.reminder.calendar.extensions.config
import io.reminder.calendar.extensions.recheckCalDAVCalendars
import io.reminder.calendar.extensions.refreshCalDAVCalendars
import io.reminder.calendar.extensions.updateWidgets

class CalDAVSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (context.config.caldavSync) {
            context.refreshCalDAVCalendars(context.config.caldavSyncedCalendarIds, false)
        }

        context.recheckCalDAVCalendars(true) {
            context.updateWidgets()
        }
    }
}
