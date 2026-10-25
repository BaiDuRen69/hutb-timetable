package com.hutb.timetable.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 敏感凭据本地存储。
 *
 * 只保存 4 个与登录身份直接相关的键：
 * - JSESSIONID：强智 jsxsd 会话，重启后可用于免登录恢复（有效期以服务端为准）
 * - SERVERID：负载均衡节点标识
 * - 学号：查询 JSON API 时需要
 * - 显示名
 *
 * 显示偏好（上次周次/校区、精简教室名、排序方式等）在 [AppPrefs]，
 * 两边的清理互不影响。
 *
 * 使用 EncryptedSharedPreferences 加密落盘（依赖 androidx.security）。
 * 若设备不支持（部分定制 ROM 缺少 KeyStore 提供者），自动降级为普通 SharedPreferences，
 * 并通过 [secureStorage] 让 UI 明确告知用户。
 */
class CredentialStore(context: Context) {

    /** 是否使用系统 KeyStore 加密存储；false 表示已降级为普通 SharedPreferences。 */
    private var fallbackToPlainPrefs = false

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "hutb_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences 不可用，降级为普通存储", e)
        fallbackToPlainPrefs = true
        context.getSharedPreferences("hutb_fallback_prefs", Context.MODE_PRIVATE)
    }

    /** 凭据是否加密落盘。false 时 UI 必须明确告知用户（明文保存风险）。 */
    val secureStorage: Boolean get() = !fallbackToPlainPrefs

    var jsessionId: String
        get() = prefs.getString(KEY_JSESSION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_JSESSION, value).apply()

    var serverId: String
        get() = prefs.getString(KEY_SERVER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SERVER, value).apply()

    var studentId: String
        get() = prefs.getString(KEY_STUDENT_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STUDENT_ID, value).apply()

    var displayName: String
        get() = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DISPLAY_NAME, value).apply()

    val hasSession: Boolean get() = jsessionId.isNotBlank()

    /** 清空登录凭据（保留显示偏好）。 */
    fun clearCredentials() {
        prefs.edit()
            .remove(KEY_JSESSION)
            .remove(KEY_SERVER)
            .remove(KEY_STUDENT_ID)
            .remove(KEY_DISPLAY_NAME)
            .apply()
    }

    companion object {
        private const val TAG = "CredentialStore"
        private const val KEY_JSESSION = "jsession_id"
        private const val KEY_SERVER = "server_id"
        private const val KEY_STUDENT_ID = "student_id"
        private const val KEY_DISPLAY_NAME = "display_name"
    }
}
