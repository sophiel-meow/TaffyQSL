package moe.zzy040330.taffyqsl.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        StationEntity::class,
        DuplicateQsoEntity::class,
        QsoFileEntity::class,
        QsoRecordEntity::class
    ],
    version = 1,
    exportSchema = false
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun stationDao(): StationDao
    abstract fun duplicateQsoDao(): DuplicateQsoDao
    abstract fun qsoFileDao(): QsoFileDao
    abstract fun qsoRecordDao(): QsoRecordDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "taffyqsl.db"
                ).build().also { instance = it }
            }
    }
}
