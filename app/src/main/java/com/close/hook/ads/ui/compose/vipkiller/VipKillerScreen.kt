package com.close.hook.ads.ui.compose.vipkiller

import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import com.close.hook.ads.data.model.AppInfo
import com.close.hook.ads.data.model.HookField
import com.close.hook.ads.data.model.HookMethodType
import com.close.hook.ads.hook.ha.VipKillerEngine
import com.close.hook.ads.ui.compose.TargetAppCard
import com.close.hook.ads.ui.compose.WorkshopTheme

/* ════════════════════════════════════════════
   通杀会员 · 发行版 UI
   AdClose VipKiller — Production UI
   ════════════════════════════════════════════ */

/* ──── 主题快捷访问 ──── */
private object T {
    @Composable fun accent() = WorkshopTheme.accent()
    @Composable fun surface() = WorkshopTheme.cardBg()
    @Composable fun t1() = WorkshopTheme.textPrimary()
    @Composable fun t2() = WorkshopTheme.textSecondary()
    @Composable fun t3() = WorkshopTheme.textTertiary()
    @Composable fun border() = WorkshopTheme.borderColor()
    @Composable fun bg() = Brush.verticalGradient(WorkshopTheme.bgBrush())
}


/* ──── Hook 类型：枚举名 / legacy 值 → 显示名 ──── */
private fun hookTypeLabel(type: String): String {
    val mt = HookMethodType.entries.find { it.name == type }
    return mt?.displayName ?: when (type.lowercase()) {
        "replace" -> "替换方法体"
        "before" -> "前插"
        "after" -> "后插"
        "hook_all_methods" -> "hookAllMethods"
        else -> type
    }
}

