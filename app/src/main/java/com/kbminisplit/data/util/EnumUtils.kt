package com.kbminisplit.data.util

/**
 * Generic extension function for safe enum parsing from strings.
 */
inline fun <reified T : Enum<T>> String.toEnumOrDefault(default: T): T {
    return try {
        java.lang.Enum.valueOf(T::class.java, this)
    } catch (e: IllegalArgumentException) {
        default
    }
}
