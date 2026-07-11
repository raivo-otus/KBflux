package com.kbminisplit.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.ui.mapper.toKbCircuits
import com.kbminisplit.ui.mapper.toStrengthRows
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Drives the Log tab. Reads committed history from [SessionRepository] and
 * folds it into a calendar grid via [buildLogRows]. Cell taps open a read-only
 * [SessionDetail] sheet (spec §5.2).
 */
@HiltViewModel
class LogViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val clock: Clock,
) : ViewModel() {

    private val history: StateFlow<List<Session>> =
        sessionRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val state: StateFlow<LogUiState> = history
        .map { sessions ->
            val content = buildLogRows(sessions = sessions, today = LocalDate.now(clock))
            LogUiState.Ready(rows = content.rows, todayRowIndex = content.todayRowIndex)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LogUiState.Loading)

    private val _justCommittedDate = MutableStateFlow<LocalDate?>(null)
    val justCommittedDate: StateFlow<LocalDate?> = _justCommittedDate.asStateFlow()

    fun markJustCommitted(date: LocalDate) {
        _justCommittedDate.value = date
    }

    fun clearJustCommitted() {
        _justCommittedDate.value = null
    }

    private val _selected = MutableStateFlow<SessionDetail?>(null)
    val selected: StateFlow<SessionDetail?> = _selected.asStateFlow()

    fun onCellTap(date: LocalDate) {
        val session = history.value.firstOrNull { it.date == date } ?: return
        _selected.value = session.toDetail()
    }

    fun onDismissDetail() {
        _selected.value = null
    }
}

private val CATALOG_ORDER: Map<String, Int> =
    ExerciseCatalog.all.withIndex().associate { (idx, e) -> e.slug to idx }

private fun Session.toDetail(): SessionDetail {
    val kbCircuits = sets.toKbCircuits().map { it.status }

    val strengthExercises = sets.filter { it.exerciseSlug != ExerciseCatalog.KbFlow.slug && !it.isPriming }
        .mapNotNull { ExerciseCatalog.bySlug(it.exerciseSlug) }
        .distinctBy { it.slug }
        .sortedBy { CATALOG_ORDER[it.slug] ?: Int.MAX_VALUE }

    val strengthRows = sets.toStrengthRows(strengthExercises).map { row ->
        StrengthDetail(
            exerciseDisplayName = row.exercise.displayName,
            weightKg = row.weightKg,
            targetReps = row.targetReps,
            primeStatus = row.prime.status,
            workingStatuses = row.working.map { it.status },
        )
    }

    return SessionDetail(
        date = date,
        split = split,
        feedback = feedback,
        kbWeightKg = kbWeightKg,
        kbCircuits = kbCircuits,
        strength = strengthRows,
    )
}
