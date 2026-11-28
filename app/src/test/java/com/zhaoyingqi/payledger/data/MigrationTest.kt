package com.zhaoyingqi.payledger.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** v1→v2 迁移：预置 v1 数据，跑迁移后逐字段校验（v06 任务 2.2）。 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LedgerDatabase::class.java,
    )

    private val db = "migration-test.db"
    private val zone = ZoneId.of("Asia/Shanghai")
    private val t1 = LocalDate.of(2026, 11, 20).atTime(12, 30).atZone(zone).toInstant().toEpochMilli()
    private val t2 = LocalDate.of(2026, 12, 1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
    private val t3 = LocalDate.of(2026, 12, 5).atTime(18, 5).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun migrate1To2_preserves_rows_and_backfills_capturedAt() {
        helper.createDatabase(db, 1).use { v1 ->
            v1.execSQL(
                "INSERT INTO transactions (amountCents, channel, counterparty, occurredAt, summary, note, createdAt) " +
                    "VALUES (1234, 'WECHAT', '商户A', $t1, '微信付款', '备注1', $t1)",
            )
            v1.execSQL(
                "INSERT INTO transactions (amountCents, channel, counterparty, occurredAt, summary, note, createdAt) " +
                    "VALUES (9999, 'ALIPAY', '商户B', $t2, '支付宝付款', '', $t2)",
            )
            v1.execSQL(
                "INSERT INTO transactions (amountCents, channel, counterparty, occurredAt, summary, note, createdAt) " +
                    "VALUES (100, 'WECHAT', '', $t3, '微信付款', '', $t3)",
            )
        }

        val v2 = helper.runMigrationsAndValidate(db, 2, true, LedgerDatabase.MIGRATION_1_2)
        v2.query("SELECT id, amountCents, channel, counterparty, occurredAt, summary, note, createdAt, " +
            "type, source, capturedAt, timeEstimated, deletedAt, category, dedupKey FROM transactions ORDER BY id").use { c ->
            val rows = mutableListOf<List<Any?>>()
            while (c.moveToNext()) {
                rows.add(
                    listOf(
                        c.getLong(0), c.getLong(1), c.getString(2), c.getString(3), c.getLong(4),
                        c.getString(5), c.getString(6), c.getLong(7), c.getString(8), c.getString(9),
                        c.getLong(10), c.getInt(11) != 0, if (c.isNull(12)) null else c.getLong(12),
                        if (c.isNull(13)) null else c.getString(13), if (c.isNull(14)) null else c.getString(14),
                    ),
                )
            }
            assertEquals(3, rows.size)

            val r1 = rows[0]
            assertEquals(1234L, r1[1])
            assertEquals("WECHAT", r1[2])
            assertEquals("商户A", r1[3])
            assertEquals(t1, r1[4])
            assertEquals("微信付款", r1[5])
            assertEquals("备注1", r1[6])
            assertEquals(t1, r1[7])
            assertEquals("EXPENSE", r1[8])
            assertEquals("NOTIFICATION", r1[9])
            assertEquals("capturedAt 必须用 occurredAt 回填", t1, r1[10])
            assertEquals(false, r1[11])
            assertEquals(null, r1[12])
            assertEquals(null, r1[13])
            assertEquals(null, r1[14])
        }
        v2.close()
    }

    @Test
    fun migrate1To2_creates_indices() {
        helper.createDatabase(db, 1).close()
        val v2 = helper.runMigrationsAndValidate(db, 2, true, LedgerDatabase.MIGRATION_1_2)
        // runMigrationsAndValidate 已按 2.json 校验全部索引存在；这里再显式断言索引名。
        v2.query("SELECT name FROM sqlite_master WHERE type='index'").use { c ->
            val names = mutableSetOf<String>()
            while (c.moveToNext()) names.add(c.getString(0))
            assertTrue("index_transactions_occurredAt" in names)
            assertTrue("index_transactions_channel_amountCents_capturedAt" in names)
            assertTrue("index_transactions_dedupKey" in names)
        }
        v2.close()
    }

    /** v07 任务 1.2：v2 预置记录 → 迁移 → 记录逐字段一致 + category_rules 存在且已预置。 */
    @Test
    fun migrate2To3_preserves_rows_and_creates_rules() {
        val t = LocalDate.of(2026, 12, 1).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        helper.createDatabase(db, 2).use { v2 ->
            v2.execSQL(
                "INSERT INTO transactions (amountCents, channel, counterparty, occurredAt, summary, note, createdAt, " +
                    "type, source, category, dedupKey, timeEstimated, capturedAt, deletedAt) " +
                    "VALUES (4321, 'ALIPAY', '商户C', $t, '支付宝付款 · 商户C', 'v2记录', $t, " +
                    "'EXPENSE', 'ACCESSIBILITY', NULL, 'deadbeef00112233', 0, $t, NULL)",
            )
        }

        val v3 = helper.runMigrationsAndValidate(db, 3, true, LedgerDatabase.MIGRATION_2_3)
        v3.query("SELECT amountCents, channel, counterparty, occurredAt, summary, note, createdAt, " +
            "type, source, dedupKey, capturedAt, timeEstimated FROM transactions").use { c ->
            assertTrue(c.moveToNext())
            assertEquals(4321L, c.getLong(0))
            assertEquals("ALIPAY", c.getString(1))
            assertEquals("商户C", c.getString(2))
            assertEquals(t, c.getLong(3))
            assertEquals("支付宝付款 · 商户C", c.getString(4))
            assertEquals("v2记录", c.getString(5))
            assertEquals(t, c.getLong(6))
            assertEquals("EXPENSE", c.getString(7))
            assertEquals("ACCESSIBILITY", c.getString(8))
            assertEquals("deadbeef00112233", c.getString(9))
            assertEquals(t, c.getLong(10))
            assertEquals(0, c.getInt(11))
            assertFalse(c.moveToNext())
        }
        // 升级上来的库同样有预置规则
        v3.query("SELECT COUNT(*) FROM category_rules").use { c ->
            c.moveToFirst()
            assertTrue("迁移后必须预置内置规则", c.getInt(0) > 0)
        }
        v3.query("SELECT COUNT(*) FROM transactions").use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }
        v3.close()
    }
}
