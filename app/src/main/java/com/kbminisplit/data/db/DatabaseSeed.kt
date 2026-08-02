package com.kbminisplit.data.db

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kbminisplit.data.entity.ExerciseEntity
import com.kbminisplit.data.entity.ProgramDayEntity
import com.kbminisplit.data.entity.ProgramGroupEntity
import com.kbminisplit.data.entity.ProgramItemEntity
import com.kbminisplit.domain.model.GroupKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Backfills the exercise registry. Runs on every database open and only ever
 * inserts missing rows, so a movement the user renamed keeps its name.
 */
suspend fun seedExerciseRegistry(database: AppDatabase) {
    database.exerciseDao().insertAll(
        REGISTRY_SEED.map { (slug, name) -> ExerciseEntity(slug = slug, displayName = name) },
    )
}

/**
 * Writes [DEFAULT_PROGRAM] the first time the app opens a database without one —
 * a fresh install, or an existing install upgrading past the migration that added
 * the program tables.
 *
 * Upgrading users must land on exactly the program they were already following,
 * so the seed carries over what the old hardcoded engine derived from settings:
 * their onboarded starting weights, their kettlebell size, their rep ceiling, and
 * how long they have been on that kettlebell (which anchors the ladder prompt).
 *
 * Guarded on the program being empty and run in one transaction, so it can never
 * half-apply or overwrite a program the user has edited.
 */
suspend fun seedDefaultProgram(database: AppDatabase, nowMillis: Long) {
    database.withTransaction {
        val programDao = database.programDao()
        if (programDao.dayCount() > 0) return@withTransaction

        val settingsDao = database.settingsDao()
        val settings = settingsDao.get()
        val carriedWeights = settingsDao.getStartingWeights()
            .associate { it.exerciseSlug to it.weightKg }
        val circuitWeightKg = settings?.kbWeightKg ?: DEFAULT_CIRCUIT_WEIGHT_KG
        val maxReps = settings?.standardMaxReps ?: DEFAULT_MAX_REPS
        val ladderAnchor = ladderAnchorMillis(database, circuitWeightKg, nowMillis)

        DEFAULT_PROGRAM.forEachIndexed { dayIndex, day ->
            val dayId = programDao.insertDay(
                ProgramDayEntity(dayKey = day.key, name = day.name, position = dayIndex),
            )
            day.groups.forEachIndexed { groupIndex, group ->
                val isCircuit = group.kind == GroupKind.CIRCUIT
                val groupId = programDao.insertGroup(
                    ProgramGroupEntity(
                        dayId = dayId,
                        name = group.name,
                        kind = group.kind.name,
                        position = groupIndex,
                        rotates = group.rotates,
                        isDeferred = group.isDeferred,
                        rounds = group.rounds,
                        circuitSlug = group.circuitSlug,
                        weightKg = if (isCircuit) circuitWeightKg else null,
                        usesLadder = group.usesLadder,
                        weightChangedAt = if (isCircuit) ladderAnchor else null,
                    ),
                )
                group.items.forEachIndexed { itemIndex, item ->
                    programDao.insertItem(
                        ProgramItemEntity(
                            groupId = groupId,
                            exerciseSlug = item.slug,
                            position = itemIndex,
                            sets = item.sets,
                            minReps = item.minReps,
                            // The circuit's rep labels are fixed; only the graded
                            // lifts follow the user's configured rep ceiling.
                            maxReps = if (isCircuit) item.maxReps else maxReps,
                            leadInSets = item.leadInSets,
                            weightStepKg = item.weightStepKg,
                            isAssisted = item.isAssisted,
                            isPerSide = item.isPerSide,
                            currentWeightKg = if (isCircuit) {
                                circuitWeightKg
                            } else {
                                carriedWeights[item.slug] ?: item.weightKg
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * When the user's kettlebell last changed size, used to anchor the three-month
 * ladder prompt. Derived from the trailing run of sessions logged at the current
 * weight, so an upgrading user doesn't have their ladder clock reset to zero.
 */
private suspend fun ladderAnchorMillis(
    database: AppDatabase,
    circuitWeightKg: Double,
    nowMillis: Long,
): Long {
    val history = database.sessionDao().getAll()
    val runStart = history
        .takeLastWhile { it.circuitWeightKg == circuitWeightKg }
        .firstOrNull()
        ?: return nowMillis
    return LocalDate.parse(runStart.date)
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

class DatabaseSeedCallback(
    private val database: () -> AppDatabase,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seed()
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        seed()
    }

    private fun seed() {
        ioScope.launch {
            val db = database()
            // Registry first: program items have a foreign key into it.
            seedExerciseRegistry(db)
            seedDefaultProgram(db, nowMillis())
        }
    }
}
