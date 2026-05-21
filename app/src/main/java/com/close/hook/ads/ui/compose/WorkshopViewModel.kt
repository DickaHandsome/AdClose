package com.close.hook.ads.ui.compose

import androidx.lifecycle.ViewModel
import com.close.hook.ads.data.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ModuleDef(
    val id: String,
    val name: String,
    val icon: Int,
    val statusText: String,
    val indicatorActive: Boolean
)

data class Preset(
    val id: String,
    val name: String,
    val isActive: Boolean
)

class WorkshopViewModel : ViewModel() {

    private val _currentTargetApp = MutableStateFlow<AppInfo?>(null)
    val currentTargetApp: StateFlow<AppInfo?> = _currentTargetApp.asStateFlow()

    private val _modulesWithStatus = MutableStateFlow<List<ModuleDef>>(emptyList())
    val modulesWithStatus: StateFlow<List<ModuleDef>> = _modulesWithStatus.asStateFlow()

    private val _presets = MutableStateFlow<List<Preset>>(emptyList())
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    private val _activePresetId = MutableStateFlow<String?>(null)
    val activePresetId: StateFlow<String?> = _activePresetId.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val _appSearchQuery = MutableStateFlow("")
    val appSearchQuery: StateFlow<String> = _appSearchQuery.asStateFlow()

    init {
        initMockData()
    }

    private fun initMockData() {
        _modulesWithStatus.value = listOf(
            ModuleDef("vip_killer", "通杀会员", android.R.drawable.ic_menu_gallery, "就绪", true),
            ModuleDef("hook", "HOOK 管理", android.R.drawable.ic_menu_manage, "运行中", true),
            ModuleDef("reward_skip", "免广告奖励", android.R.drawable.ic_menu_share, "就绪", true),
            ModuleDef("dexdump", "DEX 导出", android.R.drawable.ic_menu_save, "就绪", false),
            ModuleDef("data", "数据管理", android.R.drawable.ic_menu_edit, "待激活", false),
            ModuleDef("block", "拦截规则", android.R.drawable.ic_menu_close_clear_cancel, "运行中", true),
            ModuleDef("settings", "系统设置", android.R.drawable.ic_menu_preferences, "就绪", true)
        )

        _presets.value = listOf(
            Preset("p1", "通用方案", true),
            Preset("p2", "增强拦截", false),
            Preset("p3", "调试模式", false),
            Preset("p4", "新建方案...", false)
        )
        _activePresetId.value = "p1"
    }

    fun initTargetApp(packageName: String, appName: String, versionName: String) {
        _currentTargetApp.value = AppInfo(
            packageName = packageName,
            appName = appName.ifEmpty { packageName },
            versionName = versionName,
            versionCode = 0,
            firstInstallTime = 0L,
            lastUpdateTime = 0L,
            size = 0L,
            targetSdk = 0,
            minSdk = 0,
            isAppEnable = 1,
            isEnable = 1,
            isSystem = false
        )
    }

    fun selectApp(packageName: String) {
        val app = _allApps.value.find { it.packageName == packageName }
        if (app != null) {
            _currentTargetApp.value = app
        }
    }

    fun setAllApps(apps: List<AppInfo>) {
        _allApps.value = apps
        if (_currentTargetApp.value == null && apps.isNotEmpty()) {
            _currentTargetApp.value = apps.first()
        }
    }

    fun updateAppSearchQuery(query: String) {
        _appSearchQuery.value = query
    }

    fun applyPreset(presetId: String) {
        _presets.update { list ->
            list.map { it.copy(isActive = it.id == presetId) }
        }
        _activePresetId.value = presetId
    }

    fun createPreset(name: String) {
        val newId = "p_${System.currentTimeMillis()}"
        _presets.update { it + Preset(newId, name, false) }
    }

    fun deletePreset(presetId: String) {
        _presets.update { it.filter { p -> p.id != presetId } }
        if (_activePresetId.value == presetId) {
            _activePresetId.value = null
        }
    }

    fun renamePreset(presetId: String, newName: String) {
        _presets.update { list ->
            list.map { if (it.id == presetId) it.copy(name = newName) else it }
        }
    }
}