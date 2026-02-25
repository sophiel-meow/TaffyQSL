package moe.zzy040330.taffyqsl.data.db

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface QsoFileDao {
    @Query("SELECT * FROM qso_files ORDER BY createdAt DESC")
    fun getAllLive(): LiveData<List<QsoFileEntity>>

    @Query("SELECT * FROM qso_files ORDER BY createdAt DESC")
    suspend fun getAll(): List<QsoFileEntity>

    @Query("SELECT * FROM qso_files WHERE fileName = :fileName")
    fun getByNameFlow(fileName: String): Flow<QsoFileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: QsoFileEntity)

    @Update
    suspend fun update(file: QsoFileEntity)

    @Query("DELETE FROM qso_files WHERE fileName = :fileName")
    suspend fun deleteByName(fileName: String)

    @Query("UPDATE qso_files SET qsoCount = :count WHERE fileName = :fileName")
    suspend fun updateQsoCount(fileName: String, count: Int)

    @Query("UPDATE qso_files SET displayName = :displayName WHERE fileName = :fileName")
    suspend fun updateDisplayName(fileName: String, displayName: String)
}
