package com.close.hook.ads.ui.compose

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ToolGrid(
    modules: List<ModuleDef>,
    onModuleClick: (ModuleDef) -> Unit
) {
    Column {
        Text(
            text = "工具集",
            color = WorkshopTheme.textPrimary(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            itemsIndexed(modules) { index, module ->
                val icon = getModuleIcon(module.id)
                val accent = getModuleAccent(module.id)

                // 交错入场动画
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(index * 60L)
                    visible = true
                }
                val entranceAlpha by animateFloatAsState(
                    targetValue = if (visible) 1f else 0f,
                    animationSpec = tween(400, delayMillis = index * 60)
                )
                val entranceOffset by animateFloatAsState(
                    targetValue = if (visible) 0f else 20f,
                    animationSpec = tween(400, delayMillis = index * 60)
                )

                Box(
                    modifier = Modifier
                        .alpha(entranceAlpha)
                        .offset(y = entranceOffset.dp)
                ) {
                    ModernToolCard(
                        module = module,
                        icon = icon,
                        accentColor = accent,
                        onClick = { onModuleClick(module) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernToolCard(
    module: ModuleDef,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit
) {
    // 活跃状态呼吸动画
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "glowAlpha"
    )

    var pressed by remember { mutableFloatStateOf(1f) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .scale(pressed)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(WorkshopTheme.cardGradient())
            )
            .border(0.5.dp, WorkshopTheme.toolCardBorder(module.indicatorActive, accentColor), RoundedCornerShape(18.dp))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                pressed = 0.92f
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 14.dp)
    ) {
        // 图标 + 发光底座
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(if (module.indicatorActive) pulseScale else 1f)
                .shadow(
                    elevation = if (module.indicatorActive) 6.dp else 0.dp,
                    shape = CircleShape,
                    ambientColor = accentColor,
                    spotColor = accentColor
                )
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.25f),
                            accentColor.copy(alpha = 0.05f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // 发光光晕
            if (module.indicatorActive) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            Icon(
                imageVector = icon,
                contentDescription = module.name,
                tint = if (module.indicatorActive) accentColor else WorkshopTheme.iconInactive(),
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        // 名称
        Text(
            text = module.name,
            color = WorkshopTheme.textPrimary(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(4.dp))

        // 状态文字
        Text(
            text = module.statusText,
            color = if (module.indicatorActive) accentColor.copy(alpha = 0.8f) else WorkshopTheme.statusInactive(),
            fontSize = 9.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun getModuleAccent(moduleId: String): Color = when (moduleId) {
    "vip_killer" -> MaterialTheme.colorScheme.tertiary
    "hook" -> MaterialTheme.colorScheme.primary
    "dexdump" -> MaterialTheme.colorScheme.secondary
    "data" -> MaterialTheme.colorScheme.primaryContainer
    "block" -> MaterialTheme.colorScheme.error
    "settings" -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.outline
}

@Composable
fun getModuleIcon(moduleId: String): ImageVector = when (moduleId) {
    "vip_killer" -> Icons.Filled.Star
    "hook" -> Icons.Filled.Build
    "dexdump" -> Icons.Filled.Save
    "data" -> Icons.Filled.Edit
    "block" -> Icons.Filled.Close
    "settings" -> Icons.Filled.Settings
    else -> Icons.Filled.Star
}