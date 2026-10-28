package io.reminder.calendar.widgets

import android.app.Application
import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.reminder.calendar.R
import io.reminder.calendar.helpers.MyWidgetMonthlyProvider
import io.reminder.calendar.models.DayMonthly
import io.reminder.calendar.models.Event
import org.joda.time.DateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CompactWidgetTest {
    private fun render(compact: Boolean): View {
        val context = ContextThemeWrapper(ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme)
        val first = DateTime(2026, 10, 1, 0, 0)
        val days = (0 until 42).map { index ->
            val date = first.plusDays(index)
            DayMonthly(date.dayOfMonth, date.monthOfYear == 10, index == 0, date.toString("yyyyMMdd"), date.weekOfWeekyear,
                if (index == 0) arrayListOf(Event(1, title = "Test event")) else arrayListOf(), index, false)
        }
        val views = MyWidgetMonthlyProvider().createMonthViews(context, "October", days, first, compact)
        val root = views.apply(context, FrameLayout(context))
        val density = context.resources.displayMetrics.density
        root.measure(View.MeasureSpec.makeMeasureSpec((250 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(((if (compact) 110 else 250) * density).toInt(), View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    @Test fun compactMonthFitsAllSixWeeksAndKeepsClicksAndEventIndicator() {
        val root = render(true)
        for (index in 0 until 42) {
            val cell = root.findViewById<LinearLayout>(root.resources.getIdentifier("day_$index", "id", "io.reminder.calendar"))
            assertEquals(1, cell.childCount)
            assertTrue("day $index has height", cell.height > 0)
            val number = cell.findViewById<TextView>(R.id.day_monthly_number_id)
            assertTrue("day $index fits", number.lineHeight <= cell.height)
            assertTrue(cell.hasOnClickListeners())
        }
        val first = root.findViewById<LinearLayout>(R.id.day_0)
        assertTrue(first.findViewById<TextView>(R.id.day_monthly_number_id).text.contains("·"))
        assertTrue(first.contentDescription.contains("Test event"))
    }

    @Test fun expandedMonthRestoresEventTitles() {
        assertEquals(2, render(false).findViewById<LinearLayout>(R.id.day_0).childCount)
    }

    @Test fun bothProvidersAdvertiseFourByTwoAndCanResize() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        listOf(R.xml.widget_monthly_info, R.xml.widget_list_info).forEach { id ->
            context.resources.getXml(id).use { xml ->
                while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
                val ns = "http://schemas.android.com/apk/res/android"
                assertEquals(4, xml.getAttributeIntValue(ns, "targetCellWidth", -1))
                assertEquals(2, xml.getAttributeIntValue(ns, "targetCellHeight", -1))
                assertEquals(3, xml.getAttributeIntValue(ns, "resizeMode", -1))
            }
        }
    }
}
