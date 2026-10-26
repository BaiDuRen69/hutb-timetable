package com.hutb.timetable.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** 免责声明正文；首次启动弹窗、登录页与设置页共用，避免多处措辞不一致。 */
const val DISCLAIMER_BODY =
    "本应用为个人开发的非官方工具，与湖南工商大学及学校教务系统无隶属关系。\n\n" +
        "课表数据来自教务系统页面，可能因网络延迟、缓存、解析误差或学校临时调课而滞后、缺失或不准确。" +
        "课表仅供参考，请以教务系统及学校官方通知为准。\n\n" +
        "因依赖本应用提供的信息而造成的缺课、迟到或其他后果，作者不承担相关责任。"

@Composable
fun DisclaimerDialog(onConfirm: () -> Unit) {
    AlertDialog(
        // 使用须知必须点“我已了解”确认；点外部或按返回不算同意
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        title = { Text("使用须知") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "课表仅供参考",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = DISCLAIMER_BODY,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("我已了解") }
        }
    )
}
