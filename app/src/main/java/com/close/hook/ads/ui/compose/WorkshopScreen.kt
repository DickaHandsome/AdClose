package com.close.hook.ads.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkshopScreen(
    viewModel: WorkshopViewModel,
    onBackClick: () -> Unit = {},
    onNavigateToModule: (String) -> Unit = {}
) {
    val currentTarget by viewModel.currentTargetApp.collectAsState()
    val modules by viewModel.modulesWithStatus.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val activePresetId by viewModel.activePresetId.collectAsState()
    val activePresetName = presets.find { it.id == activePresetId }?.name

    val bgBrush = Brush.verticalGradient(WorkshopTheme.bgBrush())

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "\u25C6 逆向工坊",
                        color = WorkshopTheme.textPrimary(),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "返回",
                            tint = WorkshopTheme.textPrimary()
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item(key = "target_card") {
                    TargetAppCard(
                        appInfo = currentTarget,
                        activePresetName = activePresetName,
                        showSwitchButton = false,
                        onSwitchClick = {}
                    )
                }
                item(key = "tool_grid") {
                    ToolGrid(
                        modules = modules,
                        onModuleClick = { module ->
                            onNavigateToModule(module.id)
                        }
                    )
                }
                item(key = "preset_section") {
                    PresetSection(viewModel = viewModel)
                }
                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(52.dp))
                }
            }
        }
    }
}