package moe.zzy040330.taffyqsl.data.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface StationDao {
    @Query("SELECT * FROM stations ORDER BY name ASC")
    fun getAllLive(): LiveData<List<StationEntity>>

    @Query("SELECT * FROM stations ORDER BY name ASC")
    suspend fun getAll(): List<StationEntity>

    @Query("SELECT * FROM stations WHERE id = :id")
    suspend fun getById(id: Long): StationEntity?

    @Query("SELECT * FROM stations WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): StationEntity?

    @Query("SELECT * FROM stations WHERE callSign = :callSign")
    suspend fun getByCallSign(callSign: String): List<StationEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(station: StationEntity): Long

    @Update
    suspend fun update(station: StationEntity)

    @Delete
    suspend fun delete(station: StationEntity)

    @Query("DELETE FROM stations WHERE id = :id")
    suspend fun deleteById(id: Long)
}
