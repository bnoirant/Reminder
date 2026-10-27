package io.reminder.calendar.extensions

import io.reminder.calendar.models.MonthViewEvent

fun MonthViewEvent.shouldStrikeThrough() = isTaskCompleted || isAttendeeInviteDeclined
