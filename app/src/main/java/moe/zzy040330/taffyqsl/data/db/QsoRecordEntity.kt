package moe.zzy040330.taffyqsl.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import moe.zzy040330.taffyqsl.domain.model.QsoRecord
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Entity(
    tableName = "qso_records",
    foreignKeys = [ForeignKey(
        entity = QsoFileEntity::class,
        parentColumns = ["fileName"],
        childColumns = ["fileId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("fileId")]
)
data class QsoRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileId: String,
    val callsign: String,
    val bandName: String,
    val modeName: String,
    val submode: String = "",
    val dateStr: String,       // YYYYMMDD
    val timeStr: String,       // HHmmss
    val freq: String = "",
    val rxFreq: String = "",
    val rxBandName: String = "",
    val propMode: String = "",
    val satName: String = ""
)

fun QsoRecordEntity.toQsoRecord(): QsoRecord {
    val date = runCatching {
        LocalDate.parse(dateStr, DateTimeFormatter.BASIC_ISO_DATE)
    }.getOrElse { LocalDate.now() }

    val digits = timeStr.filter { it.isDigit() }.padEnd(6, '0').take(6)
    val time = runCatching {
        LocalTime.of(
            digits.substring(0, 2).toInt(),
            digits.substring(2, 4).toInt(),
            digits.substring(4, 6).toInt()
        )
    }.getOrDefault(LocalTime.MIDNIGHT)

    return QsoRecord(
        id = id,
        callsign = callsign,
        band = bandName,
        mode = modeName,
        submode = submode,
        date = date,
        time = time,
        freq = freq,
        rxFreq = rxFreq,
        rxBand = rxBandName,
        propMode = propMode,
        satName = satName
    )
}
