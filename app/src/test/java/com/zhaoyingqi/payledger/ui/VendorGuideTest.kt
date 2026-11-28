package com.zhaoyingqi.payledger.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** v08 任务 3.1：厂商 → 指引 key 纯映射。 */
class VendorGuideTest {

    @Test
    fun known_vendors_map_to_own_keys() {
        assertEquals("xiaomi", vendorGuideKey("Xiaomi"))
        assertEquals("huawei", vendorGuideKey("HUAWEI"))
        assertEquals("honor", vendorGuideKey("HONOR"))
        assertEquals("oppo", vendorGuideKey("OPPO"))
        assertEquals("vivo", vendorGuideKey("vivo"))
        assertEquals("oneplus", vendorGuideKey("OnePlus"))
        assertEquals("samsung", vendorGuideKey("samsung"))
    }

    @Test
    fun sub_brands_map_to_parent_brand() {
        assertEquals("xiaomi", vendorGuideKey("Redmi"))
        assertEquals("xiaomi", vendorGuideKey("POCO"))
        assertEquals("oppo", vendorGuideKey("realme"))
        assertEquals("vivo", vendorGuideKey("iQOO"))
        assertEquals("huawei", vendorGuideKey("HUAWEI-HarmonyOS"))
    }

    @Test
    fun unknown_vendor_falls_back_to_other() {
        assertEquals("other", vendorGuideKey("Google"))
        assertEquals("other", vendorGuideKey("Nokia"))
        assertEquals("other", vendorGuideKey(""))
        assertEquals("other", vendorGuideKey("  "))
    }

    @Test
    fun guide_steps_cover_every_key() {
        val expected = setOf("xiaomi", "huawei", "honor", "oppo", "vivo", "oneplus", "samsung", "other")
        assertEquals(expected, VENDOR_GUIDE_STEPS.keys)
        // 每个品牌至少 2 条步骤，避免渲染空章节
        VENDOR_GUIDE_STEPS.forEach { (key, steps) ->
            assert(steps.size >= 2) { "$key steps should not be empty" }
        }
    }
}
