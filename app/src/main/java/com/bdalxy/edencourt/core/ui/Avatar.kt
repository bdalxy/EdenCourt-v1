package com.bdalxy.edencourt.core.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.exifinterface.media.ExifInterface
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
        // 用 remember + LaunchedEffect 而非 produceState：后者对带显式类型参数+位置 key 的
        // 调用存在 ProduceStateDoesNotAssignValue 误报，等价写法规避 lint
        var loaded by remember(avatarPath) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(avatarPath) {
            loaded = withContext(Dispatchers.IO) { loadAvatarBitmap(avatarPath) }?.asImageBitmap()
        }
        // 委托属性无法 smart cast，先取到本地变量再判空
        val current = loaded
        if (current != null) {
            Image(
                bitmap = current,
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

/** 从本地路径加载头像 Bitmap：按目标尺寸 512px 采样压缩，防止大图内存过大（T6.3 关注内存） */
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

/** 从本地路径加载立绘背景 Bitmap：按最大边 1024px 采样压缩（B3 置顶大卡背景用，比头像清晰又控制内存） */
fun loadArtworkBitmap(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > ARTWORK_DISPLAY_MAX_PX || bounds.outHeight / sample > ARTWORK_DISPLAY_MAX_PX) {
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
        // 按目标尺寸 2 倍采样解码（避免全尺寸解码 OOM），再居中裁剪 + 缩放
        val source = decodeUriSampled(context, uri, AVATAR_SIZE_PX * 2) ?: return@runCatching null
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
        source.recycle()
        file.absolutePath
    }.getOrNull()
}

/** 头像目标尺寸（像素），保存/加载统一按此压缩 */
private const val AVATAR_SIZE_PX = 512
/** 头像 JPEG 压缩质量 */
private const val AVATAR_JPEG_QUALITY = 90
/** 立绘大图最大边（像素），防止超大图内存过高；超限等比缩放到此尺寸内（B3） */
private const val ARTWORK_MAX_EDGE_PX = 2048
/** 立绘 JPEG 压缩质量 */
private const val ARTWORK_JPEG_QUALITY = 85
/** 立绘背景显示采样上限（像素，加载压缩用） */
private const val ARTWORK_DISPLAY_MAX_PX = 2048
/** 裁剪头像框占可视区边长比例（B3-3b；框固定中央，用户拖动/缩放立绘定位头像） */
private const val FRAME_FRAC = 0.72f

/**
 * 从系统图库 URI 读取原图，等比缩放到最大边不超过 ARTWORK_MAX_EDGE_PX，保存到私有目录 artworks/。
 * @return 保存后的文件绝对路径；读取/处理失败返回 null（B3 置顶大卡背景用，保留整图不改裁剪）
 */
suspend fun saveArtworkFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        // 按最大边采样解码（避免全尺寸解码 OOM），再兜底等比缩放
        val source = decodeUriSampled(context, uri, ARTWORK_MAX_EDGE_PX) ?: return@runCatching null
        // 等比缩放：仅当最大边超限时压缩，保持立绘整体完整
        val scale = ARTWORK_MAX_EDGE_PX.toFloat() / maxOf(source.width, source.height)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            source
        }
        val dir = File(context.filesDir, "artworks").apply { mkdirs() }
        val file = File(dir, "artwork_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, ARTWORK_JPEG_QUALITY, it) }
        // 及时释放中间位图，避免内存峰值
        if (scaled != source) scaled.recycle()
        source.recycle()
        file.absolutePath
    }.getOrNull()
}

/**
 * 从图库 URI 按最大边限制采样解码（T6.3 关注内存）。
 * 先 inJustDecodeBounds 读取原图尺寸，再按 2 的幂 inSampleSize 采样解码，
 * 避免全尺寸解码超大照片导致 OOM。URI 流只能消费一次，故 bounds 与采样各开一次流。
 */
private fun decodeUriSampled(context: Context, uri: Uri, maxEdge: Int): Bitmap? {
    // 第一遍：只读尺寸不占内存
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    // 计算 2 的幂采样倍数：解码后最长边 ≤ maxEdge
    var sample = 1
    while (bounds.outWidth / sample > maxEdge || bounds.outHeight / sample > maxEdge) {
        sample *= 2
    }
    // 第二遍：按采样倍数实际解码
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, opts)
    } ?: return null
    // 纠正 EXIF 方向（相册照片可能带旋转信息，解码默认不应用）
    return applyExifRotation(context, uri, bitmap)
}

