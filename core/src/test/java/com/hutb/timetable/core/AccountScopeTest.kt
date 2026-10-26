package com.hutb.timetable.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
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
    fun `空学号统一归一到匿名键`() {
        // 仓库层在学号为空时直接跳过读写缓存；这里的 anon 只作为底层兜底，
        // 必须保证空白学号也归一到同一个键，不能产生 "anon" 之外的变体
        assertEquals("anon", accountCacheKey(""))
        assertEquals("anon", accountCacheKey("   "))
    }

    @Test
    fun `键中不包含学号明文`() {
        val key = accountCacheKey("2523040002")
        assertEquals(16, key.length)
        assertFalse(key.contains("2523040002"))
    }

    @Test
    fun `学号未知时才回退到上次账号分区`() {
        val last = accountCacheKey("2523040002")
        assertEquals(last, readableAccountKey("", last))
        assertEquals(last, readableAccountKey(null, last))
    }

    @Test
    fun `学号已知时绝不读取上一个账号的分区`() {
        val lastA = accountCacheKey("2523040002")
        assertEquals(accountCacheKey("2523040003"), readableAccountKey("2523040003", lastA))
    }

    @Test
    fun `新登录身份未解析且旧键已清空时不读任何分区`() {
        // persistSession 会清空 lastAccountKey；此时不能退回 A 的缓存
        assertNull(readableAccountKey("", ""))
        assertNull(readableAccountKey(null, null))
    }
}
