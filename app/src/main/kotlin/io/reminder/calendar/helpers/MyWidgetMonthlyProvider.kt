package io.reminder.calendar.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import io.reminder.calendar.R
import io.reminder.calendar.activities.SplashActivity
import io.reminder.calendar.extensions.*
import io.reminder.calendar.interfaces.MonthlyCalendar
import io.reminder.calendar.models.DayMonthly
import io.reminder.calendar.models.Event
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.MEDIUM_ALPHA
import org.joda.time.DateTime
import org.joda.time.DateTimeConstants

class MyWidgetMonthlyProvider : AppWidgetProvider() {
    private val PREV = "prev"
    private val NEXT = "next"
    private val GO_TO_TODAY = "go_to_today"
    private val NEW_EVENT = "new_event"

    companion object {
        private var targetDate = DateTime.now().withDayOfMonth(1)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        performUpdate(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        performUpdate(context)
    }

    private fun performUpdate(context: Context) {
        MonthlyCalendarImpl(monthlyCalendar, context).getMonth(targetDate)
    }

    private fun getComponentName(context: Context) = ComponentName(context, MyWidgetMonthlyProvider::class.java)

    private fun setupIntent(context: Context, views: RemoteViews, action: String, id: Int) {
        Intent(context, MyWidgetMonthlyProvider::class.java).apply {
            this.action = action
            val pendingIntent = PendingIntent.getBroadcast(context, 0, this, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(id, pendingIntent)
        }
    }

    private fun setupAppOpenIntent(context: Context, views: RemoteViews, id: Int, dayCode: String) {
        (context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)).apply {
            putExtra(DAY_CODE, dayCode)
            putExtra(VIEW_TO_OPEN, MONTHLY_VIEW)
            val pendingIntent = PendingIntent.getActivity(context, Integer.parseInt(dayCode.substring(0, 6)), this, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(id, pendingIntent)
        }
    }

    private fun setupDayOpenIntent(context: Context, views: RemoteViews, id: Int, dayCode: String) {
        (context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)).apply {
            putExtra(DAY_CODE, dayCode)
            putExtra(VIEW_TO_OPEN, DAILY_VIEW)
            val pendingIntent = PendingIntent.getActivity(context, Integer.parseInt(dayCode), this, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(id, pendingIntent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            PREV -> getPrevMonth(context)
            NEXT -> getNextMonth(context)
            GO_TO_TODAY -> goToToday(context)
            NEW_EVENT -> context.launchNewEventOrTaskActivity()
            else -> super.onReceive(context, intent)
        }
    }

    private fun getPrevMonth(context: Context) {
        targetDate = targetDate!!.minusMonths(1)
        MonthlyCalendarImpl(monthlyCalendar, context).getMonth(targetDate!!)
    }

    private fun getNextMonth(context: Context) {
        targetDate = targetDate!!.plusMonths(1)
        MonthlyCalendarImpl(monthlyCalendar, context).getMonth(targetDate!!)
    }

    private fun goToToday(context: Context) {
        targetDate = DateTime.now().withDayOfMonth(1)
        MonthlyCalendarImpl(monthlyCalendar, context).getMonth(targetDate!!)
    }

    private fun updateDays(context: Context, views: RemoteViews, days: List<DayMonthly>, compact: Boolean) {
        val displayWeekNumbers = context.config.showWeekNumbers
        val textColor = context.config.widgetTextColor
        val dimPastEvents = context.config.dimPastEvents
        val dimCompletedTasks = context.config.dimCompletedTasks
        val smallerFontSize = if (compact) 9f else context.getWidgetFontSize() - 3f
        val res = context.resources
        val len = days.size
        val packageName = context.packageName
        views.apply {
            setTextColor(R.id.week_num, textColor)
            setTextSize(R.id.week_num, smallerFontSize)
            setViewVisibility(R.id.week_num, if (displayWeekNumbers) View.VISIBLE else View.GONE)
        }

        for (i in 0..5) {
            val id = res.getIdentifier("week_num_$i", "id", packageName)
            views.apply {
                setText(id, "${days[i * 7 + 3].weekOfYear}:")    // fourth day of the week matters at determining week of the year
                setTextColor(id, textColor)
                setTextSize(id, smallerFontSize)
                setViewVisibility(id, if (displayWeekNumbers) View.VISIBLE else View.GONE)
            }
        }

        for (i in 0 until len) {
            val day = days[i]

            val dayTextColor = if (context.config.highlightWeekends && day.isWeekend) {
                context.config.highlightWeekendsColor
            } else {
                textColor
            }

            val weakTextColor = dayTextColor.adjustAlpha(MEDIUM_ALPHA)
            val currTextColor = if (day.isThisMonth) dayTextColor else weakTextColor
            val id = res.getIdentifier("day_$i", "id", packageName)
            views.removeAllViews(id)
            addDayNumber(context, views, day, currTextColor, id, compact)
            setupDayOpenIntent(context, views, id, day.code)

            views.setContentDescription(id, day.code + ": " + day.dayEvents.joinToString { it.title })
            if (compact) continue

            day.dayEvents = day.dayEvents.asSequence().sortedWith(compareBy({ it.flags and FLAG_ALL_DAY == 0 }, { it.startTS }, { it.title }))
                .toMutableList() as ArrayList<Event>

            day.dayEvents.forEach {
                val backgroundColor = it.color
                var eventTextColor = backgroundColor.getContrastColor()

                if (it.isTask() && it.isTaskCompleted() && dimCompletedTasks || !day.isThisMonth || (dimPastEvents && it.isPastEvent)) {
                    eventTextColor = eventTextColor.adjustAlpha(MEDIUM_ALPHA)
                }

                val newRemoteView = RemoteViews(packageName, R.layout.day_monthly_event_view_widget).apply {
                    setText(R.id.day_monthly_event_id, it.title.replace(" ", "\u00A0"))
                    setTextColor(R.id.day_monthly_event_id, eventTextColor)
                    setTextSize(R.id.day_monthly_event_id, smallerFontSize - 3f)
                    setVisibleIf(R.id.day_monthly_task_image, it.isTask())
                    applyColorFilter(R.id.day_monthly_task_image, eventTextColor)
                    setInt(R.id.day_monthly_event_background, "setColorFilter", it.color)

                    if (it.shouldStrikeThrough()) {
                        setInt(R.id.day_monthly_event_id, "setPaintFlags", Paint.ANTI_ALIAS_FLAG or Paint.STRIKE_THRU_TEXT_FLAG)
                    } else {
                        setInt(R.id.day_monthly_event_id, "setPaintFlags", Paint.ANTI_ALIAS_FLAG)
                    }
                }
                views.addView(id, newRemoteView)
            }
        }
    }

    private fun addDayNumber(context: Context, views: RemoteViews, day: DayMonthly, textColor: Int, id: Int, compact: Boolean) {
        val newRemoteView = RemoteViews(context.packageName, if (compact) R.layout.day_monthly_number_view_compact else R.layout.day_monthly_number_view).apply {
            setText(R.id.day_monthly_number_id, day.value.toString() + if (compact && day.dayEvents.isNotEmpty()) " ·" else "")
            setTextSize(R.id.day_monthly_number_id, if (compact) 10f else context.getWidgetFontSize() - 3f)

            if (day.isToday) {
                setTextColor(R.id.day_monthly_number_id, textColor.getContrastColor())
                setViewVisibility(R.id.day_monthly_number_background, View.VISIBLE)
                setInt(R.id.day_monthly_number_background, "setColorFilter", textColor)
            } else {
                setTextColor(R.id.day_monthly_number_id, textColor)
                setViewVisibility(R.id.day_monthly_number_background, View.GONE)
            }
        }
        views.addView(id, newRemoteView)
    }

    internal fun createMonthViews(context: Context, month: String, days: List<DayMonthly>, currTargetDate: DateTime, compact: Boolean): RemoteViews {
        val textColor = context.config.widgetTextColor
        val resources = context.resources
                val views = RemoteViews(context.packageName, R.layout.fragment_month_widget)
                views.setText(R.id.top_value, month)

                views.applyColorFilter(R.id.widget_month_background, context.config.widgetBgColor)

                views.setTextColor(R.id.top_value, textColor)
                views.setTextSize(R.id.top_value, if (compact) 12f else context.getWidgetFontSize() + 3f)

                var bmp = resources.getColoredBitmap(com.simplemobiletools.commons.R.drawable.ic_chevron_left_vector, textColor)
                views.setImageViewBitmap(R.id.top_left_arrow, bmp)

                bmp = resources.getColoredBitmap(com.simplemobiletools.commons.R.drawable.ic_chevron_right_vector, textColor)
                views.setImageViewBitmap(R.id.top_right_arrow, bmp)

                bmp = resources.getColoredBitmap(R.drawable.ic_today_vector, textColor)
                views.setImageViewBitmap(R.id.top_go_to_today, bmp)

                bmp = resources.getColoredBitmap(com.simplemobiletools.commons.R.drawable.ic_plus_vector, textColor)
                views.setImageViewBitmap(R.id.top_new_event, bmp)

                val shouldGoToTodayBeVisible = currTargetDate.withTime(0, 0, 0, 0) != DateTime.now().withDayOfMonth(1).withTime(0, 0, 0, 0)
                views.setVisibleIf(R.id.top_go_to_today, shouldGoToTodayBeVisible)

                updateDayLabels(context, views, resources, textColor, compact)
                updateDays(context, views, days, compact)

                setupIntent(context, views, PREV, R.id.top_left_arrow)
                setupIntent(context, views, NEXT, R.id.top_right_arrow)
                setupIntent(context, views, GO_TO_TODAY, R.id.top_go_to_today)
                setupIntent(context, views, NEW_EVENT, R.id.top_new_event)

                val monthCode = days.firstOrNull { it.code.substring(6) == "01" }?.code ?: Formatter.getTodayCode()
                setupAppOpenIntent(context, views, R.id.top_value, monthCode)

                if (compact) {
                    val padding = (3 * resources.displayMetrics.density).toInt()
                    views.setViewPadding(R.id.calendar_wrapper, padding, padding, padding, padding)
                    views.setViewPadding(R.id.top_value, 0, 0, 0, 0)
                    views.setViewPadding(R.id.first_row_widget, 0, 0, 0, 0)
                }
                return views
    }

    private val monthlyCalendar = object : MonthlyCalendar {
        override fun updateMonthlyCalendar(context: Context, month: String, days: ArrayList<DayMonthly>, checkedEvents: Boolean, currTargetDate: DateTime) {
            val largerFontSize = context.getWidgetFontSize() + 3f
            val textColor = context.config.widgetTextColor
            val resources = context.resources

            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach {
                val options = appWidgetManager.getAppWidgetOptions(it)
                val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 250)
                val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, minHeight)
                val portrait = createMonthViews(context, month, days, currTargetDate, maxHeight < 280)
                val views = if ((minHeight < 280) != (maxHeight < 280)) {
                    RemoteViews(createMonthViews(context, month, days, currTargetDate, minHeight < 280), portrait)
                } else portrait

                try {
                    appWidgetManager.updateAppWidget(it, views)
                } catch (ignored: RuntimeException) {
                }
            }
        }
    }

    private fun updateDayLabels(context: Context, views: RemoteViews, resources: Resources, textColor: Int, compact: Boolean) {
        val config = context.config
        val firstDayOfWeek = config.firstDayOfWeek
        val smallerFontSize = if (compact) 8f else context.getWidgetFontSize()
        val packageName = context.packageName
        val letters = context.resources.getStringArray(com.simplemobiletools.commons.R.array.week_day_letters)

        for (i in 0..6) {
            val id = resources.getIdentifier("label_$i", "id", packageName)
            val dayTextColor = if (context.config.highlightWeekends && context.isWeekendIndex(i)) {
                context.config.highlightWeekendsColor
            } else {
                textColor
            }

            views.setTextColor(id, dayTextColor)
            views.setTextSize(id, smallerFontSize)

            var index = i
            if (firstDayOfWeek != DateTimeConstants.MONDAY) {
                index = (index + firstDayOfWeek - 1) % 7
            }

            views.setText(id, letters[index])
        }
    }
}
