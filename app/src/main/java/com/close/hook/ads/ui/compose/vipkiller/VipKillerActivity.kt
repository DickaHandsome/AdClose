package com.close.hook.ads.ui.compose.vipkiller

import android.os.Bundle
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.close.hook.ads.manager.ScopeManager
import com.close.hook.ads.manager.ServiceManager
import com.close.hook.ads.ui.activity.BaseActivity
import com.close.hook.ads.ui.compose.resolveThemeColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VipKillerActivity : BaseActivity() {

    private val viewModel by lazy { VipKillerViewModel(application) }

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { writeExport(it) }
    }

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { readImport(it) }
    }

    private fun writeExport(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = viewModel.exportConfigToJson()
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VipKillerActivity, "配置已导出", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VipKillerActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun readImport(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val count = withContext(Dispatchers.Main) {
                    viewModel.importConfigFromJson(json)
                }
                withContext(Dispatchers.Main) {
                    if (count > 0) {
                        viewModel.loadPendingHooks()
                        Toast.makeText(this@VipKillerActivity, "已导入 $count 条配置", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VipKillerActivity, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceManager.init()

        val packageName = intent.getStringExtra("packageName") ?: ""
        if (packageName.isNotEmpty()) {
            viewModel.setCurrentApp(packageName)
            viewModel.initEngineFromPackage(this, packageName)
            viewModel.loadPendingHooks()
        }

        setContent {
            MaterialTheme(colorScheme = resolveThemeColorScheme(this@VipKillerActivity)) {
                val state by viewModel.state.collectAsState()
                VipKillerScreen(
                    state = state,
                    onScanStart = { viewModel.startScan() },
                    onHookSingle = { viewModel.hookSingle(it) },
                    onHookAll = { viewModel.hookAll() },
                    onToggleL2 = { viewModel.toggleL2(it) },
                    onToggleL3 = { viewModel.toggleL3(it) },
                    onToggleL4 = { viewModel.toggleL4(it) },
                    onToggleL5 = { viewModel.toggleL5(it) },
                    onToggleFieldScan = { viewModel.toggleFieldScan(it) },
                    onSharedToggle = { viewModel.toggleShared(it) },
                    onAppendInput = { viewModel.setAppendInput(it) },
                    onRequestScope = { requestLsposedScope(packageName) },
                    onShowLogs = { showLogScreen() },
                    onExportConfig = { exportLauncher.launch("adclose_config_${packageName}_${System.currentTimeMillis()}.json") },
                    onImportConfig = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    onImportFromClipboard = {
                        val text = getSystemService(android.content.ClipboardManager::class.java)
                            ?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                        if (text.isNotBlank()) viewModel.importFromClipboard(text)
                    },
                    onBack = { finish() },
                    pendingHooks = state.pendingHooks,
                    onLoadPending = { viewModel.loadPendingHooks() },
                    onClearPending = { viewModel.clearPendingHooks() },
                    onClearDisabledPending = { viewModel.clearDisabledPendingHooks() },
                    onSavePending = { viewModel.addOrUpdatePendingHook(it) },
                    onRemovePending = { viewModel.removePendingHook(it) },
onCreateHook = { viewModel.createEmptyHook() },
                    onSearchMethods = { viewModel.searchMethods(it) },
onTogglePending = { hook, enabled -> viewModel.togglePendingHook(hook, enabled) },
                        onCandidateToPending = { viewModel.candidateToPending(it) }
                )
            }
        }
    }

    private fun showLogScreen() {
        startActivity(Intent(this, VipKillerLogActivity::class.java))
    }

    private fun requestLsposedScope(targetPkg: String) {
        if (targetPkg.isEmpty()) { Toast.makeText(this, "未选择目标应用", Toast.LENGTH_SHORT).show(); return }
        val appName = try {
            val ai = packageManager.getApplicationInfo(targetPkg, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) { targetPkg }
        val appIcon = try {
            packageManager.getApplicationIcon(targetPkg)
        } catch (_: Exception) { null }

        lifecycleScope.launch {
            try {
                val currentScope = ScopeManager.getScope()
                if (currentScope != null && targetPkg in currentScope) {
                    Toast.makeText(this@VipKillerActivity, "「$appName」已在作用域中", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                ScopeManager.addScope(targetPkg, object : ScopeManager.ScopeCallback {
                    override fun onScopeOperationSuccess(message: String) {
                        Toast.makeText(this@VipKillerActivity, message, Toast.LENGTH_SHORT).show()
                    }
                    override fun onScopeOperationFail(message: String) {
                        Toast.makeText(this@VipKillerActivity, message, Toast.LENGTH_LONG).show()
                    }
                })
            } catch (e: Exception) {
                Toast.makeText(this@VipKillerActivity, "作用域操作失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}