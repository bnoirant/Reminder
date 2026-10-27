package io.reminder.calendar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.reminder.calendar.R
import io.reminder.calendar.extensions.config
import io.reminder.calendar.reminders.ReminderEngine
import com.simplemobiletools.commons.extensions.toast
import java.util.concurrent.Executors

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(ReminderEngine.EXTRA_KEY) ?: return
        val result = goAsync()
        executor.execute {
            try {
                val engine = ReminderEngine.get(context)
                when (intent.action) {
                    ReminderEngine.ACTION_DISMISS -> engine.dismiss(key)
                    ReminderEngine.ACTION_SNOOZE -> {
                        if (!engine.snooze(key, context.config.snoozeTime * 60_000L)) context.toast(R.string.snooze_exceeds_expiry)
                    }
                    ReminderEngine.ACTION_EXPIRE -> engine.receive(key, true)
                    ReminderEngine.ACTION_DELIVER -> engine.receive(key)
                }
            } finally { result.finish() }
        }
    }
    companion object { val executor = Executors.newSingleThreadExecutor() }
}
