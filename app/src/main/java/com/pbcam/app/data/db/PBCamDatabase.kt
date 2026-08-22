package com.pbcam.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [RecordingSession::class],
    version = 8, // Bumped to 8 to add progress columns for permanence
    exportSchema = false
)
@TypeConverters(com.pbcam.app.data.db.TypeConverters::class)
abstract class PBCamDatabase : RoomDatabase() {
    abstract fun recordingSessionDao(): RecordingSessionDao

    companion object {
        @Volatile
        private var instance: PBCamDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recording_sessions_startTime ON recording_sessions(startTime)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_recording_sessions_status ON recording_sessions(status)")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE recording_sessions ADD COLUMN progressValue REAL NOT NULL DEFAULT 0.0")
                database.execSQL("ALTER TABLE recording_sessions ADD COLUMN progressMessage TEXT")
            }
        }

        fun getInstance(context: Context): PBCamDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PBCamDatabase::class.java,
                    "pbcam.db"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
            }
        }
    }
}
