package com.hutb.timetable.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * 自动同步前的网络检查。
 *
 * “仅 Wi-Fi”实际按“非计费网络”判断：Wi-Fi、以太网和不计费的手机热点都允许，
 * 计费移动网络不自动请求。手动刷新不受此限制。
 */
object NetworkStatus {

    fun autoSyncAllowed(context: Context, wifiOnly: Boolean): Boolean {
        val manager = context.applicationContext
            .getSystemService(ConnectivityManager::class.java) ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }
        return !wifiOnly || capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
