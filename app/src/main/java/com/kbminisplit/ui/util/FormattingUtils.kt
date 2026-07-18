package com.kbminisplit.ui.util

/**
 * Shared formatting for KG values.
 */
fun formatKg(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else rounded.toString()
}

/**
 * Elapsed time as `m:ss` (`0:47`, `1:30`, `12:05`). Minutes are uncapped;
 * negative input clamps to `0:00`.
 */
fun formatElapsed(totalSeconds: Long): String {
    val clamped = totalSeconds.coerceAtLeast(0)
    val minutes = clamped / 60
    val seconds = clamped % 60
    return "%d:%02d".format(minutes, seconds)
}
