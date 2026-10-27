package io.reminder.calendar.reminders

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.Cursor

/** Separate durable ledger: never stored in preferences or rebuilt from visible notifications. */
class ReminderStore(context: Context) : SQLiteOpenHelper(context, "reminders.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE reminders (
            identity TEXT NOT NULL PRIMARY KEY, event_id INTEGER NOT NULL, occurrence INTEGER NOT NULL,
            slot INTEGER NOT NULL, planned INTEGER NOT NULL, expires INTEGER NOT NULL,
            revision TEXT NOT NULL, delivery INTEGER NOT NULL, state TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX reminder_event ON reminders(event_id)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun all(): List<ReminderRecord> = query("SELECT * FROM reminders")
    fun active(): List<ReminderRecord> = query("SELECT * FROM reminders WHERE state IN ('scheduled','shown','snoozed')")
    fun forEvent(id: Long): List<ReminderRecord> = query("SELECT * FROM reminders WHERE event_id = ?", arrayOf(id.toString()))

    private fun query(sql: String, args: Array<String>? = null): List<ReminderRecord> = readableDatabase.rawQuery(sql, args).use { c ->
        buildList {
            while (c.moveToNext()) add(read(c))
        }
    }

    private fun read(c: Cursor) = ReminderRecord(c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3),
        c.getLong(4), c.getLong(5), c.getString(6), c.getLong(7), c.getString(8))

    fun get(key: String): ReminderRecord? = readableDatabase.rawQuery("SELECT * FROM reminders WHERE identity = ?", arrayOf(key))
        .use { if (it.moveToFirst()) read(it) else null }

    fun insert(record: ReminderRecord) {
        writableDatabase.insertWithOnConflict("reminders", null, values(record), SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun save(record: ReminderRecord) {
        writableDatabase.update("reminders", values(record), "identity = ?", arrayOf(record.key))
    }

    private fun values(r: ReminderRecord) = ContentValues().apply {
        put("identity", r.key); put("event_id", r.eventId); put("occurrence", r.occurrence)
        put("slot", r.slot); put("planned", r.planned); put("expires", r.expires)
        put("revision", r.revision); put("delivery", r.delivery); put("state", r.state)
    }
}
