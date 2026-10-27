package io.reminder.calendar.services

import android.content.Intent
import android.widget.RemoteViewsService
import io.reminder.calendar.adapters.EventListWidgetAdapterEmpty

class WidgetServiceEmpty : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = EventListWidgetAdapterEmpty(applicationContext)
}
