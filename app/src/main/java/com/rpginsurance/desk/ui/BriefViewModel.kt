package com.rpginsurance.desk.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.rpginsurance.brief.Brief
import com.rpginsurance.brief.IST
import com.rpginsurance.brief.TableFailure
import com.rpginsurance.desk.app
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BriefUiState(
    val brief: Brief? = null,
    val refreshing: Boolean = false,
    val hasToken: Boolean = false,
    /** Set when the last refresh failed completely and [brief] is an older saved copy. */
    val refreshFailures: List<TableFailure> = emptyList(),
) {
    val isStale: Boolean get() = brief != null && brief.date != LocalDate.now(IST).toString()
}

class BriefViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = application.app.repository
    private val settings = application.app.settings

    private val _state = MutableStateFlow(BriefUiState(brief = repo.brief.value, hasToken = settings.hasToken))
    val state: StateFlow<BriefUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repo.brief.collect { b -> _state.update { it.copy(brief = b) } } }
        val cached = repo.brief.value
        if (settings.hasToken && (cached == null || cached.date != LocalDate.now(IST).toString())) refresh()
    }

    /** Called when returning from Settings, in case the token changed. */
    fun onResume() {
        val had = _state.value.hasToken
        val has = settings.hasToken
        _state.update { it.copy(hasToken = has) }
        if (has && !had) refresh()
    }

    fun refresh() {
        if (_state.value.refreshing || !settings.hasToken) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val fresh = try {
                repo.refresh()
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
            // If everything failed, the repository kept the old brief; surface why.
            val keptOld = fresh !== repo.brief.value
            _state.update { it.copy(refreshFailures = if (keptOld) fresh.failures else emptyList()) }
        }
    }
}
