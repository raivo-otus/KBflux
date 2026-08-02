package com.kbminisplit.domain.progression

import com.kbminisplit.domain.model.ProgramItem

/**
 * The only two ways a working weight ever moves.
 *
 * Both honour the movement's mechanic: for an assisted movement the logged number
 * is machine help, so getting stronger *lowers* it and an easier session *raises*
 * it. Neither direction goes below zero — for an assisted movement that floor is
 * unassisted bodyweight, and there is nowhere further to progress.
 */

/** Next weight up, offered by the bump chip once every working set is completed. */
fun bumpedWeightKg(currentKg: Double, stepKg: Double, isAssisted: Boolean): Double =
    if (isAssisted) (currentKg - stepKg).coerceAtLeast(0.0) else currentKg + stepKg

/** One step easier, applied to every movement when a rest week is accepted. */
fun deloadedWeightKg(currentKg: Double, stepKg: Double, isAssisted: Boolean): Double =
    if (isAssisted) currentKg + stepKg else (currentKg - stepKg).coerceAtLeast(0.0)

fun bumpedWeightKg(item: ProgramItem): Double =
    bumpedWeightKg(item.currentWeightKg, item.weightStepKg, item.isAssisted)

fun deloadedWeightKg(item: ProgramItem): Double =
    deloadedWeightKg(item.currentWeightKg, item.weightStepKg, item.isAssisted)

/**
 * False once an assisted movement has reached zero assistance, or a traditional
 * one has a zero step — there is no bump left to offer, so no chip is shown.
 */
fun canBump(item: ProgramItem): Boolean = bumpedWeightKg(item) != item.currentWeightKg
