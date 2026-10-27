package io.reminder.calendar.reminders

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import io.reminder.calendar.databases.EventsDatabase
import io.reminder.calendar.extensions.config
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class VisibilityMigrationTest {
    @Test fun fullRoomEightToNineMigrationPreservesExistingEvents() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("migration-test.db")
        val json = javaClass.classLoader!!.getResourceAsStream("io.reminder.calendar.databases.EventsDatabase/8.json")!!
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val entities = json.getJSONArray("entities")
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-test.db").callback(object : SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    for (i in 0 until entities.length()) {
                        val entity = entities.getJSONObject(i)
                        val name = entity.getString("tableName")
                        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", name))
                        val indices = entity.getJSONArray("indices")
                        for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", name))
                    }
                    val setup = json.getJSONArray("setupQueries")
                    for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                    val fields = entities.getJSONObject(0).getJSONArray("fields")
                    val row = ContentValues()
                    for (i in 0 until fields.length()) {
                        val f = fields.getJSONObject(i)
                        val name = f.getString("columnName")
                        if (f.getString("affinity") == "INTEGER") row.put(name, if (name == "id") 7L else 0L)
                        else row.put(name, if (name in listOf("attendees", "repetition_exceptions")) "[]" else "")
                    }
                    row.put("title", "Existing event")
                    db.insert("events", SQLiteDatabase.CONFLICT_REPLACE, row)
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        val migrated = Room.databaseBuilder(context, EventsDatabase::class.java, "migration-test.db")
            .addMigrations(EventsDatabase.MIGRATION_8_9).allowMainThreadQueries().build()
        val event = migrated.EventsDao().getEventOrTaskWithId(7)!! // Opening validates the entire Room 9 schema.
        assertEquals("Existing event", event.title)
        assertNull(event.notificationVisibilityMs)
        assertEquals(4 * 3_600_000L, context.config.notificationVisibilityMs)
        migrated.close()
    }
}
