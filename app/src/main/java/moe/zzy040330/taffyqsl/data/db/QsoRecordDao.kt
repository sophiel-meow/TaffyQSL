package moe.zzy040330.taffyqsl.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QsoRecordDao {

    @Query("SELECT * FROM qso_records WHERE fileId = :fileId ORDER BY dateStr ASC, timeStr ASC")
    fun getByFileFlow(fileId: String): Flow<List<QsoRecordEntity>>

    @Query("SELECT * FROM qso_records WHERE fileId = :fileId ORDER BY dateStr ASC, timeStr ASC")
    suspend fun getByFile(fileId: String): List<QsoRecordEntity>

    @Query("SELECT * FROM qso_records WHERE id = :id")
    suspend fun getById(id: Long): QsoRecordEntity?

    @Insert
    suspend fun insert(record: QsoRecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<QsoRecordEntity>)

    @Update
    suspend fun update(record: QsoRecordEntity)

    @Query("DELETE FROM qso_records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM qso_records WHERE fileId = :fileId")
    suspend fun deleteByFile(fileId: String)

    @Query("SELECT COUNT(*) FROM qso_records WHERE fileId = :fileId")
    suspend fun countByFile(fileId: String): Int
}
