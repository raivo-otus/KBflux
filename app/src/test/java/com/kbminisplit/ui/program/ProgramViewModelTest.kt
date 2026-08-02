package com.kbminisplit.ui.program

import com.google.common.truth.Truth.assertThat
import com.kbminisplit.data.repository.ProgramRepository
import com.kbminisplit.domain.model.GroupKind
import com.kbminisplit.domain.progression.day
import com.kbminisplit.domain.progression.item
import com.kbminisplit.domain.progression.program
import com.kbminisplit.domain.progression.standardGroup
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgramViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val bench = item(1, "Bench Press", currentWeightKg = 60.0)
    private val group = standardGroup(10, name = "Main", items = listOf(bench))
    private val programFlow = MutableStateFlow(
        program(day(1, "A", "Push", groups = listOf(group))),
    )

    private lateinit var repository: ProgramRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true) {
            every { observeProgram() } returns programFlow
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = ProgramViewModel(repository)

    @Test
    fun `the first day is expanded on load`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            assertThat(vm.state.value.isLoading).isFalse()
            assertThat(vm.state.value.expandedDayIds).containsExactly(1L)
        }
    }

    @Test
    fun `toggling a day expands and collapses it`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onToggleDay(1L)
            assertThat(vm.state.value.expandedDayIds).isEmpty()

            vm.onToggleDay(1L)
            assertThat(vm.state.value.expandedDayIds).containsExactly(1L)
        }
    }

    @Test
    fun `adding a day names it after the current count`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onAddDay()
            advanceUntilIdle()

            coVerify { repository.addDay("Day 2") }
        }
    }

    @Test
    fun `editing an item preloads its current values`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onEditItem(bench, group)

            val draft = vm.state.value.editingItem!!
            assertThat(draft.itemId).isEqualTo(1L)
            assertThat(draft.name).isEqualTo("Bench Press")
            assertThat(draft.weight).isEqualTo("60")
            assertThat(draft.minReps).isEqualTo("8")
            assertThat(draft.maxReps).isEqualTo("12")
            assertThat(draft.isValid).isTrue()
        }
    }

    @Test
    fun `saving an edited item writes every field through`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onEditItem(bench, group)

            vm.onItemDraftChange(
                vm.state.value.editingItem!!.copy(
                    name = "Incline Bench",
                    sets = "4",
                    minReps = "5",
                    maxReps = "8",
                    weight = "70",
                    weightStep = "5",
                    leadInSets = 1,
                    isAssisted = false,
                ),
            )
            vm.onSaveItem()
            advanceUntilIdle()

            coVerify {
                repository.updateItem(
                    itemId = 1L,
                    name = "Incline Bench",
                    sets = 4,
                    minReps = 5,
                    maxReps = 8,
                    leadInSets = 1,
                    weightStepKg = 5.0,
                    isAssisted = false,
                    isPerSide = false,
                    currentWeightKg = 70.0,
                )
            }
            assertThat(vm.state.value.editingItem).isNull()
        }
    }

    @Test
    fun `an inverted rep range is rejected`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onEditItem(bench, group)

            vm.onItemDraftChange(vm.state.value.editingItem!!.copy(minReps = "12", maxReps = "8"))

            assertThat(vm.state.value.editingItem!!.isRepRangeValid).isFalse()
            assertThat(vm.state.value.editingItem!!.isValid).isFalse()
        }
    }

    @Test
    fun `a blank name is rejected`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onEditItem(bench, group)

            vm.onItemDraftChange(vm.state.value.editingItem!!.copy(name = "  "))

            assertThat(vm.state.value.editingItem!!.isValid).isFalse()
        }
    }

    @Test
    fun `saving an invalid draft writes nothing`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onEditItem(bench, group)
            vm.onItemDraftChange(vm.state.value.editingItem!!.copy(sets = "not a number"))

            vm.onSaveItem()
            advanceUntilIdle()

            coVerify(exactly = 0) { repository.updateItem(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
            assertThat(vm.state.value.editingItem).isNotNull()
        }
    }

    @Test
    fun `adding a movement to a circuit hides the set-based fields`() {
        runTest(testDispatcher) {
            val circuit = com.kbminisplit.domain.progression.circuitGroup(20)
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onAddItem(circuit)

            val draft = vm.state.value.editingItem!!
            assertThat(draft.isCircuitItem).isTrue()
            assertThat(draft.leadInSets).isEqualTo(0)
            assertThat(draft.setsValue).isEqualTo(0)
        }
    }

    @Test
    fun `adding a new movement inserts rather than updates`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onAddItem(group)
            vm.onItemDraftChange(vm.state.value.editingItem!!.copy(name = "Face Pull", weight = "20"))

            vm.onSaveItem()
            advanceUntilIdle()

            coVerify {
                repository.addItem(
                    groupId = 10L,
                    name = "Face Pull",
                    sets = 3,
                    minReps = 8,
                    maxReps = 12,
                    leadInSets = 2,
                    weightStepKg = 2.5,
                    isAssisted = false,
                    isPerSide = false,
                    currentWeightKg = 20.0,
                )
            }
        }
    }

    @Test
    fun `group edits carry rotation and reveal switches`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onEditGroup(group)

            vm.onGroupDraftChange(
                vm.state.value.editingGroup!!.copy(
                    name = "Accessories",
                    rotates = false,
                    isDeferred = true,
                ),
            )
            vm.onSaveGroup()
            advanceUntilIdle()

            coVerify {
                repository.updateGroup(
                    groupId = 10L,
                    name = "Accessories",
                    rotates = false,
                    isDeferred = true,
                    rounds = 0,
                    usesLadder = false,
                )
            }
        }
    }

    @Test
    fun `adding a group picks a sensible default name per kind`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onAddGroup(1L, GroupKind.CIRCUIT)
            vm.onAddGroup(1L, GroupKind.STANDARD)
            advanceUntilIdle()

            coVerify { repository.addGroup(1L, "Circuit", GroupKind.CIRCUIT) }
            coVerify { repository.addGroup(1L, "Block", GroupKind.STANDARD) }
        }
    }

    @Test
    fun `reordering delegates straight to the repository`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()

            vm.onMoveDay(1L, 1)
            vm.onMoveGroup(10L, -1)
            vm.onMoveItem(1L, 1)
            advanceUntilIdle()

            coVerify { repository.moveDay(1L, 1) }
            coVerify { repository.moveGroup(10L, -1) }
            coVerify { repository.moveItem(1L, 1) }
        }
    }

    @Test
    fun `deleting an item closes the editor`() {
        runTest(testDispatcher) {
            val vm = newViewModel()
            advanceUntilIdle()
            vm.onEditItem(bench, group)

            vm.onDeleteItem(1L)
            advanceUntilIdle()

            coVerify { repository.deleteItem(1L) }
            assertThat(vm.state.value.editingItem).isNull()
        }
    }
}
