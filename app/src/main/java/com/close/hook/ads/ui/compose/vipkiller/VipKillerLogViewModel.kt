package com.close.hook.ads.ui.compose.vipkiller

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VipKillerLogViewModel : ViewModel() {

    data class DebugLogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: LogLevel,
        val tag: String,
        val message: String
    )

    enum class LogLevel(val label: String) {
        VERBOSE("V"), DEBUG("D"), INFO("I"), WARN("W"), ERROR("E"), FATAL("F")
    }

    data class LogUiState(
        val logs: List<DebugLogEntry> = emptyList(),
        val filterLevel: LogLevel? = null,
        val searchQuery: String = "",
        val isAutoScroll: Boolean = true,
        val selectedIndex: Int = -1,
        val filterExpanded: Boolean = false
    ) {
        val filteredLogs: List<DebugLogEntry>
            get() {
                var list = logs
                filterLevel?.let { lv -> list = list.filter { it.level == lv } }
                if (searchQuery.isNotBlank()) {
                    val q = searchQuery.lowercase()
                    list = list.filter { it.message.lowercase().contains(q) || it.tag.lowercase().contains(q) }
                }
                return list
            }

        val levelCounts: Map<LogLevel, Int>
            get() = logs.groupingBy { it.level }.eachCount()

        val exportText: String
            get() = logs.joinToString("\n") { "[${it.level.label}] ${it.tag}: ${it.message}" }
    }

    private val _state = MutableStateFlow(LogUiState())
    val state: StateFlow<LogUiState> = _state.asStateFlow()

    fun setFilterLevel(level: LogLevel?) {
        _state.value = _state.value.copy(filterLevel = level)
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun toggleAutoScroll() {
        _state.value = _state.value.copy(isAutoScroll = !_state.value.isAutoScroll)
    }

    fun toggleFilterExpanded() {
        _state.value = _state.value.copy(filterExpanded = !_state.value.filterExpanded)
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList(), selectedIndex = -1)
    }

    fun selectEntry(index: Int) {
        _state.value = _state.value.copy(
            selectedIndex = if (_state.value.selectedIndex == index) -1 else index
        )
    }

    fun pushLog(level: LogLevel, tag: String, message: String) {
        val entry = DebugLogEntry(level = level, tag = tag, message = message)
        _state.value = _state.value.copy(
            logs = (_state.value.logs + entry).takeLast(2000)
        )
    }

    fun getExportText(): String = _state.value.exportText
    fun getFilteredLogs(): List<DebugLogEntry> = _state.value.filteredLogs
}