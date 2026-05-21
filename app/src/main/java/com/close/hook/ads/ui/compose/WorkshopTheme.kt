package com.close.hook.ads.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * 逆向工坊 / 通杀会员 等 Compose 页面统一主题。
 *
 * 所有颜色均委托给 MaterialTheme.colorScheme，
 * 由宿主 Activity 通过 dynamicLightColorScheme / dynamicDarkColorScheme 注入，
 * 自动跟随系统 Material You 动态颜色（与 XML 主题一致）。
 */
object WorkshopTheme {
    // ---- 从 MaterialTheme 读取（跟随 Activity 主题） ----

    @Composable fun bgBrush() = listOf(
        MaterialTheme.colorScheme.background,
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.background
    )
    @Composable fun cardBg() = MaterialTheme.colorScheme.surface
    @Composable fun cardGradient() = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface
    )
    @Composable fun textPrimary() = MaterialTheme.colorScheme.onSurface
    @Composable fun textSecondary() = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun textTertiary() = MaterialTheme.colorScheme.outline
    @Composable fun borderColor() = MaterialTheme.colorScheme.outlineVariant
    @Composable fun presetBg() = MaterialTheme.colorScheme.surfaceVariant
    @Composable fun presetBorderInactive() = MaterialTheme.colorScheme.outlineVariant
    @Composable fun logBg() = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    @Composable fun logBgExpanded() = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    @Composable fun targetCardGradient() = listOf(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.surface
    )
    @Composable fun toolCardBorder(active: Boolean, accent: Color) =
        if (active) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant

    @Composable fun accent() = MaterialTheme.colorScheme.primary
    @Composable fun gray() = MaterialTheme.colorScheme.onSurfaceVariant
    @Composable fun iconInactive() = MaterialTheme.colorScheme.outline
    @Composable fun statusInactive() = MaterialTheme.colorScheme.outline
}