/**
 * 读取图库 URI 的 EXIF 方向并按需旋转位图（T6.2B 图片处理）。
 * 只读 EXIF 头不读全图，失败（如非 JPEG）则忽略旋转返回原图。
 */
private fun applyExifRotation(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
    val degrees = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val exif = ExifInterface(input)
            when (exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    }.getOrDefault(0)
    if (degrees == 0) return bmp
    // 旋转生成新图，释放原图避免内存峰值
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
    if (rotated != bmp) bmp.recycle()
    return rotated
}

/**
 * B3-3b：立绘裁剪生成头像。
 * 在裁剪器里，立绘图以 contain 初始铺满可视区（边长 drawSizePx），用户拖动 offset、缩放 scale，
 * 中央固定头像框（框边长 = drawSizePx * FRAME_FRAC）。完成即把框内对应的立绘区域裁切缩放为头像保存。
 * @return 头像文件绝对路径；裁切越界/处理失败返回 null
 */
suspend fun saveAvatarFromCrop(
    context: Context,
    artworkPath: String,
    scale: Float,
    offsetPx: Offset,
    drawSizePx: Float,
): String? = withContext(Dispatchers.IO) {
    runCatching {
        // 全分辨率解码立绘：裁剪头像需要最高精度，避免头像模糊
        val bmp = BitmapFactory.decodeFile(artworkPath) ?: return@runCatching null
        val bmpW = bmp.width.toFloat()
        val bmpH = bmp.height.toFloat()
        // 初始 contain 缩放基准：位图适配到可视区
        val base = drawSizePx / maxOf(bmpW, bmpH)
        val frame = drawSizePx * FRAME_FRAC
        // 缩放后图片上每位的缩放系数（立绘 px → 可视区 px）
        val unit = base * scale
        // 框中心与图片中心的可视区偏移
        val srcSize = frame / unit
        val srcX = bmpW / 2f - (frame / 2f + offsetPx.x) / unit
        val srcY = bmpH / 2f - (frame / 2f + offsetPx.y) / unit
        // 边界收敛，防止越界导致 createBitmap 崩溃
        val l = srcX.coerceIn(0f, bmpW - 1f)
        val t = srcY.coerceIn(0f, bmpH - 1f)
        val r = (srcX + srcSize).coerceIn(1f, bmpW)
        val b = (srcY + srcSize).coerceIn(1f, bmpH)
        val crop = Bitmap.createBitmap(
            bmp,
            l.toInt(),
            t.toInt(),
            (r - l).toInt().coerceAtLeast(1),
            (b - t).toInt().coerceAtLeast(1),
        )
        val scaled = if (crop.width == AVATAR_SIZE_PX && crop.height == AVATAR_SIZE_PX) {
            crop
        } else {
            Bitmap.createScaledBitmap(crop, AVATAR_SIZE_PX, AVATAR_SIZE_PX, true)
        }
        // 圆形裁切：生成透明背景 + 圆形头像（头像组件以圆形展示，裁成圆形观感一致）
        val circle = Bitmap.createBitmap(AVATAR_SIZE_PX, AVATAR_SIZE_PX, Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
        }
        val canvas = android.graphics.Canvas(circle)
        canvas.drawCircle(AVATAR_SIZE_PX / 2f, AVATAR_SIZE_PX / 2f, AVATAR_SIZE_PX / 2f - 1f, paint)
        // Xfermode SRC_IN：以圆为 src，把缩略图内容合成进圆内
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(scaled, 0f, 0f, paint)
        val dir = File(context.filesDir, "avatars").apply { mkdirs() }
        val file = File(dir, "crop_${System.currentTimeMillis()}.png")
        file.outputStream().use { circle.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        if (scaled != crop) scaled.recycle()
        if (crop != bmp) crop.recycle()
        if (circle != scaled) circle.recycle()
        file.absolutePath
    }.getOrNull()
}
