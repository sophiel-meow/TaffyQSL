package moe.zzy040330.taffyqsl.data.db

import androidx.room.*

@Dao
interface DuplicateQsoDao {
    @Query("SELECT COUNT(*) FROM duplicate_qsos WHERE `key` = :key")
    suspend fun exists(key: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(qso: DuplicateQsoEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(qsos: List<DuplicateQsoEntity>)

    @Query("DELETE FROM duplicate_qsos WHERE `key` IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    @Query("SELECT COUNT(*) FROM duplicate_qsos")
    suspend fun count(): Int
}
