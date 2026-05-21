package com.close.hook.ads.ui.compose.vipkiller

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.close.hook.ads.hook.ha.VipKillerEngine
import com.close.hook.ads.data.model.CustomHookInfo
import com.close.hook.ads.data.model.HookMethodType
import com.close.hook.ads.preference.HookPrefs
import dalvik.system.PathClassLoader
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.luckypray.dexkit.DexKitBridge

class VipKillerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VipKillerVM"
        private val prettyJson = Json { prettyPrint = true }
        private val lenientJson = Json { ignoreUnknownKeys = true }
    }

    private var engine: VipKillerEngine? = null

    data class VipKillerState(
        val isLoading: Boolean = false, val isScanning: Boolean = false,
        val scanProgress: Float = 0f,
        val candidates: List<VipKillerEngine.VipCandidate> = emptyList(),
        val hookedCount: Int = 0, val totalCount: Int = 0,
        val currentApp: String = "",
        val enableL2: Boolean = false,
        val enableL3: Boolean = false,
        val enableL4: Boolean = false, val enableL5: Boolean = false,
        val enableFieldScan: Boolean = false,
        val enableShared: Boolean = false,
        val appendInput: String = "",
        val scanLogs: List<LogEntry> = emptyList(),
        val pendingHooks: List<VipKillerEngine.PendingHook> = emptyList()
    )

    @Serializable
    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: LogLevel, val message: String
    )

    @Serializable
    enum class LogLevel(val label: String) {
        INFO("信息"), SUCCESS("成功"), WARNING("警告"), ERROR("错误")
    }

    private val _state = MutableStateFlow(VipKillerState())
    val state: StateFlow<VipKillerState> = _state.asStateFlow()

    fun initEngineFromPackage(context: Context, packageName: String) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val pm: PackageManager = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val apkPath = appInfo.sourceDir
                withContext(Dispatchers.Main) { addLog(LogLevel.INFO, "目标APK: $apkPath") }
                System.loadLibrary("dexkit")
                // 使用 ClassLoader 模式创建 DexKitBridge，与 AdvancedTongsha 保持一致
                val targetClassLoader = PathClassLoader(apkPath, ClassLoader.getSystemClassLoader())
                @Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
                val bridge = DexKitBridge.create(targetClassLoader, true)
                    ?: DexKitBridge.create(apkPath)
                    ?: throw IllegalStateException("DexKitBridge.create returned null for $packageName")
                withContext(Dispatchers.Main) {
                    engine = VipKillerEngine(targetClassLoader, bridge)
                    addLog(LogLevel.SUCCESS, "引擎初始化成功: $packageName")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addLog(LogLevel.ERROR, "引擎初始化失败: ${e.message}")
                    Log.e(TAG, "initEngineFromPackage failed for $packageName", e)
                }
            }
        }
    }

    fun setCurrentApp(pkg: String) { _state.value = _state.value.copy(currentApp = pkg) }

    fun startScan() {
        val eng = engine ?: run { addLog(LogLevel.ERROR, "引擎未初始化，无法扫描"); return }
        addLog(LogLevel.INFO, "开始扫描 ${_state.value.currentApp}…")
        viewModelScope.launch {
            _state.value = _state.value.copy(isScanning = true, scanProgress = 0f, candidates = emptyList())
            val s = _state.value
            val merged = mutableListOf<VipKillerEngine.VipCandidate>()
            try {
                withContext(Dispatchers.Default) {
                    // 收集启用的层及其预估工作量
                    data class LayerTask(val name: String, val weight: Float, val block: suspend ((Int, Int) -> Unit) -> List<VipKillerEngine.VipCandidate>)
                    val tasks = mutableListOf<LayerTask>()
                    // 权重按各层实际循环量比例分配：L2 85关键词 · L3 80+pattern双通道 · L5 25×6×4 · Field 全量字段
                    if (s.enableL2) tasks += LayerTask("L2", 85f) { cb -> eng.scanL2(cb) }
                    if (s.enableL3) tasks += LayerTask("L3", 160f) { cb -> eng.scanL3(cb) }
                    if (s.enableL4) tasks += LayerTask("L4", 5f) { eng.scanL4() }
                    if (s.enableL5) tasks += LayerTask("L5", 600f) { cb -> eng.scanL5(cb) }
                    if (s.enableFieldScan) tasks += LayerTask("Field", 300f) { cb -> eng.scanFieldGetters(200, cb) }
                    if (tasks.isEmpty()) return@withContext

                    val totalWeight = tasks.sumOf { it.weight.toDouble() }.toFloat()
                    var accumulatedWeight = 0f
                    for (task in tasks) {
                        val layerFrom = accumulatedWeight / totalWeight
                        val layerSpan = task.weight / totalWeight
                        val r = task.block { done, total ->
                            val p = layerFrom + layerSpan * (done.toFloat() / total.toFloat().coerceAtLeast(1f))
                            _state.value = _state.value.copy(scanProgress = p.coerceIn(0.01f, 0.98f))
                        }
                        merged += r
                        accumulatedWeight += task.weight
                    }
                }
            } catch (e: Exception) {
                addLog(LogLevel.ERROR, "扫描失败: ${e.message}")
                _state.value = _state.value.copy(isScanning = false)
                return@launch
            }
            val results = merged.distinctBy { "${it.className}#${it.methodName}" }
                .sortedByDescending { it.confidence.ordinal }
            _state.value = _state.value.copy(
                isScanning = false, scanProgress = 1f,
                candidates = results, totalCount = results.size)
            val layerCount = listOf(s.enableL2, s.enableL3, s.enableL4, s.enableL5, s.enableFieldScan).count { it }
            addLog(LogLevel.SUCCESS, "扫描完成: ${results.size} 个候选方法 (${layerCount}层)")
        }
    }

    fun hookSingle(c: VipKillerEngine.VipCandidate) {
        val eng = engine ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val r = eng.hook(c)
            if (r.success) { addLog(LogLevel.SUCCESS, "✓ ${c.methodName}()"); refreshState() }
            else if (r.message.contains("需目标应用进程")) {
                val pkg = _state.value.currentApp
                eng.hookDeferred(c, pkg)
                syncToHookPrefs(pkg, VipKillerEngine.loadPendingHooks(pkg))
                addLog(LogLevel.WARNING, "⏳ ${c.methodName} 已暂存"); loadPendingHooks()
            }
            else addLog(LogLevel.ERROR, "✗ ${c.methodName}: ${r.message}")
        }
    }

    fun hookAll() {
        val eng = engine ?: return
        viewModelScope.launch(Dispatchers.Default) {
            addLog(LogLevel.INFO, "一键Hook全部...")
            val pkg = _state.value.currentApp
            if (!VipKillerEngine.isXposedAvailable()) {
                eng.hookAllDeferred(_state.value.candidates, pkg)
                syncToHookPrefs(pkg, VipKillerEngine.loadPendingHooks(pkg))
                addLog(LogLevel.WARNING, "⏳ 全部 ${_state.value.candidates.size} 个方法已暂存"); loadPendingHooks()
            } else { val n = eng.hookAll(_state.value.candidates); addLog(LogLevel.SUCCESS, "成功Hook $n 个方法") }
            refreshState()
        }
    }

    fun toggleL2(v: Boolean) { _state.value = _state.value.copy(enableL2 = v) }
    fun toggleL3(v: Boolean) { _state.value = _state.value.copy(enableL3 = v) }
    fun toggleL4(v: Boolean) { _state.value = _state.value.copy(enableL4 = v) }
    fun toggleL5(v: Boolean) { _state.value = _state.value.copy(enableL5 = v) }
    fun toggleFieldScan(v: Boolean) { _state.value = _state.value.copy(enableFieldScan = v) }
    fun toggleShared(v: Boolean) { _state.value = _state.value.copy(enableShared = v) }
    fun setAppendInput(v: String) { _state.value = _state.value.copy(appendInput = v) }
    fun clearLogs() { _state.value = _state.value.copy(scanLogs = emptyList()) }

    fun loadPendingHooks() {
        val pkg = _state.value.currentApp
        if (pkg.isEmpty()) { addLog(LogLevel.WARNING, "未选择目标应用"); return }
        _state.value = _state.value.copy(pendingHooks = VipKillerEngine.loadPendingHooks(pkg))
    }

    fun clearPendingHooks() {
        val pkg = _state.value.currentApp
        if (pkg.isEmpty()) return
        VipKillerEngine.clearPendingHooks(pkg)
        HookPrefs.setCustomHookConfigs("vipkiller_$pkg", emptyList())
        _state.value = _state.value.copy(pendingHooks = emptyList())
    }

    private fun syncToHookPrefs(pkg: String, hooks: List<VipKillerEngine.PendingHook>) {
        HookPrefs.setCustomHookConfigs("vipkiller_$pkg", hooks.map { ph ->
            val methodType = ph.hookMethodTypeName.ifBlank { ph.hookType }
            val hmt = HookMethodType.entries.find { it.name == methodType } ?: HookMethodType.HOOK_ALL_METHODS
            CustomHookInfo(
                id = "${ph.className}#${ph.methodName}",
                hookMethodType = hmt,
                isEnabled = ph.enabled,
                className = ph.className,
                methodNames = if (ph.methodName.isNotBlank()) listOf(ph.methodName) else null,
                hookPoint = ph.hookPoint.ifBlank { null },
                searchStrings = if (ph.searchStrings.isNotBlank()) ph.searchStrings.split(",").map { it.trim() }.filter { it.isNotEmpty() } else null,
                parameterTypes = if (ph.paramTypes.isNotBlank()) ph.paramTypes.split(",").map { it.trim() }.filter { it.isNotEmpty() } else null,
                fieldName = ph.fieldName.ifBlank { null },
                fieldValue = ph.fieldValue.ifBlank { null },
                returnValue = ph.replacementValue.ifBlank {
                    val rt = ph.returnType.lowercase().replace("java.lang.", "")
                    when {
                        rt == "boolean" -> "true"
                        rt == "long" -> "3495751810"
                        rt == "int" || rt == "integer" -> {
                            val lower = ph.methodName.lowercase()
                            when {
                                lower.matches(Regex(".*(level|grade|rank|role)")) -> "999"
                                lower.matches(Regex(".*(status|state|flag|mode)")) -> "999"
                                lower.contains("expire") || lower.contains("end") || lower.contains("remain")
                                    || lower.contains("count") || lower.contains("times") || lower.contains("balance")
                                    || lower.contains("point") || lower.contains("credit") || lower.contains("coin") -> "999999"
                                else -> "1"
                            }
                        }
                        rt == "float" -> "999999.0"
                        rt == "double" -> "999999.0"
                        rt == "string" -> "premium"
                        else -> "true"
                    }
                }
            )
        })
    }

    fun clearDisabledPendingHooks() {
        val pkg = _state.value.currentApp
        if (pkg.isEmpty()) return
        val toRemove = _state.value.pendingHooks.filter { !it.enabled }
        if (toRemove.isEmpty()) return
        toRemove.forEach { VipKillerEngine.removePendingHook(pkg, it) }
        syncToHookPrefs(pkg, VipKillerEngine.loadPendingHooks(pkg))
        loadPendingHooks()
    }

    fun candidateToPending(candidate: VipKillerEngine.VipCandidate) {
        val pkg = _state.value.currentApp; if (pkg.isEmpty()) return
        val hook = VipKillerEngine.PendingHook(
            className = candidate.className,
            methodName = candidate.methodName,
            returnType = candidate.returnType,
            paramsCount = 0,
            hookType = HookMethodType.HOOK_ALL_METHODS.name,
            enabled = true,
            replacementValue = when {
                candidate.returnType.lowercase().contains("boolean") -> "true"
                candidate.returnType.lowercase().contains("long") -> "3495751810"
                candidate.returnType.lowercase().contains("int") -> "0"
                else -> "true"
            },
            description = "来自扫描结果 · ${candidate.confidence.label}",
            hookMethodTypeName = HookMethodType.HOOK_ALL_METHODS.name
        )
        addOrUpdatePendingHook(hook)
        // 从候选列表移除
        _state.value = _state.value.copy(
            candidates = _state.value.candidates - candidate
        )
    }

    fun addOrUpdatePendingHook(hook: VipKillerEngine.PendingHook) {
        val pkg = _state.value.currentApp; if (pkg.isEmpty()) { addLog(LogLevel.WARNING, "未选择目标"); return }
        VipKillerEngine.updatePendingHook(pkg, hook)
        // 同步到 HookPrefs，HookLogic 在目标App启动时会读取
        syncToHookPrefs(pkg, VipKillerEngine.loadPendingHooks(pkg))
        addLog(LogLevel.SUCCESS, "暂存已保存: ${hook.methodName} (${hook.returnType}, ${hook.hookType})")
        loadPendingHooks()
    }

    fun createEmptyHook() {
        val pkg = _state.value.currentApp; if (pkg.isEmpty()) { addLog(LogLevel.WARNING, "未选择目标应用"); return }
        val hook = VipKillerEngine.PendingHook(
            className = "", methodName = "", returnType = "boolean",
            paramsCount = 0, hookType = HookMethodType.HOOK_ALL_METHODS.name, enabled = true,
            replacementValue = "true", description = "",
            hookMethodTypeName = HookMethodType.HOOK_ALL_METHODS.name
        )
        addOrUpdatePendingHook(hook)
    }

    /** DexKit 方法签名查询：根据关键词搜索APK内匹配的方法 */
    fun searchMethods(keyword: String): List<VipKillerEngine.MethodSignature> {
        return engine?.searchMethods(keyword) ?: emptyList()
    }

    /** 从剪贴板文本批量解析方法签名并创建暂存 */
    fun importFromClipboard(text: String): Int {
        val pkg = _state.value.currentApp; if (pkg.isEmpty()) { addLog(LogLevel.WARNING, "未选择目标应用"); return 0 }
        if (text.isBlank()) { addLog(LogLevel.WARNING, "剪贴板为空"); return 0 }
        val lines = text.trim().lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }
        if (lines.isEmpty()) { addLog(LogLevel.WARNING, "剪贴板无有效内容"); return 0 }
        var imported = 0
        val skipped = mutableListOf<String>()
        for (line in lines) {
            val parsed = parseMethodSignature(line) ?: run { skipped.add(line.take(60)); continue }
            val sigs = engine?.searchMethods(parsed.second) ?: emptyList()
            val best = if (parsed.first.isNotEmpty()) sigs.firstOrNull { it.className.contains(parsed.first) } else sigs.firstOrNull()
            val hook = VipKillerEngine.PendingHook(
                className = best?.className ?: parsed.first,
                methodName = best?.methodName ?: parsed.second,
                returnType = best?.returnType ?: "boolean",
                paramsCount = best?.paramsCount ?: 0,
                hookType = HookMethodType.HOOK_ALL_METHODS.name,
                enabled = true,
                replacementValue = when {
                    best == null -> "true"
                    best.returnType.lowercase().contains("boolean") -> "true"
                    best.returnType.lowercase().contains("long") -> "3495751810"
                    best.returnType.lowercase().contains("int") -> {
                        val lower = best.methodName.lowercase()
                        when {
                            lower.matches(Regex(".*(level|grade|rank|role)")) -> "999"
                            lower.matches(Regex(".*(status|state|flag|mode)")) -> "999"
                            lower.contains("expire") || lower.contains("end") || lower.contains("remain")
                                || lower.contains("count") || lower.contains("times") || lower.contains("balance")
                                || lower.contains("point") || lower.contains("credit") || lower.contains("coin") -> "999999"
                            else -> "1"
                        }
                    }
                    best.returnType.lowercase().contains("float") -> "999999.0"
                    best.returnType.lowercase().contains("double") -> "999999.0"
                    best.returnType.lowercase().contains("string") -> "premium"
                    else -> "true"
                },
                description = "剪贴板导入",
                hookMethodTypeName = HookMethodType.HOOK_ALL_METHODS.name
            )
            addOrUpdatePendingHook(hook); imported++
        }
        if (skipped.isNotEmpty()) addLog(LogLevel.WARNING, "跳过了 ${skipped.size} 行无法解析的签名")
        return imported
    }

    private fun parseMethodSignature(text: String): Pair<String, String>? {
        val cleaned = text.trim().replace(Regex("\\s*→.*"), "").trim()
        if (cleaned.startsWith("L") && cleaned.contains(";->")) {
            val parts = cleaned.split(";->", limit = 2)
            val cls = parts[0].substring(1).replace('/', '.')
            val method = parts[1].replace(Regex("\\(.*"), "").trim()
            if (cls.isNotBlank() && method.isNotBlank()) return cls to method
        }
        val hashIdx = cleaned.lastIndexOf('#')
        if (hashIdx > 0) {
            val cls = cleaned.substring(0, hashIdx).trim()
            val method = cleaned.substring(hashIdx + 1).replace(Regex("\\(.*"), "").trim()
            if (cls.isNotBlank() && method.isNotBlank()) return cls to method
        }
        val dotSep = Regex("\\.([a-zA-Z_<]\\w*(?:<init>|<clinit>)?)\\s*(?:\\(|$)")
        val lastDot = dotSep.findAll(cleaned).lastOrNull()
        if (lastDot != null) {
            val method = lastDot.groupValues[1]
            val cls = cleaned.substring(0, lastDot.range.first).trim()
            if (cls.isNotBlank() && method.isNotBlank()) return cls to method
        }
        val methodOnly = cleaned.replace(Regex("[()<> ].*"), "").trim()
        if (methodOnly.matches(Regex("^[a-zA-Z_]\\w*$"))) return "" to methodOnly
        return null
    }

    /** 导出当前暂存配置为 JSON 字符串 */
    fun exportConfigToJson(): String {
        val hooks = _state.value.pendingHooks
        if (hooks.isEmpty()) return "[]"
        return try {
            prettyJson.encodeToString(hooks)
        } catch (e: Exception) {
            addLog(LogLevel.ERROR, "序列化失败: ${e.message}")
            "[]"
        }
    }

    /** 从 JSON 字符串导入配置，返回成功导入条数 */
    fun importConfigFromJson(json: String): Int {
        val pkg = _state.value.currentApp
        if (pkg.isEmpty()) { addLog(LogLevel.WARNING, "未选择目标应用"); return 0 }
        if (json.isBlank()) { addLog(LogLevel.WARNING, "配置为空"); return 0 }
        return try {
            val hooks = lenientJson.decodeFromString<List<VipKillerEngine.PendingHook>>(json)
            hooks.forEach { addOrUpdatePendingHook(it) }
            addLog(LogLevel.SUCCESS, "已导入 ${hooks.size} 条配置")
            hooks.size
        } catch (e: Exception) {
            addLog(LogLevel.ERROR, "导入失败: ${e.message}")
            0
        }
    }

    fun removePendingHook(hook: VipKillerEngine.PendingHook) {
        val pkg = _state.value.currentApp; if (pkg.isEmpty()) return
        VipKillerEngine.removePendingHook(pkg, hook)
        syncToHookPrefs(pkg, VipKillerEngine.loadPendingHooks(pkg))
        loadPendingHooks()
    }

    fun togglePendingHook(hook: VipKillerEngine.PendingHook, enabled: Boolean) {
        val pkg = _state.value.currentApp; if (pkg.isEmpty()) return
        VipKillerEngine.togglePendingHook(pkg, hook, enabled)
        syncToHookPrefs(pkg, VipKillerEngine.loadPendingHooks(pkg))
        loadPendingHooks()
    }

    fun toggleAllPendingHooks(enabled: Boolean) {
        val pkg = _state.value.currentApp; if (pkg.isEmpty()) return
        VipKillerEngine.toggleAllPendingHooks(pkg, enabled)
        syncToHookPrefs(pkg, VipKillerEngine.loadPendingHooks(pkg))
        loadPendingHooks()
    }

    private fun addLog(level: LogLevel, msg: String) {
        val logs = _state.value.scanLogs.toMutableList()
        logs.add(LogEntry(level = level, message = msg))
        _state.value = _state.value.copy(scanLogs = logs.takeLast(200))
    }

    private fun refreshState() {
        _state.value = _state.value.copy(hookedCount = _state.value.candidates.count { it.isHooked })
    }
}