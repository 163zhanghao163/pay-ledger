package com.zhaoyingqi.payledger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionRecord::class, CategoryRule::class],
    version = 3,
    exportSchema = true,
)
abstract class LedgerDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        fun build(context: Context): LedgerDatabase =
            Room.databaseBuilder(context.applicationContext, LedgerDatabase::class.java, "payledger.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(PRESET_CALLBACK)
                .build()

        /** v1→v2：一次加齐 7 列 + 3 索引；capturedAt 用 occurredAt 回填（v1 的 occurredAt 即到达时间）。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN type TEXT NOT NULL DEFAULT 'EXPENSE'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'NOTIFICATION'")
                db.execSQL("ALTER TABLE transactions ADD COLUMN category TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN dedupKey TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN timeEstimated INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE transactions ADD COLUMN capturedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE transactions SET capturedAt = occurredAt")
                db.execSQL("ALTER TABLE transactions ADD COLUMN deletedAt INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_occurredAt ON transactions(occurredAt)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_transactions_channel_amountCents_capturedAt " +
                        "ON transactions(channel, amountCents, capturedAt)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_dedupKey ON transactions(dedupKey)")
            }
        }

        /** v2→v3：只建 category_rules 表与唯一索引，不触碰 transactions；顺带预置内置规则（升级库也要有）。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS category_rules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "keyword TEXT NOT NULL, " +
                        "category TEXT NOT NULL, " +
                        "enabled INTEGER NOT NULL DEFAULT 1, " +
                        "priority INTEGER NOT NULL DEFAULT 10)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_category_rules_keyword ON category_rules(keyword)")
                insertPresets(db)
            }
        }

        /**
         * 首次建库预置内置规则（v07 D5）。internal 供测试的 inMemory 库挂载
         * （真实构建走 [build]，已自动挂载）。
         */
        internal val PRESET_CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                insertPresets(db)
            }
        }

        private fun insertPresets(db: SupportSQLiteDatabase) {
            CategoryRule.PRESETS.forEach { (keyword, category, priority) ->
                db.execSQL(CategoryRule.presetInsertSql(keyword, category, priority))
            }
        }
    }
}
