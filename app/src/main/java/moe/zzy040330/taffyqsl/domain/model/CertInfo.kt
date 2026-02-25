package moe.zzy040330.taffyqsl.domain.model

import java.time.LocalDate

data class CertInfo(
    val alias: String,      // SHA-256 fingerprint hex (also for keystore alias)
    val callSign: String,
    val name: String,       // CN from subject
    val dxccEntity: Int,
    val dxccEntityName: String = "",
    val qsoNotBefore: LocalDate?,
    val qsoNotAfter: LocalDate?,
    val notBefore: LocalDate?,
    val notAfter: LocalDate?,
    val issuerOrg: String,
    val serialNumber: String,
    val email: String,
    val fingerprint: String
) {
    val isExpired: Boolean
        get() = notAfter?.isBefore(LocalDate.now()) == true

    val isQsoExpired: Boolean
        get() = qsoNotAfter?.isBefore(LocalDate.now()) == true

    val statusLabel: String
        get() = when {
            isExpired -> "Expired"
            isQsoExpired -> "QSO Range Expired"
            else -> "Valid"
        }

    fun coversQsoDate(date: LocalDate): Boolean {
        val after = qsoNotBefore?.isAfter(date) == true
        val before = qsoNotAfter?.isBefore(date) == true
        return !(after || before)
    }
}
