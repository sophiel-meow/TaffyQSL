package moe.zzy040330.taffyqsl.data.lotw

data class LotwQueryParams(
    val qsoQsl: String = "yes",         // "yes" or "no"
    val qsoQslSince: String? = null,    // YYYY-MM-DD (when qsoQsl="yes")
    val qsoQsoRxSince: String? = null,  // YYYY-MM-DD (when qsoQsl="no")
    val qsoOwnCall: String? = null,
    val qsoCallsign: String? = null,
    val qsoMode: String? = null,
    val qsoBand: String? = null,
    val qsoDxcc: String? = null,
    val qsoStartDate: String? = null,
    val qsoEndDate: String? = null,
    val qsoQslDetail: Boolean = false,  // adds qso_qsldetail=yes; forces qsoQsl="yes"
)
