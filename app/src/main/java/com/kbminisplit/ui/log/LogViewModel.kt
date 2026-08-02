package com.kbminisplit.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbminisplit.data.repository.ProgramRepository
import com.kbminisplit.data.repository.SessionRepository
import com.kbminisplit.domain.model.Program
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.SetEntry
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
 * Drives the Log tab. Reads committed history from [SessionRepository] and folds
 * it into a calendar grid via [buildLogRows]. Cell taps open a read-only
 * [SessionDetail] sheet.
 *
 * The detail sheet replays a session exactly as it was performed — its own stored
 * order, weights and rep ranges — rather than describing it in terms of today's
 * program, which may since have changed.
 */
@HiltViewModel
class LogViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    programRepository: ProgramRepository,
    private val clock: Clock,
) : ViewModel() {

    private val history: StateFlow<List<Session>> =
        sessionRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val program: StateFlow<Program> = programRepository.observeProgram()
        .stateIn(viewModelScope, SharingStarted.Eagerly, Program.EMPTY)

    private val exerciseNames: StateFlow<Map<String, String>> =
        programRepository.observeExerciseNames()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

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
        _selected.value = session.toDetail(
            dayLabel = program.value.dayByKey(session.dayKey)?.name ?: session.dayKey,
            names = exerciseNames.value,
        )
    }

    fun onDismissDetail() {
        _selected.value = null
    }
}

/**
 * Rebuilds a session's movements from its own rows. Sets arrive already ordered as
 * performed, so consecutive runs of one movement are grouped in place — which also
 * keeps a movement that legitimately appeared twice in a day as two entries.
 */
internal fun Session.toDetail(dayLabel: String, names: Map<String, String>): SessionDetail =
    SessionDetail(
        date = date,
        dayLabel = dayLabel,
        feedback = feedback,
        movements = sets.groupConsecutiveMovements().map { group ->
            val first = group.first()
            // Weight and reps come from a working set; a circuit has only those.
            val reference = group.firstOrNull { !it.isPriming } ?: first
            MovementDetail(
                name = names[first.exerciseSlug] ?: first.exerciseSlug,
                weightKg = reference.weightKg,
                repsLabel = reference.repRangeLabel,
                statuses = group.map { it.status },
            )
        },
    )

private fun List<SetEntry>.groupConsecutiveMovements(): List<List<SetEntry>> =
    fold(mutableListOf<MutableList<SetEntry>>()) { groups, set ->
        val current = groups.lastOrNull()?.first()
        if (current != null &&
            current.position == set.position &&
            current.exerciseSlug == set.exerciseSlug
        ) {
            groups.last().add(set)
        } else {
            groups.add(mutableListOf(set))
        }
        groups
    }
