package com.hutb.timetable.ui.timetable

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.hutb.timetable.core.HtmlStructureProbe
import java.io.File

/**
 * 页面快照对话框。
 *
 * 教务系统改版时，解析失败最常见的原因是"我们看到的 HTML 和预期不一致"。
 * 这个对话框做两件事：
 *
 * 1. 列出 App 抓到的原始页面快照；
 * 2. 对选中的快照运行 [HtmlStructureProbe]，把页面压缩成**结构指纹**
 *    （表单字段、下拉选项、表格表头、候选接口、学期线索），
 *    用户一键复制即可粘贴反馈，**不需要传输上百 KB 的原始 HTML**。
 */
@Composable
fun SnapshotDialog(
    snapshots: List<File>,
    onDismiss: () -> Unit,
    onRefreshSnapshots: () -> Unit
) {
    val context = LocalContext.current
    var selectedIndex by remember { mutableStateOf(0) }

    // 结构指纹按选中项懒加载
    val selected = snapshots.getOrNull(selectedIndex)
    val fingerprint = remember(selected?.name) {
        selected?.let { file ->
            runCatching { HtmlStructureProbe.probe(file.readText()).render() }.getOrElse { "读取失败：${it.message}" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("页面快照（排查改版用）") },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(max = 420.dp)
            ) {
                if (snapshots.isEmpty()) {
                    Text(
                        text = "暂无快照。请先登录并尝试刷新课表，App 会自动保存抓到的页面。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "已保存 ${snapshots.size} 份快照，点击查看结构指纹：",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    snapshots.take(20).forEachIndexed { index, file ->
                        val active = index == selectedIndex
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(
                                    if (active) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    }
                                )
                                .clickable { selectedIndex = index }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }

                    if (fingerprint != null) {
                        Text(
                            text = "\n结构指纹（可直接复制发我）：",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            text = fingerprint,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(6.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    val text = fingerprint
                    if (text.isNullOrBlank()) {
                        Toast.makeText(context, "请先选择一份快照", Toast.LENGTH_SHORT).show()
                    } else {
                        copyToClipboard(context, text)
                        Toast.makeText(context, "结构指纹已复制，粘贴给我即可", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("复制指纹") }

                TextButton(onClick = {
                    val all = snapshots.take(20).mapNotNull { file ->
                        runCatching { HtmlStructureProbe.probe(file.readText()).render() }.getOrNull()
                    }.joinToString("\n\n================\n\n")
                    if (all.isBlank()) {
                        Toast.makeText(context, "没有可复制的快照", Toast.LENGTH_SHORT).show()
                    } else {
                        copyToClipboard(context, all)
                        Toast.makeText(context, "全部快照指纹已复制", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("复制全部") }

                TextButton(onClick = onRefreshSnapshots) { Text("刷新列表") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/**
 * 复制文本到系统剪贴板。
 */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("hutb-snapshot", text))
}