/** 将 PendingHook 中存储的 hookMethodTypeName / hookType 解析为 HookMethodType 枚举名 */
private fun resolveHookTypeName(hook: VipKillerEngine.PendingHook): String {
    val raw = hook.hookMethodTypeName.ifBlank { hook.hookType }
    // 优先枚举精确匹配
    if (HookMethodType.entries.any { it.name == raw }) return raw
    // legacy 值映射
    return when (raw.lowercase()) {
        "replace" -> HookMethodType.HOOK_ALL_METHODS.name
        "before", "after" -> HookMethodType.HOOK_ALL_METHODS.name
        else -> HookMethodType.HOOK_ALL_METHODS.name
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VipKillerScreen(
    state: VipKillerViewModel.VipKillerState,
    onScanStart: () -> Unit,
    onHookSingle: (VipKillerEngine.VipCandidate) -> Unit,
    onHookAll: () -> Unit,
    onToggleL2: (Boolean) -> Unit,
    onToggleL3: (Boolean) -> Unit,
    onToggleL4: (Boolean) -> Unit, onToggleL5: (Boolean) -> Unit,
    onToggleFieldScan: (Boolean) -> Unit,
    onSharedToggle: (Boolean) -> Unit,
    onAppendInput: (String) -> Unit,
    onShowLogs: () -> Unit = {},
    onExportConfig: () -> Unit = {},
    onImportConfig: () -> Unit = {},
    onImportFromClipboard: () -> Unit = {},
    onRequestScope: () -> Unit,
    onBack: () -> Unit,
    pendingHooks: List<VipKillerEngine.PendingHook> = emptyList(),
    onLoadPending: () -> Unit = {},
    onClearPending: () -> Unit = {},
    onClearDisabledPending: () -> Unit = {},
    onSavePending: (VipKillerEngine.PendingHook) -> Unit = {},
    onRemovePending: (VipKillerEngine.PendingHook) -> Unit = {},
    onCreateHook: () -> Unit = {},
    onSearchMethods: (String) -> List<VipKillerEngine.MethodSignature> = { emptyList() },
    onTogglePending: (VipKillerEngine.PendingHook, Boolean) -> Unit = { _, _ -> },
    onCandidateToPending: (VipKillerEngine.VipCandidate) -> Unit = {},

) {
    val accent = T.accent()
    val surface = T.surface()
    val t1 = T.t1(); val t2 = T.t2(); val t3 = T.t3(); val bd = T.border()

    var inputText by remember { mutableStateOf(state.appendInput) }
    var showClearDialog by remember { mutableStateOf(false) }
    var isFabExpanded by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 从包名构建 AppInfo（供 TargetAppCard 使用）
    val targetAppInfo = remember(state.currentApp) {
        if (state.currentApp.isNotEmpty()) {
            try {
                val pm = context.packageManager
                val ai = pm.getApplicationInfo(state.currentApp, 0)
                val pi = pm.getPackageInfo(state.currentApp, 0)
                AppInfo(
                    appName = pm.getApplicationLabel(ai).toString(),
                    packageName = state.currentApp,
                    versionName = pi.versionName ?: "",
                    versionCode = pi.longVersionCode.toInt(),
                    firstInstallTime = pi.firstInstallTime,
                    lastUpdateTime = pi.lastUpdateTime,
                    size = 0L,
                    targetSdk = ai.targetSdkVersion,
                    minSdk = 0,
                    isAppEnable = 1,
                    isEnable = 1,
                    isSystem = false
                )
            } catch (_: Exception) { null }
        } else null
    }

    // 当前编辑的暂存（null=新建）
    var editingHook by remember { mutableStateOf<VipKillerEngine.PendingHook?>(null) }
    // 编辑状态（从 editingHook 恢复，或新建空值）
    var editClass by remember { mutableStateOf("") }
    var editMethod by remember { mutableStateOf("") }
    var editReturn by remember { mutableStateOf("boolean") }
    var editParams by remember { mutableStateOf("0") }
    var editHookType by remember { mutableStateOf(HookMethodType.HOOK_ALL_METHODS.name) }
    var editRetVal by remember { mutableStateOf("true") }
    var editDesc by remember { mutableStateOf("") }
    var editFieldName by remember { mutableStateOf("") }
    var editFieldValue by remember { mutableStateOf("") }
    var editSearchStrings by remember { mutableStateOf("") }
    var editParamTypes by remember { mutableStateOf("") }
    var editHookPoint by remember { mutableStateOf("before") }
    var editSearchResults by remember { mutableStateOf<List<VipKillerEngine.MethodSignature>>(emptyList()) }
    var showEditSearchDialog by remember { mutableStateOf(false) }
    var showDeleteInDialog by remember { mutableStateOf(false) }

    // 当前选中类型的可见字段
    val selectedType = remember(editHookType) { HookMethodType.entries.find { it.name == editHookType } ?: HookMethodType.HOOK_ALL_METHODS }
    val visibleFields = selectedType.visibleFields

    // 从已有暂存填充编辑状态
    fun loadForEdit(hook: VipKillerEngine.PendingHook) {
        editClass = hook.className; editMethod = hook.methodName
        editReturn = hook.returnType; editParams = hook.paramsCount.toString()
        editHookType = resolveHookTypeName(hook); editRetVal = hook.replacementValue
        editDesc = hook.description
        editFieldName = hook.fieldName; editFieldValue = hook.fieldValue
        editSearchStrings = hook.searchStrings; editParamTypes = hook.paramTypes
        editHookPoint = hook.hookPoint.ifBlank { "before" }
    }
    fun openNewDialog() { editingHook = null; showCreateDialog = true }
    fun openEditDialog(hook: VipKillerEngine.PendingHook) { editingHook = hook; loadForEdit(hook); showCreateDialog = true }

    // 暂存编辑/新建对话框
    if (showCreateDialog) {
        val isEdit = editingHook != null
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; editingHook = null },
            containerColor = surface,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (isEdit) Icons.Outlined.Edit else Icons.Outlined.AddCircleOutline,
                        null, tint = accent, modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isEdit) "编辑暂存 Hook" else "新建暂存 Hook",
                        color = t1, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 480.dp)) {
                    // 类名 — CLASS_NAME 在 visibleFields 时显示
                    if (HookField.CLASS_NAME in visibleFields) {
                        OutlinedTextField(
                            value = editClass, onValueChange = { editClass = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("完整类名", fontSize = 11.sp) },
                            placeholder = { Text("com.example.MyClass", fontSize = 12.sp, color = t3) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                            colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                        )
                    }
                    // 方法名 + 查询 — METHOD_NAME 在 visibleFields 时显示
                    if (HookField.METHOD_NAME in visibleFields) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editMethod, onValueChange = { editMethod = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("方法名", fontSize = 11.sp) },
                                placeholder = { Text("isVip", fontSize = 12.sp, color = t3) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                                colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                            )
                            IconButton(
                                onClick = {
                                    if (editMethod.isNotBlank()) {
                                        editSearchResults = onSearchMethods(editMethod.trim())
                                        showEditSearchDialog = true
                                    }
                                },
                                enabled = editMethod.isNotBlank(),
                                modifier = Modifier.size(40.dp)
                            ) {
                                @Suppress("DEPRECATION")
                                Icon(Icons.Outlined.ManageSearch, "DexKit查询", tint = if (editMethod.isNotBlank()) accent else t3, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    // 字段名 — FIELD_NAME 在 visibleFields 时显示
                    if (HookField.FIELD_NAME in visibleFields) {
                        OutlinedTextField(
                            value = editFieldName, onValueChange = { editFieldName = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("字段名", fontSize = 11.sp) },
                            placeholder = { Text("isVip", fontSize = 12.sp, color = t3) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                            colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                        )
                    }
                    // 搜索字符串 — SEARCH_STRINGS 在 visibleFields 时显示
                    if (HookField.SEARCH_STRINGS in visibleFields) {
                        OutlinedTextField(
                            value = editSearchStrings, onValueChange = { editSearchStrings = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("搜索字符串", fontSize = 11.sp) },
                            placeholder = { Text("vip, premium, is_vip", fontSize = 12.sp, color = t3) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                            colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                        )
                    }
                    // 参数类型 — PARAMETER_TYPES 在 visibleFields 时显示
                    if (HookField.PARAMETER_TYPES in visibleFields) {
                        OutlinedTextField(
                            value = editParamTypes, onValueChange = { editParamTypes = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("参数类型", fontSize = 11.sp) },
                            placeholder = { Text("int, boolean, String", fontSize = 12.sp, color = t3) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                            colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                        )
                    }
                    // 返回类型 + 参数数
                    if (HookField.RETURN_VALUE in visibleFields) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editReturn, onValueChange = { editReturn = it },
                                modifier = Modifier.weight(1f),
                                label = { Text("返回类型", fontSize = 11.sp) },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                                colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                            )
                            OutlinedTextField(
                                value = editParams,
                                onValueChange = { v -> editParams = v.filter { it.isDigit() } },
                                modifier = Modifier.width(70.dp),
                                label = { Text("参数数", fontSize = 11.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                                colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }
                    // 字段值
                    if (HookField.FIELD_VALUE in visibleFields) {
                        OutlinedTextField(
                            value = editFieldValue, onValueChange = { editFieldValue = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("字段值", fontSize = 11.sp) },
                            placeholder = { Text("true", fontSize = 12.sp, color = t3) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                            colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                        )
                    }
                    // Hook 类型下拉 — 独占一行
                    var hookTypeExpanded by remember { mutableStateOf(false) }
                    val allTypes = HookMethodType.entries
                    ExposedDropdownMenuBox(expanded = hookTypeExpanded, onExpandedChange = { hookTypeExpanded = it }) {
                        OutlinedTextField(
                            value = hookTypeLabel(editHookType), onValueChange = {}, readOnly = true,
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true).fillMaxWidth(),
                            label = { Text("Hook类型", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = accent),
                            colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hookTypeExpanded) }
                        )
                        ExposedDropdownMenu(expanded = hookTypeExpanded, onDismissRequest = { hookTypeExpanded = false }) {
                            allTypes.forEach { mt ->
                                DropdownMenuItem(
                                    text = { Text(mt.displayName, fontSize = 13.sp) },
                                    onClick = { editHookType = mt.name; hookTypeExpanded = false },
                                    leadingIcon = {
                                        Box(Modifier.size(8.dp).clip(CircleShape).background(
                                            when (mt.name) {
                                                "before" -> MaterialTheme.colorScheme.secondary
                                                "after" -> MaterialTheme.colorScheme.tertiary
                                                else -> accent
                                            }
                                        ))
                                    }
                                )
                            }
                        }
                    }
                    // 替换值 — 独占一行，仅当可见时
                    if (HookField.RETURN_VALUE in visibleFields || HookField.FIELD_VALUE in visibleFields) {
                        OutlinedTextField(
                            value = editRetVal, onValueChange = { editRetVal = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("替换值", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = t1),
                            colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                        )
                    }
                    // Hook 时机 — HOOK_POINT 在 visibleFields 时显示
                    if (HookField.HOOK_POINT in visibleFields) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = editHookPoint == "before",
                                onClick = { editHookPoint = "before" },
                                label = { Text("前插", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f))
                            )
                            FilterChip(
                                selected = editHookPoint == "after",
                                onClick = { editHookPoint = "after" },
                                label = { Text("后插", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                            )
                        }
                    }
                    // 描述
                    OutlinedTextField(
                        value = editDesc, onValueChange = { editDesc = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("备注 (可选)", fontSize = 11.sp) },
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = t1),
                        colors = miniFieldColors(accent), shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 必填字段校验（对齐 hook 管理）
                        val type = selectedType
                        val missing = type.requiredFields.filter { field ->
                            when (field) {
                                HookField.CLASS_NAME -> editClass.isBlank()
                                HookField.METHOD_NAME -> editMethod.isBlank()
                                HookField.PARAMETER_TYPES -> editParamTypes.isBlank()
                                HookField.FIELD_NAME -> editFieldName.isBlank()
                                HookField.SEARCH_STRINGS -> editSearchStrings.isBlank()
                                HookField.RETURN_VALUE -> false
                                HookField.FIELD_VALUE -> false
                                HookField.HOOK_POINT -> false
                                HookField.PARAMETER_REPLACEMENTS -> false
                            }
                        }
                        if (missing.isNotEmpty()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("请填写必填字段：${missing.joinToString { it.name }}")
                            }
                            return@Button
                        }
                        onSavePending(VipKillerEngine.PendingHook(
                            className = editClass.trim(),
                            methodName = editMethod.trim(),
                            returnType = editReturn.trim().ifBlank { "boolean" },
                            paramsCount = editParams.toIntOrNull() ?: 0,
                            hookType = editHookType.trim(),
                            enabled = editingHook?.enabled ?: true,
                            replacementValue = editRetVal.trim().ifBlank { "true" },
                            description = editDesc.trim(),
                            hookMethodTypeName = editHookType.trim(),
                            fieldName = editFieldName.trim(),
                            fieldValue = editFieldValue.trim(),
                            searchStrings = editSearchStrings.trim(),
                            paramTypes = editParamTypes.trim(),
                            hookPoint = editHookPoint
                        ))
                        editClass = ""; editMethod = ""; editReturn = "boolean"
                        editParams = "0"; editHookType = HookMethodType.HOOK_ALL_METHODS.name
                        editRetVal = "true"; editDesc = ""; editFieldName = ""; editFieldValue = ""
                        editSearchStrings = ""; editParamTypes = ""; editHookPoint = "before"
                        showCreateDialog = false; editingHook = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(10.dp),
                    enabled = editMethod.isNotBlank() || editClass.isNotBlank()
                ) { Text("保存暂存", color = Color.White, fontSize = 14.sp) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (isEdit) {
                        OutlinedButton(
                            onClick = { showDeleteInDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) { Icon(Icons.Outlined.DeleteOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }
                    }
                    OutlinedButton(
                        onClick = {
                            editClass = ""; editMethod = ""; editReturn = "boolean"
                            editParams = "0"; editHookType = HookMethodType.HOOK_ALL_METHODS.name
                            editRetVal = "true"; editDesc = ""; editFieldName = ""; editFieldValue = ""
                            editSearchStrings = ""; editParamTypes = ""; editHookPoint = "before"
                            showCreateDialog = false; editingHook = null
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("取消", color = t2) }
                }
            }
        )
    }

    // 对话框内删除确认
    if (showDeleteInDialog && editingHook != null) {
        AlertDialog(
            onDismissRequest = { showDeleteInDialog = false },
            containerColor = surface,
            shape = RoundedCornerShape(16.dp),
            title = { Text("删除此暂存 Hook？", color = t1, fontWeight = FontWeight.SemiBold) },
            text = { Text("「${editingHook!!.methodName.ifEmpty { "(空)" }}」将被永久移除，不可撤销。", color = t2, fontSize = 13.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        onRemovePending(editingHook!!)
                        showDeleteInDialog = false
                        showCreateDialog = false
                        editingHook = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("确认删除", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteInDialog = false }, shape = RoundedCornerShape(10.dp)) {
                    Text("取消", color = t2)
                }
            }
        )
    }

    // 对话框内的 DexKit 搜索结果弹窗
    if (showEditSearchDialog && editSearchResults.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showEditSearchDialog = false },
            containerColor = surface,
            title = { Text("查询结果 (${editSearchResults.size})", color = t1, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                    itemsIndexed(editSearchResults) { _, sig ->
                        Surface(
                            Modifier.fillMaxWidth().clickable {
                                editClass = sig.className; editMethod = sig.methodName
                                editReturn = sig.returnType; editParams = sig.paramsCount.toString()
                                editRetVal = when {
                                    sig.returnType.lowercase().contains("boolean") -> "true"
                                    sig.returnType.lowercase().contains("long") -> "3495751810"
                                    else -> editRetVal
                                }
                                showEditSearchDialog = false
                            }.padding(vertical = 10.dp, horizontal = 8.dp),
                            shape = RoundedCornerShape(8.dp), color = Color.Transparent
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Code, null, tint = accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(sig.methodName, color = t1, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${sig.className.substringAfterLast('.')} → ${sig.returnType} (${sig.paramsCount}参)",
                                        color = t3, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showEditSearchDialog = false }) { Text("关闭", color = accent) } }
        )
    }
    if (showEditSearchDialog && editSearchResults.isEmpty()) {
        AlertDialog(
            onDismissRequest = { showEditSearchDialog = false },
            containerColor = surface,
            title = { Text("无匹配结果", color = t1) },
            text = { Text("DexKit 未找到包含「${editMethod}」的方法", color = t2) },
            confirmButton = { TextButton(onClick = { showEditSearchDialog = false }) { Text("关闭", color = accent) } }
        )
    }

    // 清空确认弹窗 — 支持选择「删除全部」或「仅删未勾选」
    if (showClearDialog) {
        val disabledCount = pendingHooks.count { !it.enabled }
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = surface, shape = RoundedCornerShape(16.dp),
            title = { Text("删除暂存 Hook", color = t1, fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text("共 ${pendingHooks.size} 条暂存，其中 ${disabledCount} 条未勾选", color = t2, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("请选择删除范围：", color = t3, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // 删除全部
                    Button(
                        onClick = { onClearPending(); showClearDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.DeleteForever, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("删除全部 (${pendingHooks.size})", color = Color.White, fontSize = 14.sp)
                    }
                    // 仅删未勾选
                    if (disabledCount > 0) {
                        OutlinedButton(
                            onClick = { onClearDisabledPending(); showClearDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Icon(Icons.Outlined.Deselect, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(6.dp))
                            Text("仅删未勾选 ($disabledCount)", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                        }
                    }
                    // 取消
                    TextButton(
                        onClick = { showClearDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消", color = t2, fontSize = 14.sp)
                    }
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(accent), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Diamond, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text("通杀会员", color = t1, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, "返回", tint = t2) } },
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "更多选项", tint = t2, modifier = Modifier.size(22.dp))
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = surface
                        ) {
                            DropdownMenuItem(
                                text = { Text("诊断日志", fontSize = 14.sp, color = t1) },
                                onClick = { menuExpanded = false; onShowLogs() },
                                leadingIcon = { Icon(Icons.Outlined.BugReport, null, tint = t2, modifier = Modifier.size(20.dp)) }
                            )
if (state.currentApp.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("请求作用域", fontSize = 14.sp, color = t1) },
                                    onClick = { menuExpanded = false; onRequestScope() },
                                    leadingIcon = { Icon(Icons.Outlined.Shield, null, tint = t2, modifier = Modifier.size(20.dp)) }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("导出配置", fontSize = 14.sp, color = t1) },
                                onClick = { menuExpanded = false; onExportConfig() },
                                leadingIcon = { Icon(Icons.Outlined.FileDownload, null, tint = t2, modifier = Modifier.size(20.dp)) }
                            )
                            DropdownMenuItem(
                                text = { Text("导入配置", fontSize = 14.sp, color = t1) },
                                onClick = { menuExpanded = false; onImportConfig() },
                                leadingIcon = { Icon(Icons.Outlined.FileUpload, null, tint = t2, modifier = Modifier.size(20.dp)) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = Color.Transparent,
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedVisibility(
                    visible = isFabExpanded,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // 新建暂存配置
                        ExtendedFloatingActionButton(
                            text = { Text("新建暂存配置", fontSize = 13.sp) },
                            icon = { Icon(Icons.Outlined.AddCircleOutline, null) },
                            onClick = { showCreateDialog = true; isFabExpanded = false },
                            containerColor = surface,
                            contentColor = accent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        // 从剪贴板导入
                        ExtendedFloatingActionButton(
                            text = { Text("从剪贴板导入", fontSize = 13.sp) },
                            icon = { Icon(Icons.Outlined.ContentPaste, null) },
                            onClick = { onImportFromClipboard(); isFabExpanded = false },
                            containerColor = surface,
                            contentColor = accent,
                            shape = RoundedCornerShape(12.dp)
                        )
                        // 清空全部
                        if (pendingHooks.isNotEmpty()) {
                            ExtendedFloatingActionButton(
                                text = { Text("清空全部 (${pendingHooks.size})", fontSize = 13.sp) },
                                icon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                onClick = { showClearDialog = true; isFabExpanded = false },
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = Color.White,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
                // 主 FAB 按钮
                FloatingActionButton(
                    onClick = { isFabExpanded = !isFabExpanded },
                    containerColor = accent,
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        if (isFabExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isFabExpanded) "收起" else "新建",
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().background(T.bg()).padding(padding)) {
            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 110.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
/* ────── ① 目标应用卡片 ────── */
                item {
                    TargetAppCard(
                        appInfo = targetAppInfo,
                        activePresetName = "暂存区 · ${pendingHooks.size} 条 Hook配置",
                        showSwitchButton = false
                    )
                }

                /* ────── ② 操作按钮组 ────── */
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val layerCount = listOf(state.enableL2, state.enableL3, state.enableL4, state.enableL5, state.enableFieldScan).count { it }
                                if (layerCount == 0 && state.appendInput.isBlank()) {
                                    scope.launch { snackbarHostState.showSnackbar("请先勾选至少一个扫描策略或输入关键词", duration = SnackbarDuration.Short) }
                                } else {
                                    onScanStart()
                                }
                            }, modifier = Modifier.weight(1f).height(52.dp),
                            enabled = !state.isScanning,
                            colors = ButtonDefaults.buttonColors(containerColor = accent),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            if (state.isScanning) {
                                CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.5.dp)
                                Spacer(Modifier.width(8.dp)); Text("扫描中…", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            } else {
                                Icon(Icons.Outlined.Radar, null, Modifier.size(22.dp))
                                Spacer(Modifier.width(6.dp)); Text("开始扫描", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        OutlinedButton(
                            onClick = onHookAll, modifier = Modifier.weight(1f).height(52.dp),
                            enabled = state.candidates.isNotEmpty() && state.hookedCount < state.totalCount,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accent),
                            border = BorderStroke(1.5.dp, accent.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.AutoFixHigh, null, Modifier.size(21.dp))
                            Spacer(Modifier.width(6.dp)); Text("一键 Hook", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                /* ────── ③ 扫描进度 ────── */
                if (state.isScanning) {
                    item {
                        LinearProgressIndicator(
                            progress = { state.scanProgress },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color = accent, trackColor = accent.copy(alpha = 0.08f)
                        )
                    }
                }

                /* ────── ④ 关键词搜索 ────── */
                item {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { newVal -> inputText = newVal; onAppendInput(newVal) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("追加关键词，逗号分隔", color = t3, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = t3, modifier = Modifier.size(19.dp)) },
                        trailingIcon = {
                            if (inputText.isNotEmpty())
                                IconButton(onClick = { inputText = ""; onAppendInput("") }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.Close, "清除", tint = t3, modifier = Modifier.size(16.dp))
                                }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = t1, unfocusedTextColor = t1,
                            focusedIndicatorColor = accent,
                            unfocusedIndicatorColor = t3.copy(alpha = 0.2f),
                            cursorColor = accent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(13.dp)
                    )
                }

                /* ────── ⑤ 扫描策略 ────── */
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Tune, null, tint = accent, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("扫描策略", color = t1, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            val activeCount = listOf(state.enableL2, state.enableL3, state.enableL4, state.enableL5, state.enableFieldScan).count { it }
                            Spacer(Modifier.width(8.dp))
                            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.12f)) {
                                Text("${activeCount}项已选", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = accent, fontSize = 10.sp)
                            }
                        }
                        HorizontalDivider(color = accent, thickness = 1.dp)
                        Spacer(Modifier.height(8.dp))
                        StrategyChip("L2  字符串引用反查", "方法体内VIP字符串 → DexKit反查", state.enableL2, onToggleL2, accent, t1, t3)
                        StrategyChip("L3  方法名模式匹配", "isVip / getVip / isPremium 等语义匹配", state.enableL3, onToggleL3, accent, t1, t3)
                        StrategyChip("L4  社区共享规则", "云端贡献的已验证 Hook 方案", state.enableL4, onToggleL4, accent, t1, t3)
                        StrategyChip("L5  注解字段反查", "JSON / ORM 序列化字段 → 反推 getter", state.enableL5, onToggleL5, accent, t1, t3)
                        StrategyChip("L6  字段名过滤", "全量字段 → VIP关键词 → 推导 getter", state.enableFieldScan, onToggleFieldScan, accent, t1, t3)
                    }
                }

            /* ────── ⑥ 暂存 Hook ────── */
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // 标题栏
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.BookmarkBorder, null, tint = accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("暂存 Hook", color = t1, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                if (pendingHooks.isNotEmpty()) {
                                    Spacer(Modifier.width(8.dp))
                                    Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.12f)) {
                                        Text("${pendingHooks.size}条", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                        }
                        Spacer(Modifier.weight(1f))
                        // 右侧操作
                        if (pendingHooks.isNotEmpty()) {
                            val allEnabled = pendingHooks.all { it.enabled }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    onClick = { pendingHooks.forEach { onTogglePending(it, !allEnabled) } },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (allEnabled) accent.copy(alpha = 0.12f) else t3.copy(alpha = 0.08f)
                                ) {
                                    Text(
                                        if (allEnabled) "ON" else "OFF",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        color = if (allEnabled) accent else t3,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Surface(
                                    onClick = { showClearDialog = true },
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "清空",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(4.dp).size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                    }

                        if (pendingHooks.isEmpty()) {
                            // 空状态
                            Surface(
                                Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = surface,
                                border = BorderStroke(0.5.dp, bd)
                            ) {
                                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Outlined.BookmarkBorder, null, tint = t3, modifier = Modifier.size(36.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text("暂无暂存 Hook", color = t1, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.height(4.dp))
                                    Text("点击右下角 + 新建，或从扫描结果中加入", color = t3, fontSize = 12.sp)
                                }
                            }
                        } else {
                    val replaceCount = pendingHooks.count { it.hookPoint == "before" || it.hookPoint == "after" }
                    val beforeCount = pendingHooks.count { it.hookPoint == "before" }
                    val afterCount = pendingHooks.count { it.hookPoint == "after" }
                    val activeCount = pendingHooks.count { it.enabled }
val hookTypeColors = mapOf(
    "before" to MaterialTheme.colorScheme.secondary,
    "after" to MaterialTheme.colorScheme.tertiary,
)

    // 统计摘要
                            Row(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))) {
                                if (replaceCount > 0) Box(Modifier.weight(replaceCount.toFloat()).fillMaxHeight().background(accent))
                                if (beforeCount > 0) Box(Modifier.weight(beforeCount.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.secondary))
                                if (afterCount > 0) Box(Modifier.weight(afterCount.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("${activeCount}/${pendingHooks.size} 启用", color = t3, fontSize = 10.sp)
                            Text("${replaceCount} 替换", color = accent, fontSize = 10.sp)
                            if (beforeCount > 0) Text("${beforeCount} 前插", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
                            if (afterCount > 0) Text("${afterCount} 后插", color = MaterialTheme.colorScheme.tertiary, fontSize = 10.sp)
                            }

                            Spacer(Modifier.height(4.dp))

                            // Hook 卡片
                            pendingHooks.forEach { ph ->
                                val htColor = hookTypeColors[ph.hookMethodTypeName.ifBlank { ph.hookType }] ?: accent
                                Surface(
                                    Modifier.fillMaxWidth().clickable { openEditDialog(ph) },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (ph.enabled) surface else surface.copy(alpha = 0.5f),
                                    border = BorderStroke(0.5.dp, if (ph.enabled) bd else bd.copy(alpha = 0.2f))
                                ) {
                                    Box(Modifier.fillMaxWidth()) {
                                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // 类型圆点 + 标签
                                            Box(Modifier.size(8.dp).clip(CircleShape).background(htColor))
                                            Spacer(Modifier.width(6.dp))
                                        Text(hookTypeLabel(ph.hookMethodTypeName.ifBlank { ph.hookType }), color = htColor, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                            Spacer(Modifier.weight(1f))
                                            // 禁用标记
                                            if (!ph.enabled) {
                                                Surface(shape = RoundedCornerShape(4.dp), color = t3.copy(alpha = 0.1f)) {
                                                    Text("暂停", modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp), color = t3, fontSize = 10.sp)
                                                }
                                                Spacer(Modifier.width(6.dp))
                                            }
                                            // 删除
                                            IconButton(
                                                onClick = { onRemovePending(ph) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Outlined.RemoveCircleOutline, "删除", tint = t3.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        // 表达式主体
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                ph.methodName.ifEmpty { "（空）" },
                                                color = if (ph.enabled) t1 else t3,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(" : ", color = t3, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                            Text(ph.returnType, color = t2, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                            Text(" → ", color = t3, fontSize = 12.sp)
                                            Text(
                                                ph.replacementValue,
                                                color = if (ph.enabled) htColor else t3,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            // 给右下角 Switch 留空间
                                            Spacer(Modifier.width(36.dp))
                                        }
                                        // 类名 + 参数
                                        if (ph.className.isNotEmpty()) {
                                            Spacer(Modifier.height(2.dp))
                                            Row {
                                                Text(ph.className.substringAfterLast('.'), color = t3, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                if (ph.paramsCount > 0) Text(" · ${ph.paramsCount}参", color = t3, fontSize = 10.sp)
                                                if (ph.description.isNotEmpty()) Text(" · ${ph.description}", color = t3.copy(alpha = 0.6f), fontSize = 10.sp)
                                            }
                                        }
                                        }
                                        // 右下角开关
                                        Switch(
                                            checked = ph.enabled,
                                            onCheckedChange = { onTogglePending(ph, it) },
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).offset(y = (-12).dp).height(14.dp),
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = htColor,
                                                uncheckedThumbColor = t3.copy(alpha = 0.5f),
                                                uncheckedTrackColor = t3.copy(alpha = 0.12f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider(color = accent, thickness = 1.dp)
                }

                /* ────── ⑦ 候选方法 ────── */
                if (state.candidates.isNotEmpty()) {
                    itemsIndexed(state.candidates) { _, c -> CandidateRow(c, { onHookSingle(c) }, { onCandidateToPending(c) }, accent) }
                }
            }

        }
    }
}

/* ──── 策略芯片行 ──── */

@Composable
private fun StrategyChip(
    label: String, subtitle: String, checked: Boolean,
    onToggle: (Boolean) -> Unit, accent: Color, t1: Color, t3: Color
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (checked) accent.copy(alpha = 0.04f) else Color.Transparent),
        border = BorderStroke(0.5.dp, if (checked) accent.copy(alpha = 0.25f) else t3.copy(alpha = 0.08f))
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(if (checked) accent else t3.copy(alpha = 0.3f)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = if (checked) accent else t1, fontSize = 12.5.sp, fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal)
                Text(subtitle, color = t3, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(
                checked = checked, onCheckedChange = onToggle,
                modifier = Modifier.height(22.dp),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = t3.copy(alpha = 0.5f),
                    uncheckedTrackColor = t3.copy(alpha = 0.12f)
                )
            )
        }
    }
}

/* ──── 迷你输入框配色 ──── */

@Composable
private fun miniFieldColors(accent: Color): TextFieldColors = TextFieldDefaults.colors(
    focusedTextColor = WorkshopTheme.textPrimary(),
    unfocusedTextColor = WorkshopTheme.textPrimary(),
    focusedIndicatorColor = accent,
    unfocusedIndicatorColor = WorkshopTheme.textTertiary().copy(alpha = 0.2f),
    cursorColor = accent,
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedLabelColor = accent,
    unfocusedLabelColor = WorkshopTheme.textTertiary()
)
/* ──── 候选方法行 ──── */

@Composable
private fun CandidateRow(
    candidate: VipKillerEngine.VipCandidate,
    onHook: (VipKillerEngine.VipCandidate) -> Unit,
    onStage: (VipKillerEngine.VipCandidate) -> Unit,
    accent: Color
) {
    val confColor = when (candidate.confidence) {
        VipKillerEngine.Confidence.HIGH -> accent
        VipKillerEngine.Confidence.MEDIUM -> MaterialTheme.colorScheme.secondary
        VipKillerEngine.Confidence.LOW -> WorkshopTheme.textSecondary()
        VipKillerEngine.Confidence.SHARED -> MaterialTheme.colorScheme.tertiary
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), color = if (candidate.isHooked) accent.copy(alpha = 0.04f) else Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(4.dp), color = confColor.copy(alpha = 0.12f)) {
                Text(candidate.confidence.label, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp), color = confColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(candidate.methodName, color = WorkshopTheme.textPrimary(), fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${candidate.className.substringAfterLast('.')} · ${candidate.returnType}", color = WorkshopTheme.textTertiary(), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(4.dp))
            // 加入暂存
            IconButton(onClick = { onStage(candidate) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.BookmarkAdd, "加入暂存", tint = WorkshopTheme.textSecondary().copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(2.dp))
            // Hook 按钮
            if (candidate.isHooked) {
                Icon(Icons.Default.CheckCircle, "已Hook", tint = accent, modifier = Modifier.size(20.dp))
            } else {
                IconButton(onClick = { onHook(candidate) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.FlashOn, "Hook", tint = WorkshopTheme.textSecondary(), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}