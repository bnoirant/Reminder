package io.reminder.calendar.extensions

import io.reminder.calendar.helpers.MONTH
import io.reminder.calendar.helpers.WEEK
import io.reminder.calendar.helpers.YEAR

fun Int.isXWeeklyRepetition() = this != 0 && this % WEEK == 0

fun Int.isXMonthlyRepetition() = this != 0 && this % MONTH == 0

fun Int.isXYearlyRepetition() = this != 0 && this % YEAR == 0
