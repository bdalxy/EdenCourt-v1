package com.bdalxy.edencourt.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

/**
 * 头像组件（T6.2B，企划书 5.1.2「头像=首字母，后期支持图片」）。
 * - avatarPath 非空：异步加载本地头像图并以圆形裁剪展示
 * - avatarPath 为空：统一主题色圆形色块 + 名称首字（默认头像）
 * 会话列表/角色卡列表/聊天页顶部统一复用，保证头像样式一致。
 */
@Composable
fun CharacterAvatar(
    avatarPath: String?,
    name: String,
    modifier: Modifier = Modifier,
    textSize: TextUnit = MaterialTheme.typography.titleMedium.fontSize,
) {
    if (avatarPath != null) {
        // 异步加载头像图（小图 ≤256px，避免阻塞主线程）
        val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
            initialValue = null,
            avatarPath,
        ) {
            value = withContext(Dispatchers.IO) { loadAvatarBitmap(avatarPath) }?.asImageBitmap()
        }
        // 本地变量避免委托属性智能转换限制（Kotlin 委托属性无法 smart cast）
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = modifier.clip(CircleShape),
            )
            return
        }
    }
    // 默认头像：统一主题色圆形色块 + 首字
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.take(1),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = textSize, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 从本地路径加载头像 Bitmap：按目标尺寸 256px 采样压缩，防止大图内存过大（T6.3 关注内存） */
fun loadAvatarBitmap(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > AVATAR_SIZE_PX || bounds.outHeight / sample > AVATAR_SIZE_PX) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    BitmapFactory.decodeFile(path, opts)
}.getOrNull()

/**
 * 从系统图库 URI 读取原图，居中正方形裁剪并缩放到标准头像尺寸，保存到应用私有目录。
 * @return 保存后的文件绝对路径；读取/处理失败返回 null（T6.2B 简化圆形裁剪方案）
 */
suspend fun saveAvatarFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            ?: return@runCatching null
        // 居中正方形裁剪：以较短边为边长，取中心区域
        val size = min(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2
        val cropped = Bitmap.createBitmap(source, x, y, size, size)
        // 缩放到标准头像尺寸（显示时以圆形裁切呈现）
        val scaled = Bitmap.createScaledBitmap(cropped, AVATAR_SIZE_PX, AVATAR_SIZE_PX, true)
        // 写入私有目录 avatars/（应用卸载自动清理，无需存储权限）
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        val file = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, AVATAR_JPEG_QUALITY, it) }
        // 及时释放中间位图，避免内存峰值（T6.3 关注内存）
        if (scaled != cropped) scaled.recycle()
        if (cropped != source) cropped.recycle()
        if (source != cropped) source.recycle()
        file.absolutePath
    }.getOrNull()
}

/** 头像目标尺寸（像素），保存/加载统一按此压缩 */
private const val AVATAR_SIZE_PX = 256
/** 头像 JPEG 压缩质量 */
private const val AVATAR_JPEG_QUALITY = 90
