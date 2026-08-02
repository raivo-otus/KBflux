package com.kbminisplit.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType

/**
 * The app's one form primitive: a numeric text field with the right keyboard.
 *
 * Typing only ever happens outside a workout — in the Program editor or a weight
 * dialog — so this is deliberately plain.
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
    isError: Boolean = false,
    tag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier
            .fillMaxWidth()
            .let { if (tag != null) it.testTag(tag) else it },
    )
}

/** Parses a user-typed number, tolerating a comma decimal separator. */
fun String.toWeightOrNull(): Double? {
    val cleaned = trim().replace(',', '.')
    if (cleaned.isEmpty()) return null
    return cleaned.toDoubleOrNull()?.takeIf { it >= 0.0 && it.isFinite() }
}

fun String.toCountOrNull(range: IntRange): Int? =
    trim().toIntOrNull()?.takeIf { it in range }
