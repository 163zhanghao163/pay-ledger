package com.zhaoyingqi.payledger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 分类规则（v07 D5）：商户名包含 [keyword] 即归入 [category]，按 [priority] 降序取第一条命中。
 * 内置规则随库预置（新建与升级都会插入），用户可在规则页增删改与启停；归类只在入账时发生一次，规则修改不追溯。
 */
@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["keyword"], unique = true)],
)
data class CategoryRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val category: String,
    val enabled: Boolean = true,
    val priority: Int = 10,
) {
    companion object {
        /**
         * 内置规则（v07 D5）：覆盖常见商户名；更具体的词（如「美团打车」）优先级更高，
         * 避免被宽泛词（「美团」→餐饮）抢先命中。
         */
        val PRESETS: List<Triple<String, String, Int>> = listOf(
            // 交通
            Triple("滴滴打车", "交通", 20),
            Triple("美团打车", "交通", 20),
            Triple("高德打车", "交通", 20),
            Triple("12306", "交通", 20),
            Triple("滴滴", "交通", 10),
            Triple("地铁", "交通", 10),
            Triple("公交", "交通", 10),
            Triple("加油", "交通", 10),
            Triple("停车", "交通", 10),
            Triple("出行", "交通", 10),
            // 餐饮
            Triple("饿了么", "餐饮", 20),
            Triple("肯德基", "餐饮", 20),
            Triple("麦当劳", "餐饮", 20),
            Triple("星巴克", "餐饮", 20),
            Triple("瑞幸", "餐饮", 20),
            Triple("蜜雪冰城", "餐饮", 20),
            Triple("美团", "餐饮", 10),
            Triple("外卖", "餐饮", 10),
            Triple("餐厅", "餐饮", 10),
            Triple("奶茶", "餐饮", 10),
            Triple("咖啡", "餐饮", 10),
            Triple("汉堡", "餐饮", 10),
            // 购物
            Triple("拼多多", "购物", 20),
            Triple("京东", "购物", 20),
            Triple("淘宝", "购物", 20),
            Triple("天猫", "购物", 20),
            Triple("盒马", "购物", 20),
            Triple("山姆", "购物", 20),
            Triple("超市", "购物", 10),
            Triple("便利店", "购物", 10),
            Triple("商场", "购物", 10),
            Triple("百货", "购物", 10),
            // 娱乐
            Triple("电影院", "娱乐", 20),
            Triple("猫眼", "娱乐", 20),
            Triple("腾讯视频", "娱乐", 20),
            Triple("爱奇艺", "娱乐", 20),
            Triple("网易云", "娱乐", 20),
            Triple("游戏", "娱乐", 10),
            Triple("电影", "娱乐", 10),
            Triple("会员", "娱乐", 10),
            // 居住
            Triple("房租", "居住", 20),
            Triple("物业", "居住", 20),
            Triple("水费", "居住", 20),
            Triple("电费", "居住", 20),
            Triple("燃气", "居住", 20),
            Triple("宽带", "居住", 20),
            // 医疗
            Triple("医院", "医疗", 20),
            Triple("药店", "医疗", 20),
            Triple("药房", "医疗", 20),
            Triple("诊所", "医疗", 20),
            Triple("体检", "医疗", 20),
            // 教育
            Triple("学费", "教育", 20),
            Triple("培训", "教育", 20),
            Triple("书店", "教育", 20),
            Triple("图书", "教育", 20),
            Triple("考试", "教育", 20),
            // 通讯
            Triple("话费", "通讯", 20),
            Triple("流量", "通讯", 20),
            Triple("中国移动", "通讯", 20),
            Triple("中国联通", "通讯", 20),
            Triple("中国电信", "通讯", 20),
        )

        val CATEGORY_LABELS = listOf("餐饮", "交通", "购物", "娱乐", "居住", "医疗", "教育", "通讯", "其他")

        /** 预置规则的 INSERT SQL（onCreate 与 MIGRATION_2_3 共用；OR IGNORE 保证幂等）。 */
        fun presetInsertSql(keyword: String, category: String, priority: Int): String =
            "INSERT OR IGNORE INTO category_rules (keyword, category, enabled, priority) " +
                "VALUES ('$keyword', '$category', 1, $priority)"
    }
}
