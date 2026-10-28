package io.reminder.calendar.activities

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.simplemobiletools.commons.extensions.baseConfig
import com.simplemobiletools.commons.extensions.isAProApp
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ReminderIdentityTest {
    @Test fun launchDoesNotWarnThatReminderIsFake() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(0, 100).forEach { count ->
            context.baseConfig.appRunCount = count
            val controller = Robolectric.buildActivity(SimpleActivity::class.java).create()
            assertNull(ShadowAlertDialog.getLatestAlertDialog())
            assertFalse(controller.get().isFinishing)
            controller.destroy()
        }
    }
    @Test fun customizationStillOpensAfterOneHundredLaunches() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.baseConfig.appRunCount = 101
        val controller = Robolectric.buildActivity(SimpleActivity::class.java).create()
        controller.get().startCustomizationActivity()
        assertEquals("com.simplemobiletools.commons.activities.CustomizationActivity", shadowOf(controller.get()).nextStartedActivity.component!!.className)
        assertNull(ShadowAlertDialog.getLatestAlertDialog())
        controller.destroy()
    }
    @Test fun reminderIsRecognizedAsFullApp() {
        assertTrue(ApplicationProvider.getApplicationContext<Context>().isAProApp())
    }
}
