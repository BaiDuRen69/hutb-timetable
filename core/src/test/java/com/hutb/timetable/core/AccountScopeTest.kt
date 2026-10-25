package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AccountScopeTest {

    @Test
    fun `同一学号得到稳定键`() {
        assertEquals(accountCacheKey("2523040002"), accountCacheKey(" 2523040002 "))
    }

    @Test
    fun `不同学号得到不同键`() {
        assertNotEquals(accountCacheKey("2523040002"), accountCacheKey("2523040003"))
    }

    @Test
    fun `空学号使用匿名键`() {
        assertEquals("anon", accountCacheKey(""))
    }

    @Test
    fun `键中不包含学号明文`() {
        val key = accountCacheKey("2523040002")
        assertEquals(16, key.length)
        assert(!key.contains("2523040002"))
    }
}
