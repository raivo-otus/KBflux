package com.kbminisplit.data.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.kbminisplit.domain.model.ExerciseCatalog
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExerciseSeedTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = buildInMemoryDatabase(InstrumentationRegistry.getInstrumentation().context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun seed_populates_full_catalog() {
        runBlocking {
            seedExerciseCatalog(db)

            val rows = db.exerciseDao().getAll()

            assertThat(rows.map { it.slug }).containsExactlyElementsIn(
                ExerciseCatalog.all.map { it.slug },
            )
        }
    }

    @Test
    fun seed_is_idempotent() {
        runBlocking {
            seedExerciseCatalog(db)
            seedExerciseCatalog(db)
            seedExerciseCatalog(db)

            val rows = db.exerciseDao().getAll()

            assertThat(rows).hasSize(ExerciseCatalog.all.size)
        }
    }

    @Test
    fun seeded_row_preserves_step_and_per_side_flags() {
        runBlocking {
            seedExerciseCatalog(db)

            val swings = db.exerciseDao().getBySlug(ExerciseCatalog.Swings.slug)
            val cleanAndPress = db.exerciseDao().getBySlug(ExerciseCatalog.CleanAndPress.slug)
            val pulldown = db.exerciseDao().getBySlug(ExerciseCatalog.LatPulldown.slug)

            assertThat(swings?.weightStepKg).isEqualTo(2.0)
            assertThat(swings?.isPerSide).isFalse()
            assertThat(cleanAndPress?.isPerSide).isTrue()
            assertThat(pulldown?.weightStepKg).isEqualTo(2.5)
            assertThat(pulldown?.category).isEqualTo("A")
        }
    }
}
