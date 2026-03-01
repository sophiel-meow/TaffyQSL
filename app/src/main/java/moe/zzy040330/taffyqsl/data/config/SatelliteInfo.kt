package moe.zzy040330.taffyqsl.data.config

data class SatelliteInfo(
    val name: String,
    val aliases: List<String>,
    val downlinkFreqMhz: Double,
    val uplinkFreqMhz: Double,
    val mode: String
)
