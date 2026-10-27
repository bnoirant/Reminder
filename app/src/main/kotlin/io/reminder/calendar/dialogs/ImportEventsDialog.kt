package io.reminder.calendar.dialogs

import androidx.appcompat.app.AlertDialog
import io.reminder.calendar.R
import io.reminder.calendar.activities.SimpleActivity
import io.reminder.calendar.databinding.DialogImportEventsBinding
import io.reminder.calendar.extensions.config
import io.reminder.calendar.extensions.eventTypesDB
import io.reminder.calendar.extensions.eventsHelper
import io.reminder.calendar.helpers.IcsImporter
import io.reminder.calendar.helpers.IcsImporter.ImportResult.IMPORT_FAIL
import io.reminder.calendar.helpers.IcsImporter.ImportResult.IMPORT_NOTHING_NEW
import io.reminder.calendar.helpers.IcsImporter.ImportResult.IMPORT_OK
import io.reminder.calendar.helpers.IcsImporter.ImportResult.IMPORT_PARTIAL
import io.reminder.calendar.helpers.REGULAR_EVENT_TYPE_ID
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread

class ImportEventsDialog(val activity: SimpleActivity, val path: String, val callback: (refreshView: Boolean) -> Unit) {
    private var currEventTypeId = REGULAR_EVENT_TYPE_ID
    private var currEventTypeCalDAVCalendarId = 0
    private val config = activity.config
    private val binding by activity.viewBinding(DialogImportEventsBinding::inflate)

    init {
        ensureBackgroundThread {
            if (activity.eventTypesDB.getEventTypeWithId(config.lastUsedLocalEventTypeId) == null) {
                config.lastUsedLocalEventTypeId = REGULAR_EVENT_TYPE_ID
            }

            val isLastCaldavCalendarOK = config.caldavSync && config.getSyncedCalendarIdsAsList().contains(config.lastUsedCaldavCalendarId)
            currEventTypeId = if (isLastCaldavCalendarOK) {
                val lastUsedCalDAVCalendar = activity.eventsHelper.getEventTypeWithCalDAVCalendarId(config.lastUsedCaldavCalendarId)
                if (lastUsedCalDAVCalendar != null) {
                    currEventTypeCalDAVCalendarId = config.lastUsedCaldavCalendarId
                    lastUsedCalDAVCalendar.id!!
                } else {
                    REGULAR_EVENT_TYPE_ID
                }
            } else {
                config.lastUsedLocalEventTypeId
            }
            binding.importEventsCheckbox.isChecked = config.lastUsedIgnoreEventTypesState

            activity.runOnUiThread {
                initDialog()
            }
        }
    }

    private fun initDialog() {
        binding.apply {
            updateEventType(this)
            importEventTypeTitle.setOnClickListener {
                SelectEventTypeDialog(
                    activity = activity,
                    currEventType = currEventTypeId,
                    showCalDAVCalendars = true,
                    showNewEventTypeOption = true,
                    addLastUsedOneAsFirstOption = false,
                    showOnlyWritable = true,
                    showManageEventTypes = false
                ) {
                    currEventTypeId = it.id!!
                    currEventTypeCalDAVCalendarId = it.caldavCalendarId

                    config.lastUsedLocalEventTypeId = it.id!!
                    config.lastUsedCaldavCalendarId = it.caldavCalendarId

                    updateEventType(this)
                }
            }

            importEventsCheckboxHolder.setOnClickListener {
                importEventsCheckbox.toggle()
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok, null)
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.import_events) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(null)
                        activity.toast(com.simplemobiletools.commons.R.string.importing)
                        ensureBackgroundThread {
                            val overrideFileEventTypes = binding.importEventsCheckbox.isChecked
                            config.lastUsedIgnoreEventTypesState = overrideFileEventTypes
                            val result = IcsImporter(activity).importEvents(path, currEventTypeId, currEventTypeCalDAVCalendarId, overrideFileEventTypes)
                            handleParseResult(result)
                            alertDialog.dismiss()
                        }
                    }
                }
            }
    }

    private fun updateEventType(binding: DialogImportEventsBinding) {
        ensureBackgroundThread {
            val eventType = activity.eventTypesDB.getEventTypeWithId(currEventTypeId)
            activity.runOnUiThread {
                binding.importEventTypeTitle.setText(eventType!!.getDisplayTitle())
                binding.importEventTypeColor.setFillWithStroke(eventType.color, activity.getProperBackgroundColor())
            }
        }
    }

    private fun handleParseResult(result: IcsImporter.ImportResult) {
        activity.toast(
            when (result) {
                IMPORT_NOTHING_NEW -> com.simplemobiletools.commons.R.string.no_new_items
                IMPORT_OK -> com.simplemobiletools.commons.R.string.importing_successful
                IMPORT_PARTIAL -> com.simplemobiletools.commons.R.string.importing_some_entries_failed
                else -> com.simplemobiletools.commons.R.string.no_items_found
            }
        )
        callback(result != IMPORT_FAIL)
    }
}
