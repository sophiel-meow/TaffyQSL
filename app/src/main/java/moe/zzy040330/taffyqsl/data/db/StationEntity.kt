package moe.zzy040330.taffyqsl.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import moe.zzy040330.taffyqsl.domain.model.StationLocation

@Entity(tableName = "stations")
data class StationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val callSign: String,
    val dxcc: Int,
    val dxccName: String = "",
    val grid: String = "",
    val state: String = "",
    val stateFieldName: String = "",
    val county: String = "",
    val countyFieldName: String = "",
    val park: String = "",
    val parkFieldName: String = "",
    val cqZone: String = "",
    val ituZone: String = "",
    val iota: String = ""
)

fun StationEntity.toDomain() = StationLocation(
    id = id, name = name, callSign = callSign, dxcc = dxcc, dxccName = dxccName,
    grid = grid, state = state, stateFieldName = stateFieldName,
    county = county, countyFieldName = countyFieldName,
    park = park, parkFieldName = parkFieldName,
    cqZone = cqZone, ituZone = ituZone, iota = iota
)

fun StationLocation.toEntity() = StationEntity(
    id = id, name = name, callSign = callSign, dxcc = dxcc, dxccName = dxccName,
    grid = grid, state = state, stateFieldName = stateFieldName,
    county = county, countyFieldName = countyFieldName,
    park = park, parkFieldName = parkFieldName,
    cqZone = cqZone, ituZone = ituZone, iota = iota
)
