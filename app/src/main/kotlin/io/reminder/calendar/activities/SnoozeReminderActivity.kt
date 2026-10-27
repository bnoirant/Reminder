package io.reminder.calendar.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.reminder.calendar.extensions.config
import io.reminder.calendar.reminders.ReminderEngine
import io.reminder.calendar.R
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.showPickSecondsDialogHelper
import com.simplemobiletools.commons.helpers.ensureBackgroundThread

class SnoozeReminderActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        showPickSecondsDialogHelper(config.snoozeTime, true, cancelCallback = { dialogCancelled() }) {
            ensureBackgroundThread {
                val key = intent.getStringExtra(ReminderEngine.EXTRA_KEY)
                if (key != null && ReminderEngine.get(this).snooze(key, it * 1000L)) {
                    config.snoozeTime = it / 60
                } else toast(R.string.snooze_exceeds_expiry)
                runOnUiThread {
                    finishActivity()
                }
            }
        }
    }

    private fun dialogCancelled() {
        finishActivity()
    }

    private fun finishActivity() {
        finish()
        overridePendingTransition(0, 0)
    }
}
