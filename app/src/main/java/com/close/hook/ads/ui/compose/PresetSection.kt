package com.close.hook.ads.ui.compose

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PresetSection(viewModel: WorkshopViewModel) {
    val presets by viewModel.presets.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()
    val context = LocalContext.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("方案", color = WorkshopTheme.textPrimary(), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = {
                Toast.makeText(context, "保存当前配置", Toast.LENGTH_SHORT).show()
            }) {
                Text("+ 保存当前配置", color = WorkshopTheme.accent(), fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(presets, key = { it.id }) { preset ->
                val isActive = preset.id == activePresetId
                val isNewPlaceholder = preset.name.contains("新建")
                val dashColor = WorkshopTheme.textTertiary()
                val borderColor by animateColorAsState(
                    targetValue = if (isActive) WorkshopTheme.accent() else WorkshopTheme.presetBorderInactive(),
                    label = "presetBorder"
                )
                val bgColor by animateColorAsState(
                    targetValue = if (isActive) WorkshopTheme.accent().copy(alpha = 0.08f) else WorkshopTheme.presetBg(),
                    label = "presetBg"
                )
                var expanded by remember { mutableStateOf(false) }

                Box {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .then(
                    if (isNewPlaceholder) {
                                        Modifier.drawBehind {
                                            drawRoundRect(
                                                color = dashColor,
                                                style = Stroke(
                                                    width = 1.dp.value * density,
                                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 4f))
                                                ),
                                                cornerRadius = CornerRadius(20.dp.value * density)
                                            )
                                        }
                                    } else Modifier
                            )
                            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
                            .background(bgColor)
                            .pointerInput(preset.id) {
                                detectTapGestures(
                                    onTap = { if (!isNewPlaceholder) viewModel.applyPreset(preset.id) },
                                    onLongPress = { if (!isNewPlaceholder) expanded = true }
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = preset.name,
                            color = if (isActive) WorkshopTheme.accent() else WorkshopTheme.textSecondary(),
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            onClick = {
                                expanded = false
                                viewModel.renamePreset(preset.id, "${preset.name}_改")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { expanded = false; viewModel.deletePreset(preset.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        val activeName = presets.find { it.id == activePresetId }?.name
        Text(
            text = if (activeName != null) "当前激活：$activeName" else "未激活",
            color = WorkshopTheme.textSecondary(),
            fontSize = 12.sp
        )
    }
}