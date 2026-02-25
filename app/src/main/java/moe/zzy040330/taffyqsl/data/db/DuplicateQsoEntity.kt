package moe.zzy040330.taffyqsl.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks QSOs that have already been signed/uploaded to prevent duplicates.
 * Key is a canonical string: callsign + band + mode + date + time + certAlias + stationName
 */
@Entity(tableName = "duplicate_qsos")
data class DuplicateQsoEntity(
    @PrimaryKey val key: String,
    val callsign: String,
    val band: String,
    val mode: String,
    val date: String,       // YYYYMMDD
    val time: String,       // HHMMSS
    val certAlias: String,
    val stationName: String,
    val signedAt: Long = System.currentTimeMillis()
)
