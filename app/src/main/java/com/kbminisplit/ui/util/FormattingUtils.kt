package com.kbminisplit.ui.util

/**
 * Shared formatting for KG values.
 */
fun formatKg(value: Double): String {
    val rounded = (value * 10).toLong() / 10.0
    return if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString()
    else rounded.toString()
}
