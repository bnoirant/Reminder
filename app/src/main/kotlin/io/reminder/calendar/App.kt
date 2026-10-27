package io.reminder.calendar

import androidx.multidex.MultiDexApplication
import com.simplemobiletools.commons.extensions.checkUseEnglish

class App : MultiDexApplication() {
    override fun onCreate() {
        super.onCreate()
        checkUseEnglish()
        io.reminder.calendar.receivers.NotificationReceiver.executor.execute {
            io.reminder.calendar.reminders.ReminderEngine.get(this).recover(false)
        }
    }
}
