package moe.zzy040330.taffyqsl.data.config

data class Band(
    val name: String,       // TQSL name e.g. "40M"
    val spectrum: String,   // "HF", "VHF", "UHF"
    val freqLow: Double,    // MHz
    val freqHigh: Double    // MHz
) {
    /**
     * Format string for display like "7-7.3 MHz" with units.
     */
    fun freqRangeDisplay(): String {
        fun fmt(v: Double) = "%.4f".format(v).trimEnd('0').trimEnd('.')
        return if (spectrum == "HF") {
            if (freqLow < 1.0) {
                // sub-MHz HF band
                "${fmt(freqLow * 1000)}-${fmt(freqHigh * 1000)} kHz"
            } else {
                "${fmt(freqLow)}-${fmt(freqHigh)} MHz"
            }
        } else {
            // UHF / VHF
            if (freqLow < 1000.0) {
                "${fmt(freqLow)}-${fmt(freqHigh)} MHz"
            } else {
                "${fmt(freqLow / 1000.0)}-${fmt(freqHigh / 1000.0)} GHz"
            }
        }
    }
}

data class Mode(
    val name: String,           // TQSL mode name, e.g. "FT8"
    val group: String,          // Mode group: "CW", "PHONE", "DATA", "IMAGE"
    val adifMode: String,       // ADIF MODE field value (same as name for TQSL modes)
    val adifSubmode: String = ""
)

data class DxccEntity(
    val arrlId: Int,
    val name: String,
    val deleted: Boolean = false,
    val zoneMap: String = "",
    // Primary subdivision field ID for this entity (e.g. "US_STATE", "CA_PROVINCE")
    val stateFieldId: String = "",
)

data class StateValue(
    val abbrev: String,
    val name: String,
    val cqZone: String = "",  // CQ zone, comma-separated e.g. "4" or "3,4"
    val ituZone: String = ""  // ITU zone, comma-separated
)

/**
 * A group of subdivision enum values that apply when the parent field
 * (DXCC or state/province) has the given [dependency] value.
 * [dependency] is null for fields that have no DXCC-level grouping (flat lists).
 */
data class SubdivisionGroup(
    val dependency: String?,  // DXCC arrlId as string, state/province abbrev, or null for flat lists
    val values: List<StateValue>
)

/**
 * A subdivision/location field (state, province, oblast, prefecture, etc.)
 * // as defined in config.xml <locfields>.
 */
data class SubdivisionField(
    val fieldId: String,        // e.g. "US_STATE", "CA_PROVINCE", "RU_OBLAST"
    val label: String,          // Human-readable label e.g. "State", "Province"
    val dependsOn: String?,     // Parent field: "DXCC", "US_STATE", etc.
    val groups: List<SubdivisionGroup>
)

data class Satellite(
    val name: String,
    val description: String,
    val startDate: String = "",
    val endDate: String = ""
)

data class PropMode(
    val name: String,
    val description: String
)
