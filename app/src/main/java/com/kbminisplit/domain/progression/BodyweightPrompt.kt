package com.kbminisplit.domain.progression

import java.time.Duration

/** A bodyweight check-in is due weekly, so any entry older than this is stale. */
private val BODYWEIGHT_MAX_AGE = Duration.ofDays(7)

/**
 * Whether the weekly bodyweight check-in is due: true when no bodyweight has ever
 * been logged, or the last entry is at least 7 days old. Effective load for
 * assisted movements depends on a current bodyweight, so the Tracker nudges for
 * one when it goes stale.
 */
fun isBodyweightStale(loggedAtMillis: Long?, nowMillis: Long): Boolean {
    if (loggedAtMillis == null) return true
    return nowMillis - loggedAtMillis >= BODYWEIGHT_MAX_AGE.toMillis()
}
