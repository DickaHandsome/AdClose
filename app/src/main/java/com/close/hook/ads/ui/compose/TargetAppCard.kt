package com.close.hook.ads.ui.compose

import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.close.hook.ads.data.model.AppInfo

@Composable
fun TargetAppCard(
    appInfo: AppInfo?,
    activePresetName: String?,
    showSwitchButton: Boolean = true,
    onSwitchClick: () -> Unit = {}
) {
    val context = LocalContext.current

    val gradient = Brush.linearGradient(WorkshopTheme.targetCardGradient())

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .padding(16.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val iconBitmap = remember(appInfo?.packageName) {
                    appInfo?.let { info ->
                        val drawable = runCatching {
                            context.packageManager.getApplicationIcon(info.packageName)
                        }.getOrNull() ?: return@remember null
                        (drawable as? BitmapDrawable)?.bitmap
                            ?: (drawable as? android.graphics.drawable.AdaptiveIconDrawable)
                                ?.let { adaptive ->
                                    val bmp = android.graphics.Bitmap.createBitmap(
                                        56, 56, android.graphics.Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = android.graphics.Canvas(bmp)
                                    adaptive.setBounds(0, 0, 56, 56)
                                    adaptive.draw(canvas)
                                    bmp
                                }
                    }
                }
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .shadow(8.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(WorkshopTheme.cardBg())
                ) {
                    iconBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = appInfo?.appName ?: "",
                            modifier = Modifier.size(56.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appInfo?.appName ?: "未选择应用",
                        color = WorkshopTheme.textPrimary(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (appInfo != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${appInfo.versionName}",
                            color = WorkshopTheme.textSecondary(),
                            fontSize = 12.sp
                        )
                        Text(
                            text = appInfo.packageName,
                            color = WorkshopTheme.textTertiary(),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (showSwitchButton) {
                    OutlinedButton(
                        onClick = onSwitchClick,
                        border = BorderStroke(1.dp, WorkshopTheme.accent()),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = WorkshopTheme.accent()
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("切换", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = activePresetName ?: "暂存区 · 0 条 Hook配置",
                color = WorkshopTheme.accent().copy(alpha = 0.6f),
                fontSize = 12.sp
            )
        }
    }
}