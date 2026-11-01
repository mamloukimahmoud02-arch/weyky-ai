package com.example.core.intelligence.knowledge.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KnowledgeDao
import com.example.core.intelligence.knowledge.db.entity.KnowledgeFollowUpBranchEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicDetailEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity

/**
 * Room Database for WEYKY's local knowledge storage foundation (Phase 2B.1).
 *
 * - Database file name: `weyky_knowledge.db`
 * - Version: `1`
 * - `exportSchema = true`
 * - Never enables `allowMainThreadQueries()`.
 * - No synthetic `Migration(1, 2)` is registered in production.
 */
@Database(
    entities = [
        KnowledgePackMetadataEntity::class,
        KnowledgeTopicHeaderEntity::class,
        KnowledgeKeywordEntity::class,
        KnowledgeKeywordTokenEntity::class,
        KnowledgeTopicDetailEntity::class,
        KnowledgeFollowUpBranchEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(KnowledgeTypeConverters::class)
abstract class WeykyKnowledgeDatabase : RoomDatabase() {

    abstract fun knowledgeDao(): KnowledgeDao

    companion object {
        const val DATABASE_NAME: String = "weyky_knowledge.db"
        const val SCHEMA_VERSION: Int = 1

        @Volatile
        private var INSTANCE: WeykyKnowledgeDatabase? = null

        /**
         * Returns the process-wide singleton [WeykyKnowledgeDatabase] instance.
         * Never enables `allowMainThreadQueries()`.
         */
        fun getInstance(context: Context): WeykyKnowledgeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext, DATABASE_NAME).also {
                    INSTANCE = it
                }
            }
        }

        /**
         * Builds a persistent [WeykyKnowledgeDatabase] at [databaseName] without
         * `allowMainThreadQueries()`.
         */
        fun buildDatabase(
            context: Context,
            databaseName: String = DATABASE_NAME
        ): WeykyKnowledgeDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                WeykyKnowledgeDatabase::class.java,
                databaseName
            ).build()
        }

        internal fun clearInstanceForTesting() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
