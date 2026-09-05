package com.bdalxy.edencourt.feature.character

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bdalxy.edencourt.core.ui.loadArtworkBitmap
import com.bdalxy.edencourt.core.ui.saveAvatarFromCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * B3-3b：立绘头像框裁剪器。
 * 全屏对话框：立绘图初始 contain 铺满可视正方形区，用户单指拖动定位、双指捏合缩放，
 * 中央固定白色头像框（框外灰遮罩）。点「完成」把框内对应立绘区域裁切成头像（覆盖该卡头像），
 * 「取消」不改变头像（仅立绘背景仍已保存）。
 */
@Composable
internal fun ArtworkCropperDialog(
    artworkPath: String,
    onCancel: () -> Unit,
    onConfirm: (avatarPath: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // 异步加载立绘图（IO 采样压缩防内存）
    var image by remember(artworkPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(artworkPath) {
        image = withContext(Dispatchers.IO) { loadArtworkBitmap(artworkPath) }?.asImageBitmap()
    }
    // 手势状态：scale 缩放（1..5 倍），offset 图片中心相对可视区中心的位移（px）
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // 裁剪可视区边长（px，由 onSizeChanged 填充）
    var drawPx by remember { mutableStateOf(0f) }
    val state = rememberTransformableState { zoomChange, pan, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = offset + pan
    }
    // 完成态：防止重复裁剪
    var saving by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            Column(
                modifier = Modifier
                    .padding(bottom = 48.dp) // 底部留白，防止按钮被导航栏/手势条遮挡
            ) {
                // —— 顶部栏 ——
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White,
                        )
                    }
                    Text(
                        text = "拖动 / 捏合缩放，对齐头像框",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.weight(1f),
                    )
                    // P1-重置：一键把缩放/位移归位到初始 contain 居中态，便于重新对齐
                    IconButton(
                        onClick = {
                            scale = 1f
                            offset = Offset.Zero
                        },
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "重置",
                            tint = Color.White,
                        )
                    }
                }
                // —— 中部：居中正方形立绘裁剪区 ——
                // P0 修复：弃用 weight(1f)+aspectRatio(1f) 组合（会把裁剪区撑高、挤掉底部按钮、且圆心随高度偏移），
                // 改为 weight 占满剩余空间 + 内部取 min(宽,高) 的居中正方形，确保裁剪框真正居中、底部按钮始终可见。
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    // 可容纳的最大居中正方形边长（Dp），保证宽高一致、圆心 = 边长一半
                    val side = minOf(maxWidth, maxHeight)
                    val bmp = image
                    Box(
                        modifier = Modifier
                            .size(side)
                            .onSizeChanged { drawPx = it.width.toFloat() },
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .transformable(state),
                        ) {
                            bmp?.let { img ->
                                val bw = img.width.toFloat()
                                val bh = img.height.toFloat()
                                // 初始 contain：位图适配到可视区
                                val base = drawPx / maxOf(bw, bh)
                                val dstW = bw * base * scale
                                val dstH = bh * base * scale
                                val left = drawPx / 2f + offset.x - dstW / 2f
                                val top = drawPx / 2f + offset.y - dstH / 2f
                                drawImage(
                                    image = img,
                                    dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                                    dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt()),
                                )
                            }
                            // —— 圆形头像框 + 圆外灰遮罩（固定中央，不随图片变换）——
                            val radius = drawPx * 0.36f // 圆形框半径（直径约可视区 72%）
                            val cx = drawPx / 2f
                            val cy = drawPx / 2f
                            val overlay = Color.Black.copy(alpha = 0.55f)
                            // 路径：整个可视区减去中央圆，圆内为透明（保留立绘），圆外盖灰遮罩
                            val mask = Path().apply {
                                fillType = PathFillType.EvenOdd
                                addRect(Rect(0f, 0f, drawPx, drawPx))
                                addOval(Rect(cx - radius, cy - radius, cx + radius, cy + radius))
                            }
                            drawPath(path = mask, color = overlay)
                            // 白色圆环描边，标示头像裁剪边界
                            drawCircle(
                                color = Color.White,
                                radius = radius,
                                center = Offset(cx, cy),
                                style = Stroke(width = 3f),
                            )
                            // P1-辅助参考线：圆内十字（水平+垂直淡线），帮助把主体物对齐到中心
                            val guideColor = Color.White.copy(alpha = 0.35f)
                            // 水平参考线
                            drawLine(
                                color = guideColor,
                                start = Offset(cx - radius, cy),
                                end = Offset(cx + radius, cy),
                                strokeWidth = 1.5f,
                            )
                            // 垂直参考线
                            drawLine(
                                color = guideColor,
                                start = Offset(cx, cy - radius),
                                end = Offset(cx, cy + radius),
                                strokeWidth = 1.5f,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // —— 底部按钮 ——
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCancel, enabled = !saving) {
                        Text("取消", color = Color.White)
                    }
                    Spacer(modifier = Modifier.size(8.dp))
                    TextButton(
                        onClick = {
                            if (saving) return@TextButton
                            saving = true
                            scope.launch {
                                val path = saveAvatarFromCrop(
                                    context = context,
                                    artworkPath = artworkPath,
                                    scale = scale,
                                    offsetPx = offset,
                                    drawSizePx = drawPx,
                                )
                                if (path != null) {
                                    onConfirm(path)
                                } else {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving && image != null,
                    ) {
                        Text(if (saving) "裁剪中…" else "完成", color = Color.White)
                    }
                }
            }
        }
    }
}