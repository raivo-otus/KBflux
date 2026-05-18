package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.Exercise
import com.kbminisplit.domain.model.ExerciseCatalog
import com.kbminisplit.domain.model.Session
import com.kbminisplit.domain.model.Split

/**
 * Strength movement order for today's split, alternating each cycle of that split.
 *
 * The canonical order is [ExerciseCatalog.strengthForSplit]. The first time the user
 * does a given split, today's order matches canonical. Every subsequent appearance
 * of that split flips the pair.
 *
 * `history` is expected in chronological order; only `Session.split` is consulted.
 */
fun movementOrder(history: List<Session>, split: Split): Pair<Exercise, Exercise> {
    val canonical = ExerciseCatalog.strengthForSplit(split)
    // Only need the count of past sessions of this split to decide order.
    val pastOfThisSplit = history.asReversed().asSequence()
        .filter { it.split == split }
        .count()
    return if (pastOfThisSplit % 2 == 0) canonical else canonical.second to canonical.first
}
