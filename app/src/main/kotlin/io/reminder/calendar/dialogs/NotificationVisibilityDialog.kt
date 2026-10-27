package io.reminder.calendar.dialogs

import android.app.Activity
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import io.reminder.calendar.R
import java.math.BigDecimal
import java.math.RoundingMode

object NotificationVisibilityDialog {
    fun summary(activity: Activity, duration: Long?): String = if (duration == null)
        activity.getString(R.string.visibility_global) else activity.getString(R.string.visibility_hours,
            BigDecimal(duration).divide(BigDecimal(3_600_000), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())

    fun show(activity: Activity, current: Long?, allowGlobal: Boolean, callback: (Long?) -> Unit) {
        val choices = listOf(1L, 2L, 4L, 8L).map { it * 3_600_000 }
        val labels = choices.map { summary(activity, it) }.toMutableList()
        if (allowGlobal) labels.add(0, activity.getString(R.string.visibility_global))
        labels.add(activity.getString(R.string.visibility_custom))
        AlertDialog.Builder(activity).setTitle(R.string.notification_visibility)
            .setItems(labels.toTypedArray()) { _, position ->
                when {
                    position == labels.lastIndex -> custom(activity, current, callback)
                    allowGlobal && position == 0 -> callback(null)
                    else -> callback(choices[position - if (allowGlobal) 1 else 0])
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun custom(activity: Activity, current: Long?, callback: (Long?) -> Unit) {
        val input = EditText(activity).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = activity.getString(R.string.visibility_minutes)
            current?.let { setText(BigDecimal(it).divide(BigDecimal(60_000), 6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()) }
        }
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.visibility_custom)
            .setMessage(R.string.visibility_minutes).setView(input)
            .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val duration = runCatching { input.text.toString().replace(',', '.').toBigDecimal()
                    .multiply(BigDecimal(60_000)).setScale(0, RoundingMode.DOWN).longValueExact() }.getOrNull()
                if (duration == null || duration <= 0 || duration > Long.MAX_VALUE / 2) {
                    input.error = activity.getString(R.string.visibility_positive)
                } else { callback(duration); dialog.dismiss() }
            }
        }
        dialog.show()
    }
}
