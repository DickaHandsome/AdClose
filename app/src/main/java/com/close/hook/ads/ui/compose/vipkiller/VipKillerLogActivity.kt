package com.close.hook.ads.ui.compose.vipkiller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import com.close.hook.ads.ui.activity.BaseActivity
import com.close.hook.ads.ui.compose.resolveThemeColorScheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VipKillerLogActivity : BaseActivity() {

    private val viewModel: VipKillerLogViewModel by viewModels()

    private val exportLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { writeExport(it) }
    }

    private fun writeExport(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(viewModel.getExportText().toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VipKillerLogActivity, "日志已导出", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VipKillerLogActivity, "导出失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareLogs() {
        val text = viewModel.getExportText()
        if (text.isBlank()) {
            Toast.makeText(this, "暂无日志可分享", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "分享日志"))
    }

    private fun copyToClipboard() {
        val text = viewModel.getExportText()
        if (text.isBlank()) {
            Toast.makeText(this, "暂无日志可复制", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("AdClose Debug Log", text))
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = resolveThemeColorScheme(this@VipKillerLogActivity)) {
                val state by viewModel.state.collectAsState()
                VipKillerLogScreen(
                    state = state,
                    onBack = { finish() },
                    onFilterLevel = { viewModel.setFilterLevel(it) },
                    onSearchQuery = { viewModel.setSearchQuery(it) },
                    onToggleAutoScroll = { viewModel.toggleAutoScroll() },
                    onToggleFilterExpanded = { viewModel.toggleFilterExpanded() },
                    onClearLogs = { viewModel.clearLogs() },
                    onExport = { exportLauncher.launch("adclose_debug_${System.currentTimeMillis()}.log") },
                    onShare = { shareLogs() },
                    onCopyAll = { copyToClipboard() },
                    onSelectEntry = { viewModel.selectEntry(it) }
                )
            }
        }
    }
}