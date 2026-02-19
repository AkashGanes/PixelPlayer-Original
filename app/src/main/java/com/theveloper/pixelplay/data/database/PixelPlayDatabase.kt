package com.theveloper.pixelplay.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AlbumArtThemeEntity::class,
        SearchHistoryEntity::class,
        SongEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        TransitionRuleEntity::class,
        SongArtistCrossRef::class,
        TelegramSongEntity::class,
        TelegramChannelEntity::class,
        SongEngagementEntity::class,
        FavoritesEntity::class,
        LyricsEntity::class,
        NeteaseSongEntity::class,
        NeteasePlaylistEntity::class,
        GDriveSongEntity::class,
        GDriveFolderEntity::class
    ],
    version = 24, // Incremented for schema consolidation and cleanup

    exportSchema = false
)
abstract class PixelPlayDatabase : RoomDatabase() {
    abstract fun albumArtThemeDao(): AlbumArtThemeDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun musicDao(): MusicDao
    abstract fun transitionDao(): TransitionDao
    abstract fun telegramDao(): TelegramDao
    abstract fun engagementDao(): EngagementDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun neteaseDao(): NeteaseDao
    abstract fun gdriveDao(): GDriveDao

    companion object {
        // Gap-bridging no-op migrations for missing version ranges.
        // These versions predate Telegram features; affected tables have since been
        // recreated by later migrations (e.g. 15→16 drops/recreates album_art_themes).
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op gap bridge */ }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op gap bridge */ }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) { /* no-op gap bridge */ }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN parent_directory_path TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN lyrics TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN mime_type TEXT")
                db.execSQL("ALTER TABLE songs ADD COLUMN bitrate INTEGER")
                db.execSQL("ALTER TABLE songs ADD COLUMN sample_rate INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN album_artist TEXT DEFAULT NULL")
                
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS song_artist_cross_ref (
                        song_id INTEGER NOT NULL,
                        artist_id INTEGER NOT NULL,
                        is_primary INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (song_id, artist_id),
                        FOREIGN KEY (song_id) REFERENCES songs(id) ON DELETE CASCADE,
                        FOREIGN KEY (artist_id) REFERENCES artists(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_song_id ON song_artist_cross_ref(song_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_artist_id ON song_artist_cross_ref(artist_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_song_artist_cross_ref_is_primary ON song_artist_cross_ref(is_primary)")
                
                db.execSQL("""
                    INSERT OR REPLACE INTO song_artist_cross_ref (song_id, artist_id, is_primary)
                    SELECT id, artist_id, 1 FROM songs WHERE artist_id IS NOT NULL
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artists ADD COLUMN image_url TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS telegram_songs (
                        id TEXT NOT NULL PRIMARY KEY,
                        chat_id INTEGER NOT NULL,
                        message_id INTEGER NOT NULL,
                        file_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        mime_type TEXT NOT NULL,
                        date_added INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE telegram_songs ADD COLUMN album_art_uri TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS telegram_channels (
                        chat_id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        username TEXT,
                        song_count INTEGER NOT NULL DEFAULT 0,
                        last_sync_time INTEGER NOT NULL DEFAULT 0,
                        photo_path TEXT
                    )
                """.trimIndent())
            }
        }
        
        val MIGRATION_15_16 = object : Migration(15, 16) {
             override fun migrate(db: SupportSQLiteDatabase) {
                // Create song_engagements table for tracking play statistics
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS song_engagements (
                        song_id TEXT NOT NULL PRIMARY KEY,
                        play_count INTEGER NOT NULL DEFAULT 0,
                        total_play_duration_ms INTEGER NOT NULL DEFAULT 0,
                        last_played_timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Fix for album_art_themes schema mismatch (Backport upstream MIGRATION_14_15 logic)
                // Since this table is a cache and the schema is complex (100 columns), it is safer to DROP and RECREATE
                // to ensure it exactly matches AlbumArtThemeEntity and avoid validation crashes.
                db.execSQL("DROP TABLE IF EXISTS album_art_themes")

                val colorColumns = listOf(
                    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
                    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
                    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
                    "background", "onBackground", "surface", "onSurface",
                    "surfaceVariant", "onSurfaceVariant", "error", "onError",
                    "outline", "errorContainer", "onErrorContainer",
                    "inversePrimary", "inverseSurface", "inverseOnSurface",
                    "surfaceTint", "outlineVariant", "scrim",
                    "surfaceBright", "surfaceDim",
                    "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest", "surfaceContainerLow", "surfaceContainerLowest",
                    "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
                    "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
                    "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant"
                )

                val themePrefixes = listOf("light_", "dark_")
                val columnDefinitions = StringBuilder()
                
                // Add standard columns
                columnDefinitions.append("albumArtUriString TEXT NOT NULL, ")
                columnDefinitions.append("paletteStyle TEXT NOT NULL, ")

                // Add dynamic color columns
                themePrefixes.forEach { prefix ->
                    colorColumns.forEach { column ->
                        columnDefinitions.append("${prefix}${column} TEXT NOT NULL, ")
                    }
                }

                // Remove trailing comma and space
                val columnsSql = columnDefinitions.toString().trimEnd(',', ' ')

                db.execSQL("CREATE TABLE IF NOT EXISTS album_art_themes ($columnsSql, PRIMARY KEY(albumArtUriString))")
            }
        }
        
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE songs ADD COLUMN telegram_chat_id INTEGER DEFAULT NULL")
                } catch (e: Exception) {
                    // Column might already exist
                }
                try {
                    db.execSQL("ALTER TABLE songs ADD COLUMN telegram_file_id INTEGER DEFAULT NULL")
                } catch (e: Exception) {
                    // Column might already exist
                }

                // Fix for album_art_themes schema mismatch if user is coming from version 16 (where the schema might be broken)
                // We re-apply the DROP and RECREATE strategy here to ensure everyone ends up with the correct schema.
                db.execSQL("DROP TABLE IF EXISTS album_art_themes")

                val colorColumns = listOf(
                    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
                    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
                    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
                    "background", "onBackground", "surface", "onSurface",
                    "surfaceVariant", "onSurfaceVariant", "error", "onError",
                    "outline", "errorContainer", "onErrorContainer",
                    "inversePrimary", "inverseSurface", "inverseOnSurface",
                    "surfaceTint", "outlineVariant", "scrim",
                    "surfaceBright", "surfaceDim",
                    "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest", "surfaceContainerLow", "surfaceContainerLowest",
                    "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
                    "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
                    "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant"
                )

                val themePrefixes = listOf("light_", "dark_")
                val columnDefinitions = StringBuilder()
                
                // Add standard columns
                columnDefinitions.append("albumArtUriString TEXT NOT NULL, ")
                columnDefinitions.append("paletteStyle TEXT NOT NULL, ")

                // Add dynamic color columns
                themePrefixes.forEach { prefix ->
                    colorColumns.forEach { column ->
                        columnDefinitions.append("${prefix}${column} TEXT NOT NULL, ")
                    }
                }

                // Remove trailing comma and space
                val columnsSql = columnDefinitions.toString().trimEnd(',', ' ')

                db.execSQL("CREATE TABLE IF NOT EXISTS album_art_themes ($columnsSql, PRIMARY KEY(albumArtUriString))")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        songId INTEGER NOT NULL PRIMARY KEY,
                        isFavorite INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Migrate existing favorites from songs table if possible
                // Note: We need to cast is_favorite (boolean/int) to ensure compatibility
                db.execSQL("""
                    INSERT OR IGNORE INTO favorites (songId, isFavorite, timestamp)
                    SELECT id, is_favorite, ? FROM songs WHERE is_favorite = 1
                """, arrayOf(System.currentTimeMillis()))
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lyrics` (`songId` INTEGER NOT NULL, `content` TEXT NOT NULL, `isSynced` INTEGER NOT NULL DEFAULT 0, `source` TEXT, PRIMARY KEY(`songId`))"
                )
                database.execSQL(
                    // Explicitly set isSynced for compatibility with older schemas that have
                    // NOT NULL but no default value for this column.
                    "INSERT OR IGNORE INTO lyrics (songId, content, isSynced) SELECT id, lyrics, 0 FROM songs WHERE lyrics IS NOT NULL AND lyrics != ''"
                )
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE album_art_themes ADD COLUMN paletteStyle TEXT NOT NULL DEFAULT 'tonal_spot'"
                )

                val newRoleColumns = listOf(
                    "surfaceBright",
                    "surfaceDim",
                    "surfaceContainer",
                    "surfaceContainerHigh",
                    "surfaceContainerHighest",
                    "surfaceContainerLow",
                    "surfaceContainerLowest",
                    "primaryFixed",
                    "primaryFixedDim",
                    "onPrimaryFixed",
                    "onPrimaryFixedVariant",
                    "secondaryFixed",
                    "secondaryFixedDim",
                    "onSecondaryFixed",
                    "onSecondaryFixedVariant",
                    "tertiaryFixed",
                    "tertiaryFixedDim",
                    "onTertiaryFixed",
                    "onTertiaryFixedVariant"
                )

                val prefixes = listOf("light_", "dark_")
                prefixes.forEach { prefix ->
                    newRoleColumns.forEach { role ->
                        database.execSQL(
                            "ALTER TABLE album_art_themes ADD COLUMN ${prefix}${role} TEXT NOT NULL DEFAULT '#00000000'"
                        )
                    }
                }

                // The table is a cache; wipe stale rows so we always regenerate with full token data.
                database.execSQL("DELETE FROM album_art_themes")
            }
        }

        /**
         * Reconcile Telegram tables: drop and recreate to match current entity definitions.
         * Telegram data is re-syncable cache, so this is safe.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop existing Telegram tables that may have schema drift
                db.execSQL("DROP TABLE IF EXISTS telegram_songs")
                db.execSQL("DROP TABLE IF EXISTS telegram_channels")

                // Recreate telegram_songs matching TelegramSongEntity exactly
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS telegram_songs (
                        id TEXT NOT NULL PRIMARY KEY,
                        chat_id INTEGER NOT NULL,
                        message_id INTEGER NOT NULL,
                        file_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        duration INTEGER NOT NULL,
                        file_path TEXT NOT NULL,
                        mime_type TEXT NOT NULL,
                        date_added INTEGER NOT NULL,
                        album_art_uri TEXT
                    )
                """.trimIndent())

                // Recreate telegram_channels matching TelegramChannelEntity exactly
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS telegram_channels (
                        chat_id INTEGER NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        username TEXT,
                        song_count INTEGER NOT NULL DEFAULT 0,
                        last_sync_time INTEGER NOT NULL DEFAULT 0,
                        photo_path TEXT
                    )
                """.trimIndent())
            }
        }

        /**
         * Add Netease Cloud Music tables.
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS netease_songs (
                        id TEXT NOT NULL PRIMARY KEY,
                        netease_id INTEGER NOT NULL,
                        playlist_id INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        album_id INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        album_art_url TEXT,
                        mime_type TEXT NOT NULL,
                        bitrate INTEGER,
                        date_added INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS netease_playlists (
                        id INTEGER NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        cover_url TEXT,
                        song_count INTEGER NOT NULL,
                        last_sync_time INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * Add Google Drive tables.
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gdrive_songs (
                        id TEXT NOT NULL PRIMARY KEY,
                        drive_file_id TEXT NOT NULL,
                        folder_id TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL,
                        album_id INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        album_art_url TEXT,
                        mime_type TEXT NOT NULL,
                        bitrate INTEGER,
                        file_size INTEGER NOT NULL,
                        date_added INTEGER NOT NULL,
                        date_modified INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS gdrive_folders (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        song_count INTEGER NOT NULL DEFAULT 0,
                        last_sync_time INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
        /**
         * Add custom_image_uri column to artists table.
         * Allows users to associate a custom image with each artist.
         * Nullable with DEFAULT NULL so this migration is safe and additive.
         */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE artists ADD COLUMN custom_image_uri TEXT DEFAULT NULL")
            }
        }

        /**
         * Rebuild lyrics table so DB-level default protects inserts that omit isSynced.
         * Also rebuilds other tables to ensure consistent schema with exact Entity definitions.
         * Includes preserving custom_image_uri for artists.
         */
        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("PRAGMA foreign_keys=OFF")

                db.execSQL("DROP TABLE IF EXISTS `lyrics_new`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lyrics_new` (`songId` INTEGER NOT NULL, `content` TEXT NOT NULL, `isSynced` INTEGER NOT NULL DEFAULT 0, `source` TEXT, PRIMARY KEY(`songId`))"
                )

                val hasLyricsTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='lyrics'"
                ).use { cursor ->
                    cursor.moveToFirst()
                }

                if (hasLyricsTable) {
                    val existingColumns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(`lyrics`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIndex >= 0) {
                                existingColumns.add(cursor.getString(nameIndex))
                            }
                        }
                    }

                    if ("songId" in existingColumns && "content" in existingColumns) {
                        val syncedExpr = if ("isSynced" in existingColumns) "COALESCE(`isSynced`, 0)" else "0"
                        val sourceExpr = if ("source" in existingColumns) "`source`" else "NULL"
                        db.execSQL(
                            "INSERT INTO `lyrics_new` (`songId`,`content`,`isSynced`,`source`) SELECT `songId`,`content`,$syncedExpr,$sourceExpr FROM `lyrics`"
                        )
                    }
                }

                db.execSQL("DROP TABLE IF EXISTS `lyrics`")
                db.execSQL("ALTER TABLE `lyrics_new` RENAME TO `lyrics`")

                db.execSQL("DROP TABLE IF EXISTS `songs_new`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `songs_new` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist_name` TEXT NOT NULL, `artist_id` INTEGER NOT NULL, `album_artist` TEXT, `album_name` TEXT NOT NULL, `album_id` INTEGER NOT NULL, `content_uri_string` TEXT NOT NULL, `album_art_uri_string` TEXT, `duration` INTEGER NOT NULL, `genre` TEXT, `file_path` TEXT NOT NULL, `parent_directory_path` TEXT NOT NULL, `is_favorite` INTEGER NOT NULL DEFAULT 0, `lyrics` TEXT DEFAULT null, `track_number` INTEGER NOT NULL DEFAULT 0, `year` INTEGER NOT NULL DEFAULT 0, `date_added` INTEGER NOT NULL DEFAULT 0, `mime_type` TEXT, `bitrate` INTEGER, `sample_rate` INTEGER, `telegram_chat_id` INTEGER, `telegram_file_id` INTEGER, PRIMARY KEY(`id`), FOREIGN KEY(`album_id`) REFERENCES `albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`artist_id`) REFERENCES `artists`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
                )

                val hasSongsTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='songs'"
                ).use { cursor ->
                    cursor.moveToFirst()
                }

                if (hasSongsTable) {
                    val existingColumns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(`songs`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIndex >= 0) {
                                existingColumns.add(cursor.getString(nameIndex))
                            }
                        }
                    }

                    if (
                        "id" in existingColumns &&
                        "title" in existingColumns &&
                        "artist_name" in existingColumns &&
                        "artist_id" in existingColumns &&
                        "album_name" in existingColumns &&
                        "album_id" in existingColumns &&
                        "content_uri_string" in existingColumns &&
                        "duration" in existingColumns &&
                        "file_path" in existingColumns &&
                        "parent_directory_path" in existingColumns
                    ) {
                        fun colOr(col: String, fallback: String): String =
                            if (col in existingColumns) "COALESCE(`$col`, $fallback)" else fallback

                        fun nullableColOrNull(col: String): String =
                            if (col in existingColumns) "`$col`" else "NULL"

                        db.execSQL(
                            """
                            INSERT INTO `songs_new` (
                                `id`,`title`,`artist_name`,`artist_id`,`album_artist`,`album_name`,`album_id`,
                                `content_uri_string`,`album_art_uri_string`,`duration`,`genre`,`file_path`,
                                `parent_directory_path`,`is_favorite`,`lyrics`,`track_number`,`year`,`date_added`,
                                `mime_type`,`bitrate`,`sample_rate`,`telegram_chat_id`,`telegram_file_id`
                            )
                            SELECT
                                `id`,
                                ${colOr("title", "''")},
                                ${colOr("artist_name", "''")},
                                ${colOr("artist_id", "0")},
                                ${nullableColOrNull("album_artist")},
                                ${colOr("album_name", "''")},
                                ${colOr("album_id", "0")},
                                ${colOr("content_uri_string", "''")},
                                ${nullableColOrNull("album_art_uri_string")},
                                ${colOr("duration", "0")},
                                ${nullableColOrNull("genre")},
                                ${colOr("file_path", "''")},
                                ${colOr("parent_directory_path", "''")},
                                ${colOr("is_favorite", "0")},
                                ${nullableColOrNull("lyrics")},
                                ${colOr("track_number", "0")},
                                ${colOr("year", "0")},
                                ${colOr("date_added", "0")},
                                ${nullableColOrNull("mime_type")},
                                ${nullableColOrNull("bitrate")},
                                ${nullableColOrNull("sample_rate")},
                                ${nullableColOrNull("telegram_chat_id")},
                                ${nullableColOrNull("telegram_file_id")}
                            FROM `songs`
                            """.trimIndent()
                        )
                    }
                }

                db.execSQL("DROP TABLE IF EXISTS `songs`")
                db.execSQL("ALTER TABLE `songs_new` RENAME TO `songs`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_title` ON `songs` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_album_id` ON `songs` (`album_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artist_id` ON `songs` (`artist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_artist_name` ON `songs` (`artist_name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_genre` ON `songs` (`genre`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_songs_parent_directory_path` ON `songs` (`parent_directory_path`)")

                db.execSQL("DROP TABLE IF EXISTS `albums_new`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `albums_new` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist_name` TEXT NOT NULL, `artist_id` INTEGER NOT NULL, `album_art_uri_string` TEXT, `song_count` INTEGER NOT NULL, `year` INTEGER NOT NULL, PRIMARY KEY(`id`))"
                )

                val hasAlbumsTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='albums'"
                ).use { cursor ->
                    cursor.moveToFirst()
                }

                if (hasAlbumsTable) {
                    val existingColumns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(`albums`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIndex >= 0) {
                                existingColumns.add(cursor.getString(nameIndex))
                            }
                        }
                    }

                    if (
                        "id" in existingColumns &&
                        "title" in existingColumns &&
                        "artist_name" in existingColumns &&
                        "artist_id" in existingColumns &&
                        "song_count" in existingColumns &&
                        "year" in existingColumns
                    ) {
                        val albumArtExpr = if ("album_art_uri_string" in existingColumns) "`album_art_uri_string`" else "NULL"
                        db.execSQL(
                            """
                            INSERT INTO `albums_new` (`id`,`title`,`artist_name`,`artist_id`,`album_art_uri_string`,`song_count`,`year`)
                            SELECT
                                `id`,
                                `title`,
                                `artist_name`,
                                `artist_id`,
                                $albumArtExpr,
                                `song_count`,
                                `year`
                            FROM `albums`
                            """.trimIndent()
                        )
                    }
                }

                db.execSQL("DROP TABLE IF EXISTS `albums`")
                db.execSQL("ALTER TABLE `albums_new` RENAME TO `albums`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_title` ON `albums` (`title`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_artist_id` ON `albums` (`artist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_artist_name` ON `albums` (`artist_name`)")

                db.execSQL("DROP TABLE IF EXISTS `artists_new`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `artists_new` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `track_count` INTEGER NOT NULL, `image_url` TEXT, `custom_image_uri` TEXT, PRIMARY KEY(`id`))"
                )

                val hasArtistsTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='artists'"
                ).use { cursor ->
                    cursor.moveToFirst()
                }

                if (hasArtistsTable) {
                    val existingColumns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(`artists`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIndex >= 0) {
                                existingColumns.add(cursor.getString(nameIndex))
                            }
                        }
                    }

                    if ("id" in existingColumns && "name" in existingColumns && "track_count" in existingColumns) {
                        val imageUrlExpr = if ("image_url" in existingColumns) "`image_url`" else "NULL"
                        val customImageUriExpr = if ("custom_image_uri" in existingColumns) "`custom_image_uri`" else "NULL"
                        
                        db.execSQL(
                            """
                            INSERT INTO `artists_new` (`id`,`name`,`track_count`,`image_url`, `custom_image_uri`)
                            SELECT
                                `id`,
                                `name`,
                                `track_count`,
                                $imageUrlExpr,
                                $customImageUriExpr
                            FROM `artists`
                            """.trimIndent()
                        )
                    }
                }

                db.execSQL("DROP TABLE IF EXISTS `artists`")
                db.execSQL("ALTER TABLE `artists_new` RENAME TO `artists`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_artists_name` ON `artists` (`name`)")

                db.execSQL("DROP TABLE IF EXISTS `song_artist_cross_ref_new`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `song_artist_cross_ref_new` (`song_id` INTEGER NOT NULL, `artist_id` INTEGER NOT NULL, `is_primary` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`song_id`, `artist_id`), FOREIGN KEY(`song_id`) REFERENCES `songs`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`artist_id`) REFERENCES `artists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )

                val hasCrossRefTable = db.query(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='song_artist_cross_ref'"
                ).use { cursor ->
                    cursor.moveToFirst()
                }

                if (hasCrossRefTable) {
                    val existingColumns = mutableSetOf<String>()
                    db.query("PRAGMA table_info(`song_artist_cross_ref`)").use { cursor ->
                        val nameIndex = cursor.getColumnIndex("name")
                        while (cursor.moveToNext()) {
                            if (nameIndex >= 0) {
                                existingColumns.add(cursor.getString(nameIndex))
                            }
                        }
                    }

                    if ("song_id" in existingColumns && "artist_id" in existingColumns) {
                        val isPrimaryExpr = if ("is_primary" in existingColumns) "COALESCE(`is_primary`, 0)" else "0"
                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO `song_artist_cross_ref_new` (`song_id`,`artist_id`,`is_primary`)
                            SELECT
                                `song_id`,
                                `artist_id`,
                                $isPrimaryExpr
                            FROM `song_artist_cross_ref`
                            """.trimIndent()
                        )
                    }
                }

                db.execSQL("DROP TABLE IF EXISTS `song_artist_cross_ref`")
                db.execSQL("ALTER TABLE `song_artist_cross_ref_new` RENAME TO `song_artist_cross_ref`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_cross_ref_song_id` ON `song_artist_cross_ref` (`song_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_cross_ref_artist_id` ON `song_artist_cross_ref` (`artist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_artist_cross_ref_is_primary` ON `song_artist_cross_ref` (`is_primary`)")

                // album_art_themes is a regenerable cache. Rebuild it to match the
                // current entity schema exactly and avoid legacy default/column drift.
                db.execSQL("DROP TABLE IF EXISTS `album_art_themes`")

                val colorColumns = listOf(
                    "primary", "onPrimary", "primaryContainer", "onPrimaryContainer",
                    "secondary", "onSecondary", "secondaryContainer", "onSecondaryContainer",
                    "tertiary", "onTertiary", "tertiaryContainer", "onTertiaryContainer",
                    "background", "onBackground", "surface", "onSurface",
                    "surfaceVariant", "onSurfaceVariant", "error", "onError",
                    "outline", "errorContainer", "onErrorContainer",
                    "inversePrimary", "inverseSurface", "inverseOnSurface",
                    "surfaceTint", "outlineVariant", "scrim",
                    "surfaceBright", "surfaceDim",
                    "surfaceContainer", "surfaceContainerHigh", "surfaceContainerHighest", "surfaceContainerLow", "surfaceContainerLowest",
                    "primaryFixed", "primaryFixedDim", "onPrimaryFixed", "onPrimaryFixedVariant",
                    "secondaryFixed", "secondaryFixedDim", "onSecondaryFixed", "onSecondaryFixedVariant",
                    "tertiaryFixed", "tertiaryFixedDim", "onTertiaryFixed", "onTertiaryFixedVariant"
                )

                val themePrefixes = listOf("light_", "dark_")
                val columnDefinitions = StringBuilder()

                columnDefinitions.append("albumArtUriString TEXT NOT NULL, ")
                columnDefinitions.append("paletteStyle TEXT NOT NULL, ")

                themePrefixes.forEach { prefix ->
                    colorColumns.forEach { column ->
                        columnDefinitions.append("${prefix}${column} TEXT NOT NULL, ")
                    }
                }

                val columnsSql = columnDefinitions.toString().trimEnd(',', ' ')
                db.execSQL("CREATE TABLE IF NOT EXISTS `album_art_themes` ($columnsSql, PRIMARY KEY(`albumArtUriString`))")

                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }
            }
        }
    }
}
