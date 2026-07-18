package com.kbminisplit.ui.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormattingUtilsTest {

    @Test
    fun `formatElapsed renders zero`() {
        assertThat(formatElapsed(0)).isEqualTo("0:00")
    }

    @Test
    fun `formatElapsed pads seconds under ten`() {
        assertThat(formatElapsed(725)).isEqualTo("12:05")
    }

    @Test
    fun `formatElapsed renders sub-minute values`() {
        assertThat(formatElapsed(47)).isEqualTo("0:47")
    }

    @Test
    fun `formatElapsed rolls seconds into minutes`() {
        assertThat(formatElapsed(89)).isEqualTo("1:29")
        assertThat(formatElapsed(90)).isEqualTo("1:30")
        assertThat(formatElapsed(180)).isEqualTo("3:00")
    }

    @Test
    fun `formatElapsed clamps negative input`() {
        assertThat(formatElapsed(-5)).isEqualTo("0:00")
    }
}
