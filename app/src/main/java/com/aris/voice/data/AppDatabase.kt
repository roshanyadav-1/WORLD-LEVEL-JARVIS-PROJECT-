package com.aris.voice.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Type converters for AppDatabase to handle complex types in future implementations.
 */
class AppConverters {
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return value?.joinToString(separator = ",") ?: ""
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        return value?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    }
}

/**
 * Room database for storing memories.
 * Production ready with logging, lifecycle callbacks, and robust migrations.
 */
@Database(entities = [Memory::class], version = 3, exportSchema = false)
@TypeConverters(AppConverters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun memoryDao(): MemoryDao
    
    companion object {
        private const val TAG = "AppDatabase"
        private const val DATABASE_NAME = "aris_memory_database"

        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_2_3)
                .fallbackToDestructiveMigration() // This will recreate the database if schema changes
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        Log.i(TAG, "Database created for the first time")
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        Log.d(TAG, "Database opened successfully")
                    }
                    
                    override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                        super.onDestructiveMigration(db)
                        Log.w(TAG, "Destructive migration occurred! All data wiped.")
                    }
                })
                .build()
                
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE memories ADD COLUMN memoryType TEXT NOT NULL DEFAULT 'GENERAL'")
                    db.execSQL("ALTER TABLE memories ADD COLUMN importanceScore INTEGER NOT NULL DEFAULT 50")
                    db.execSQL("ALTER TABLE memories ADD COLUMN accessCount INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE memories ADD COLUMN lastAccessedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE memories ADD COLUMN expiresAt INTEGER DEFAULT NULL")
                    db.execSQL("ALTER TABLE memories ADD COLUMN source TEXT NOT NULL DEFAULT 'conversation'")
                    
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_memoryType ON memories (memoryType)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_importanceScore ON memories (importanceScore)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_memories_expiresAt ON memories (expiresAt)")
                    Log.i(TAG, "Migration 2 to 3 completed successfully.")
                } catch (e: Exception) {
                    Log.e(TAG, "Migration 2 to 3 failed: ${e.message}", e)
                    // We let it throw so Room handles it (either crashes or uses destructive fallback)
                    throw e 
                }
            }
        }
    }
} 