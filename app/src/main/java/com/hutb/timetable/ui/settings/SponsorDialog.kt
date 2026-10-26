package com.hutb.timetable.ui.settings

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.hutb.timetable.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 赞助渠道及其收款码。 */
private enum class SponsorChannel(
    val title: String,
    val hint: String,
    @DrawableRes val qrRes: Int,
    val accent: Color
) {
    ALIPAY("支付宝", "推荐使用支付宝扫码", R.drawable.alipay_qr, Color(0xFF1677FF)),
    WECHAT("微信", "推荐使用微信支付", R.drawable.wechat_qr, Color(0xFF07C160))
}

/** 设置页“请我喝杯咖啡”入口行。 */
@Composable
internal fun SponsorEntry(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "请我喝杯咖啡",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "如果您觉得这个应用对您有帮助",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** 赞助弹窗：先选渠道，再展示对应收款码。 */
@Composable
internal fun SponsorDialog(onDismiss: () -> Unit) {
    var channel by remember { mutableStateOf<SponsorChannel?>(null) }
    val selected = channel
    if (selected == null) {
        SupportChannelDialog(onDismiss = onDismiss, onSelect = { channel = it })
    } else {
        RewardQrDialog(channel = selected, onDismiss = onDismiss)
    }
}

@Composable
private fun SupportChannelDialog(
    onDismiss: () -> Unit,
    onSelect: (SponsorChannel) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("支持作者", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "感谢你的支持！选择一种方式，打赏给作者买杯咖啡吧。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))
                SponsorOptionRow(SponsorChannel.ALIPAY, onSelect)
                Spacer(modifier = Modifier.height(10.dp))
                SponsorOptionRow(SponsorChannel.WECHAT, onSelect)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun SponsorOptionRow(
    channel: SponsorChannel,
    onSelect: (SponsorChannel) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSelect(channel) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(channel.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.QrCode2,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = channel.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RewardQrDialog(
    channel: SponsorChannel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val save = {
        scope.launch {
            val message = saveQrToGallery(context, channel.qrRes, channel.title)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            save()
        } else {
            Toast.makeText(context, "未获得存储权限，无法保存图片", Toast.LENGTH_LONG).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 420.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "${channel.title}收款码",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Image(
                        painter = painterResource(channel.qrRes),
                        contentDescription = "${channel.title}收款码",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                            .padding(10.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "扫描二维码支持作者，或点击下方按钮保存到相册",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            save()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存到相册")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("取消")
                }
            }
        }
    }
}

/** 把收款码写入相册；Android 9 及以下需已授予存储权限。 */
private suspend fun saveQrToGallery(
    context: Context,
    @DrawableRes qrRes: Int,
    channelName: String
): String = withContext(Dispatchers.IO) {
    try {
        val bitmap = BitmapFactory.decodeResource(context.resources, qrRes)
            ?: return@withContext "保存失败：无法读取收款码图片"
        val fileName = "课表查询打赏_${channelName}_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date()) + ".png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        }
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/课表查询"
            )
        } else {
            val dir = File(Environment.getExternalStorageDirectory(), "Pictures/课表查询")
            if (!dir.exists() && !dir.mkdirs()) {
                return@withContext "保存失败：无法创建目录"
            }
            values.put(MediaStore.Images.Media.DATA, File(dir, fileName).absolutePath)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext "保存失败：相册不可用"
        resolver.openOutputStream(uri)?.use { out ->
            if (!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)) {
                return@withContext "保存失败：图片写入失败"
            }
        } ?: return@withContext "保存失败：无法写入相册"
        "已保存到相册：Pictures/课表查询"
    } catch (e: Exception) {
        "保存失败：${e.message ?: "未知错误"}"
    }
}
