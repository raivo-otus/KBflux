package com.kbminisplit.domain.progression

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class GroupRotationTest {

    private val start = LocalDate.of(2026, 5, 1)

    private fun namesAfter(cycles: Int, group: com.kbminisplit.domain.model.ProgramGroup) =
        rotatedItems(group, cycles).map { it.name }

    @Test
    fun `a pair alternates every cycle, matching the old two-movement flip`() {
        val group = standardGroup(
            1,
            items = listOf(item(1, "Pulldown"), item(2, "Row")),
        )

        assertThat(namesAfter(0, group)).containsExactly("Pulldown", "Row").inOrder()
        assertThat(namesAfter(1, group)).containsExactly("Row", "Pulldown").inOrder()
        assertThat(namesAfter(2, group)).containsExactly("Pulldown", "Row").inOrder()
    }

    @Test
    fun `three movements cycle through every starting position`() {
        val group = standardGroup(
            1,
            items = listOf(item(1, "Pulldown"), item(2, "Row"), item(3, "Face Pull")),
        )

        assertThat(namesAfter(0, group)).containsExactly("Pulldown", "Row", "Face Pull").inOrder()
        assertThat(namesAfter(1, group)).containsExactly("Row", "Face Pull", "Pulldown").inOrder()
        assertThat(namesAfter(2, group)).containsExactly("Face Pull", "Pulldown", "Row").inOrder()
        assertThat(namesAfter(3, group)).containsExactly("Pulldown", "Row", "Face Pull").inOrder()
    }

    @Test
    fun `a group that does not rotate keeps program order forever`() {
        val group = standardGroup(
            1,
            rotates = false,
            items = listOf(item(1, "Squat"), item(2, "RDL")),
        )

        assertThat(namesAfter(0, group)).containsExactly("Squat", "RDL").inOrder()
        assertThat(namesAfter(1, group)).containsExactly("Squat", "RDL").inOrder()
        assertThat(namesAfter(7, group)).containsExactly("Squat", "RDL").inOrder()
    }

    @Test
    fun `a single movement is unaffected by rotation`() {
        val group = standardGroup(1, items = listOf(item(1, "Squat")))

        assertThat(namesAfter(0, group)).containsExactly("Squat")
        assertThat(namesAfter(5, group)).containsExactly("Squat")
    }

    @Test
    fun `an empty group stays empty`() {
        val group = standardGroup(1, items = emptyList())

        assertThat(rotatedItems(group, 3)).isEmpty()
    }

    @Test
    fun `rotation never drops or duplicates a movement`() {
        val group = standardGroup(
            1,
            items = listOf(item(1, "A"), item(2, "B"), item(3, "C"), item(4, "D")),
        )

        repeat(9) { cycle ->
            assertThat(namesAfter(cycle, group)).containsExactly("A", "B", "C", "D")
        }
    }

    @Test
    fun `cycle count only counts sessions of that day`() {
        val history = listOf(
            session(start, "A"),
            session(start.plusDays(1), "B"),
            session(start.plusDays(2), "A"),
            session(start.plusDays(3), "C"),
        )

        assertThat(dayCycleCount(history, "A")).isEqualTo(2)
        assertThat(dayCycleCount(history, "B")).isEqualTo(1)
        assertThat(dayCycleCount(history, "D")).isEqualTo(0)
    }

    @Test
    fun `resolveDay rotates every group and keeps group order`() {
        val trainingDay = day(
            1,
            "A",
            groups = listOf(
                circuitGroup(10, items = listOf(item(1, "Swings"))),
                standardGroup(11, name = "Main", items = listOf(item(2, "Bench"), item(3, "Dips"))),
            ),
        )

        val resolved = resolveDay(trainingDay, cycleCount = 1)

        assertThat(resolved.groups.map { it.group.name })
            .containsExactly("Kettlebell flow", "Main").inOrder()
        assertThat(resolved.groups[1].items.map { it.name })
            .containsExactly("Dips", "Bench").inOrder()
    }
}
