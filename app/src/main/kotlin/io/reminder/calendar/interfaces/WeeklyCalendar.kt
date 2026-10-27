package io.reminder.calendar.interfaces

import io.reminder.calendar.models.Event

interface WeeklyCalendar {
    fun updateWeeklyCalendar(events: ArrayList<Event>)
}
