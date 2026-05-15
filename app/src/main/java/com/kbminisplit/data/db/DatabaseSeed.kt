package com.kbminisplit.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kbminisplit.data.entity.ExerciseEntity
import com.kbminisplit.data.mapper.toEntity
import com.kbminisplit.domain.model.ExerciseCatalog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Seeds the static exercise catalog. Idempotent — [ExerciseDao.insertAll] uses
 * `@Upsert` so re-running on an already-seeded DB is safe and backfills any
 * changes or new rows added to [ExerciseCatalog] between releases.
 */
suspend fun seedExerciseCatalog(database: AppDatabase) {
    val dao = database.exerciseDao()
    val entities: List<ExerciseEntity> = ExerciseCatalog.all.map { it.toEntity() }
    dao.insertAll(entities)
}

class DatabaseSeedCallback(
    private val database: () -> AppDatabase,
    private val ioScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        ioScope.launch { seedExerciseCatalog(database()) }
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        ioScope.launch { seedExerciseCatalog(database()) }
    }
}
