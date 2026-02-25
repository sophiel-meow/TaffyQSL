package moe.zzy040330.taffyqsl.domain.model

import java.time.LocalDate
import java.time.LocalTime

data class QsoRecord(
    val id: Long = 0,
    val callsign: String,
    val band: String,           // TQSL band name e.g. "40M"
    val mode: String,           // TQSL mode e.g. "CW", "FT8"
    val submode: String = "",
    val date: LocalDate,
    val time: LocalTime,
    val freq: String = "",      // MHz
    val rxFreq: String = "",
    val rxBand: String = "",
    val propMode: String = "",
    val satName: String = "",
    val doNotSign: Boolean = false
)
