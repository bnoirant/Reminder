package io.reminder.calendar.extensions

import io.reminder.calendar.models.ListEvent

fun ListEvent.shouldStrikeThrough() = isTaskCompleted || isAttendeeInviteDeclined
