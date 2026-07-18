package com.kbminisplit.ui.tracker

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.SetStatus
import com.kbminisplit.domain.model.Split
import java.time.LocalDate

/**
 * Identity of a single button on the Tracker. Mirrors the unique index on
 * `in_progress_set` so the ViewModel can address sets without ambiguity.
 *
 * [weightKg] is the number shown inside the circle for Prime/Warm-up sets (their
 * acclimatization load); working and KB cells carry it too but don't render it.
 */
data class SetCell(
    val exerciseSlug: String,
    val setIndex: Int,
    val isPriming: Boolean,
    val status: SetStatus,
    val weightKg: Double,
)

/** Visual reference for a KB movement in the section header — no button attached. */
data class KbMovementLabel(
    val exercise: Exercise,
    val repsLabel: String,
)

/**
 * The KB Flow block: the day's movements shown as labels for reference, and
 * three round buttons — one per circuit (spec §2.2). Set tracking is at the
 * circuit level, not per movement.
 */
data class KbBlock(
    val movements: List<KbMovementLabel>,
    val circuits: List<SetCell>,
)

/**
 * One row in the strength block: a movement, its weight + target reps, and its set
 * buttons — Prime, Warm-up, then three Work sets.
 *
 * [warmup] is nullable so the Log can reuse this mapping for historical sessions that
 * predate the warm-up set; live Tracker rows always carry one.
 */
data class StrengthMovementRow(
    val exercise: Exercise,
    val weightKg: Double,
    val targetReps: Int,
    val prime: SetCell,
    val warmup: SetCell?,
    val working: List<SetCell>,
    /**
     * Physiological load for an ASSISTED movement (bodyweight − pin), for the
     * "Effective: X kg" subtext. Null for traditional lifts or when no bodyweight
     * is known yet.
     */
    val effectiveLoadKg: Double? = null,
)

/** Which part of the session the Tracker is currently showing. */
enum class TrackerPhase { MAIN, AUX }

sealed interface TrackerUiState {
    data object Loading : TrackerUiState

    data class Ready(
        val date: LocalDate,
        val split: Split,
        val kbWeightKg: Double,
        val kbBlock: KbBlock,
        val strength: List<StrengthMovementRow>,
        /** All KB + main strength buttons are resolved (drives the aux prompt). */
        val mainResolved: Boolean,
        val kbBump: KbBumpState?,
        val isFirstSession: Boolean = false,
        /** MAIN shows KB + strength; AUX shows the auxiliary movements. */
        val phase: TrackerPhase = TrackerPhase.MAIN,
        /** Auxiliary movement rows; non-empty only once aux work has started. */
        val aux: List<StrengthMovementRow> = emptyList(),
        /** Main is done and the user hasn't yet chosen whether to do aux work. */
        val showAuxPrompt: Boolean = false,
        /** Everything the session needs is resolved — show the feedback sheet. */
        val feedbackReady: Boolean = false,
        /** Weekly bodyweight check-in is due (assisted movement today + entry stale). */
        val bodyweightPrompt: Boolean = false,
        /** Latest known bodyweight, used to prefill the check-in dialog. */
        val currentBodyweightKg: Double? = null,
    ) : TrackerUiState
}

/** State of the 3-month KB ladder-bump prompt (spec §9.3). */
data class KbBumpState(
    val currentKg: Double,
    val targetKg: Double,
)
