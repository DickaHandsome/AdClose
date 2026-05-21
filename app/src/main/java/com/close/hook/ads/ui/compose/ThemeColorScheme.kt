package com.close.hook.ads.ui.compose

import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 从 Context 的 XML 主题读取已解析颜色（含用户选择的 Material 颜色叠加 + 暗黑模式），
 * 覆盖 dynamicColorScheme 构建完整 Compose ColorScheme，确保所有页面跟随设置页主题。
 */
fun resolveThemeColorScheme(context: Context): ColorScheme {
    val isDark = (context.resources.configuration.uiMode
        and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    val base = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    val primary = resolveAttr(context, android.R.attr.colorPrimary)
    val background = resolveAttr(context, android.R.attr.colorBackground)
    val foreground = resolveAttr(context, android.R.attr.colorForeground)

    return base.copy(
        primary = primary ?: base.primary,
        onPrimary = if (primary != null) deriveOnPrimary(primary, isDark) else base.onPrimary,
        background = background ?: base.background,
        onBackground = foreground ?: base.onBackground,
        surface = background ?: base.surface,
        onSurface = foreground ?: base.onSurface
    )
}

private fun resolveAttr(context: Context, attrResId: Int): Color? {
    val tv = TypedValue()
    return if (context.theme.resolveAttribute(attrResId, tv, true)) Color(tv.data) else null
}

private fun deriveOnPrimary(primary: Color, isDark: Boolean): Color {
    val luminance = 0.299 * primary.red + 0.587 * primary.green + 0.114 * primary.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}