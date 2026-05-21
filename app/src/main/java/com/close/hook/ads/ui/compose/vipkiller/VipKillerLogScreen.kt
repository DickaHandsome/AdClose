package com.close.hook.ads.ui.compose.vipkiller

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.close.hook.ads.ui.compose.WorkshopTheme

/* ════════════════════════════════════════════
   通杀会员 · 诊断日志查看器
   AdClose VipKiller — Debug Log Viewer
   ════════════════════════════════════════════ */

private val levelColors = mapOf(
    VipKillerLogViewModel.LogLevel.VERBOSE to 0xFF9E9E9E,
    VipKillerLogViewModel.LogLevel.DEBUG to 0xFF42A5F5,
    VipKillerLogViewModel.LogLevel.INFO to 0xFF66BB6A,
    VipKillerLogViewModel.LogLevel.WARN to 0xFFFFA726,
    VipKillerLogViewModel.LogLevel.ERROR to 0xFFEF5350,
    VipKillerLogViewModel.LogLevel.FATAL to 0xFFD50000
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipKillerLogScreen(
    state: VipKillerLogViewModel.LogUiState,
    onBack: () -> Unit,
    onFilterLevel: (VipKillerLogViewModel.LogLevel?) -> Unit,
    onSearchQuery: (String) -> Unit,
    onToggleAutoScroll: () -> Unit,
    onToggleFilterExpanded: () -> Unit,
    onClearLogs: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onCopyAll: () -> Unit,
    onSelectEntry: (Int) -> Unit
) {
    val surface = WorkshopTheme.cardBg()
    val accent = WorkshopTheme.accent()
    val t1 = WorkshopTheme.textPrimary()
    val t2 = WorkshopTheme.textSecondary()
    val t3 = WorkshopTheme.textTertiary()
    val listState = rememberLazyListState()

    // 自动滚动到底部
    LaunchedEffect(state.logs.size, state.isAutoScroll) {
        if (state.isAutoScroll && state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("诊断日志", color = t1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.12f)) {
                            Text(
                                " ${state.logs.size} ",
                                color = accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Filled.ArrowBack, "返回", tint = t2)
                    }
                },
                actions = {
                    IconButton(onClick = onExport) {
                        Icon(Icons.Outlined.FileDownload, "导出日志", tint = t2, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onShare) {
                        Icon(Icons.Outlined.Share, "分享日志", tint = t2, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onCopyAll) {
                        Icon(Icons.Outlined.ContentCopy, "复制全部", tint = t2, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onClearLogs) {
                        Icon(Icons.Outlined.DeleteOutline, "清空", tint = t2, modifier = Modifier.size(20.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 搜索栏
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                placeholder = { Text("搜索日志…", fontSize = 13.sp, color = t3) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = t3, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQuery("") }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, "清除", tint = t3, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = t1),
colors = TextFieldDefaults.colors(
                                    focusedTextColor = t1,
                                    unfocusedTextColor = t1,
                                    focusedIndicatorColor = accent,
                                    unfocusedIndicatorColor = t3.copy(alpha = 0.2f),
                                    cursorColor = accent,
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // 过滤栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val levels = listOf(null, VipKillerLogViewModel.LogLevel.VERBOSE, VipKillerLogViewModel.LogLevel.DEBUG,
                    VipKillerLogViewModel.LogLevel.INFO, VipKillerLogViewModel.LogLevel.WARN, VipKillerLogViewModel.LogLevel.ERROR)
                val labels = listOf("全部", "V", "D", "I", "W", "E")
                levels.zip(labels).forEachIndexed { i, (level, label) ->
                    FilterChip(
                        selected = state.filterLevel == level,
                        onClick = { onFilterLevel(state.filterLevel?.let { if (it == level) null else level } ?: level) },
                        label = {
                            Text(
                                label,
                                fontSize = 11.sp,
                                color = if (state.filterLevel == level || (state.filterLevel == null && level == null))
                                    accent else t2
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.15f),
                            selectedLabelColor = accent
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggleAutoScroll, modifier = Modifier.size(28.dp)) {
                    Icon(
                        if (state.isAutoScroll) Icons.Outlined.VerticalAlignBottom else Icons.Outlined.PauseCircle,
                        contentDescription = if (state.isAutoScroll) "自动滚动" else "暂停滚动",
                        tint = if (state.isAutoScroll) accent else t3,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 日志列表
            val filteredLogs = state.filteredLogs
            if (filteredLogs.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Outlined.Inbox, null, tint = t3.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("暂无日志", color = t3, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    items(filteredLogs, key = { it.timestamp.toString() + it.message.take(8) }) { entry ->
                        val isSelected = state.selectedIndex >= 0 &&
                                filteredLogs.indexOf(entry) == state.selectedIndex
                        val levelColor = Color(levelColors[entry.level] ?: 0xFFAAAAAA)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val idx = filteredLogs.indexOf(entry)
                                    if (idx >= 0) onSelectEntry(idx)
                                },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) levelColor.copy(alpha = 0.08f) else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, levelColor.copy(alpha = 0.3f)) else null
                        ) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(levelColor)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "[${entry.level.label}]",
                                        color = levelColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        entry.tag,
                                        color = t3,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    entry.message,
                                    color = t1,
                                    fontSize = 13.sp,
                                    maxLines = if (isSelected) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}