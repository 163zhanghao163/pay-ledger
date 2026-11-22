package com.zhaoyingqi.payledger.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {

    @Test fun yuan_decimal() {
        assertEquals(1250L, parseAmountToCents("12.5"))
    }

    @Test fun yuan_two_decimals() {
        assertEquals(12800L, parseAmountToCents("128.00"))
    }

    @Test fun yuan_integer() {
        assertEquals(2000L, parseAmountToCents("20"))
    }

    @Test fun currency_prefix_accepted() {
        assertEquals(2000L, parseAmountToCents("¥20"))
        assertEquals(1234L, parseAmountToCents("￥12.34"))
    }

    @Test fun fractional_cents_padded() {
        assertEquals(5L, parseAmountToCents("0.05"))
    }

    @Test fun zero_rejected() {
        assertNull(parseAmountToCents("0"))
        assertNull(parseAmountToCents("0.00"))
    }

    @Test fun invalid_rejected() {
        assertNull(parseAmountToCents(""))
        assertNull(parseAmountToCents("abc"))
        assertNull(parseAmountToCents("12.345"))
        assertNull(parseAmountToCents("-5"))
    }
}
