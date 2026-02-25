package moe.zzy040330.taffyqsl.domain

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.map
import moe.zzy040330.taffyqsl.data.db.AppDatabase
import moe.zzy040330.taffyqsl.data.db.toDomain
import moe.zzy040330.taffyqsl.data.db.toEntity
import moe.zzy040330.taffyqsl.domain.model.StationLocation

class StationRepository(context: Context) {

    private val dao = AppDatabase.getInstance(context).stationDao()

    val allStations: LiveData<List<StationLocation>> =
        dao.getAllLive().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<StationLocation> = dao.getAll().map { it.toDomain() }

    suspend fun getById(id: Long): StationLocation? = dao.getById(id)?.toDomain()

    suspend fun getByCallSign(callSign: String): List<StationLocation> =
        dao.getByCallSign(callSign).map { it.toDomain() }

    suspend fun save(station: StationLocation): Long {
        return if (station.id == 0L) {
            dao.insert(station.toEntity())
        } else {
            dao.update(station.toEntity())
            station.id
        }
    }

    suspend fun delete(station: StationLocation) {
        dao.deleteById(station.id)
    }

    suspend fun nameExists(name: String, excludeId: Long = -1): Boolean {
        val existing = dao.getByName(name)
        return existing != null && existing.id != excludeId
    }
}
