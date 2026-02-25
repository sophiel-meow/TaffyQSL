package moe.zzy040330.taffyqsl.domain.model

data class StationLocation(
    val id: Long = 0,
    val name: String,
    val callSign: String,
    val dxcc: Int,
    val dxccName: String = "",
    val grid: String = "",
    val state: String = "",             // state/province value
    val stateFieldName: String = "",    // GAbbi field name: US_STATE, CN_PROVINCE, etc.
    val county: String = "",            // county/city/municipality value
    val countyFieldName: String = "",   // GAbbi field name: US_COUNTY, JA_CITY_GUN_KU, etc.
    val park: String = "",              // park value
    val parkFieldName: String = "",     // GAbbi field name: US_PARK, CA_US_PARK, DX_US_PARK
    val cqZone: String = "",
    val ituZone: String = "",
    val iota: String = ""
)
