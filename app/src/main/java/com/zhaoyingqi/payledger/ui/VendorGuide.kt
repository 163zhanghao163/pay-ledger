package com.zhaoyingqi.payledger.ui

/**
 * 厂商 → 帮助页指引 key 的纯映射（v08 D7）：
 * 已知品牌归到自己的 key，子品牌归入母品牌（redmi/poco→xiaomi、realme→oppo、iqoo→vivo），
 * 未知厂商走通用「other」章节。只做映射，不读取设备其他信息。
 */
fun vendorGuideKey(manufacturer: String): String {
    val m = manufacturer.lowercase().trim()
    return when {
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> "xiaomi"
        m.contains("huawei") || m.contains("harmony") -> "huawei"
        m.contains("honor") -> "honor"
        m.contains("realme") -> "oppo"
        m.contains("oppo") -> "oppo"
        m.contains("vivo") || m.contains("iqoo") -> "vivo"
        m.contains("oneplus") -> "oneplus"
        m.contains("samsung") -> "samsung"
        else -> "other"
    }
}

/** 各品牌的自启动 / 后台 / 省电设置步骤（帮助页与设置指南展示用）。 */
val VENDOR_GUIDE_STEPS: Map<String, List<String>> = mapOf(
    "xiaomi" to listOf(
        "系统设置 → 应用设置 → 应用管理 → PayLedger：开启「自启动」",
        "应用详情 → 省电策略：选择「无限制」",
        "通知使用权开启后，不要在最近任务里上滑锁死 PayLedger",
    ),
    "huawei" to listOf(
        "设置 → 应用和服务 → 应用启动管理 → PayLedger：关闭「自动管理」",
        "手动管理中允许：自启动、关联启动、后台活动",
        "设置 → 电池：不要对 PayLedger 使用深度省电",
    ),
    "honor" to listOf(
        "设置 → 应用 → 应用启动管理 → PayLedger：关闭「自动管理」",
        "手动管理中允许：自启动、关联启动、后台活动",
        "设置 → 电池 → 更多设置：保持 PayLedger 不受休眠优化限制",
    ),
    "oppo" to listOf(
        "设置 → 应用 → 应用管理 → PayLedger：开启「自启动」",
        "应用详情 → 耗电管理：允许「完全后台行为」",
        "设置 → 电池：关闭对 PayLedger 的智能睡眠限制",
    ),
    "vivo" to listOf(
        "设置 → 应用与权限 → 应用管理 → PayLedger：开启「自启动」",
        "设置 → 电池 → 后台高耗电：允许 PayLedger",
        "i 管家中不要把 PayLedger 加入清理白名单之外",
    ),
    "oneplus" to listOf(
        "设置 → 应用与权限 → 应用管理 → PayLedger：开启「自启动」",
        "应用详情 → 电池：选择「不限制」后台行为",
        "设置 → 电池 → 更多设置：关闭深度睡眠对 PayLedger 的限制",
    ),
    "samsung" to listOf(
        "设置 → 应用程序 → PayLedger → 电池：允许「后台活动」",
        "设置 → 电池和设备维护 → 电池 → 应用省电：不对 PayLedger 启用休眠",
        "若最近任务清理后漏记，关闭「自动优化」对 PayLedger 的处理",
    ),
    "other" to listOf(
        "系统设置中允许 PayLedger「自启动」",
        "关闭系统对 PayLedger 的电池优化",
        "若被省电策略清理导致漏记，请把 PayLedger 加入后台白名单",
    ),
)
