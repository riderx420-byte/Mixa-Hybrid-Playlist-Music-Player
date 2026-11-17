package com.example.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CachedTrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        SearchHistoryEntity::class,
        ExtensionRegistryEntity::class,
        InstalledExtensionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SonoraDatabase : RoomDatabase() {
    abstract fun sonoraDao(): SonoraDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN sampleRateHz INTEGER")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN bitDepth INTEGER")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN isLiveStream INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN variantsJson TEXT")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN activeVariantId TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN extensionId TEXT")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN providerTrackId TEXT")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN isrc TEXT")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN isMetadataOnly INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN resolvedByExtensionId TEXT")
                db.execSQL("ALTER TABLE cached_tracks ADD COLUMN sourceLabel TEXT")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS extension_registries (
                        url TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        entriesJson TEXT NOT NULL,
                        addedAt INTEGER NOT NULL,
                        lastSyncedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS installed_extensions (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        version TEXT NOT NULL,
                        registryUrl TEXT,
                        manifestJson TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        installedAt INTEGER NOT NULL,
                        sha256 TEXT,
                        localDirPath TEXT NOT NULL,
                        lastError TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: SonoraDatabase? = null

        fun getInstance(context: Context): SonoraDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SonoraDatabase::class.java,
                    "sonora_unified.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
