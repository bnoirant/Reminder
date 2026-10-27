package io.reminder.calendar.services

import android.content.Intent
import android.widget.RemoteViewsService
import io.reminder.calendar.adapters.EventListWidgetAdapter

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent) = EventListWidgetAdapter(applicationContext, intent)
}
