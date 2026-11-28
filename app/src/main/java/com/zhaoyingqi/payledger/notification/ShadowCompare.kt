package com.zhaoyingqi.payledger.notification

/**
 * bg D7 影子比对（仅 debug 构建调用）：被新门槛拦下的事件按旧全量流程重跑，
 * 「旧流程能入账而新流程没有」计 1 个差异，其余计 0。
 * 差异明细只写结构化说明（[DESCRIPTION]），不写通知原文。
 */
internal object ShadowCompare {

    const val DESCRIPTION = "影子差异：旧流程入账、新流程未入账"

    fun diffCount(oldParsed: ParsedPayment?, newParsed: ParsedPayment?): Int =
        if (oldParsed != null && newParsed == null) 1 else 0
}
