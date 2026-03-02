package moe.zzy040330.taffyqsl.data.parser

import moe.zzy040330.taffyqsl.domain.model.QsoRecord
import moe.zzy040330.taffyqsl.domain.model.StationLocation
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream

/**
 * Writes GABBI format output (.tq7 plain, .tq8 gzip compressed).
 *
 * GABBI is an ADIF-like format used by LoTW for signed log records.
 * Sigspec version: LOTW V2.0
 *
 * tSTATION sigspec fields (in order for signdata): AU_STATE, CA_PROVINCE, CA_US_PARK,
 * CN_PROVINCE, CQZ, DX_US_PARK, FI_KUNTA, GRIDSQUARE, IOTA, ITUZ, JA_CITY_GUN_KU,
 * JA_PREFECTURE, RU_OBLAST, US_COUNTY, US_PARK, US_STATE
 *
 * tCONTACT sigspec fields (in order for signdata): BAND, BAND_RX, CALL, FREQ,
 * FREQ_RX, MODE, PROP_MODE, QSO_DATE, QSO_TIME, SAT_NAME
 */
class GabbiWriter {

    companion object {

        const val IDENT = "TaffyQSL taffyqsl"

        // hard-code SIGN_LOTW_V2.0
        // from TrustedQSL: src/location.cpp:819-822, concatenated from sigspec
        const val SIGN_FIELD = "SIGN_LOTW_V2.0"

        // tSTATION sigspec field order for signdata (LOTW V2.0)
        val STATION_SIGSPEC_FIELDS = listOf(
            "AU_STATE", "CA_PROVINCE", "CA_US_PARK", "CN_PROVINCE", "CQZ",
            "DX_US_PARK", "FI_KUNTA", "GRIDSQUARE", "IOTA", "ITUZ",
            "JA_CITY_GUN_KU", "JA_PREFECTURE", "RU_OBLAST", "US_COUNTY",
            "US_PARK", "US_STATE"
        )

        // TrustedQSL date/time format (tqsl_convertDateToText / tqsl_convertTimeToText):
        //  date: yyyy-MM-dd  (ISO 8601 with dashes)
        //  time: HH:mm:ssZ  (with colons and UTC 'Z' suffix)
        private val DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE              // yyyy-MM-dd
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss'Z'")    // HH:mm:ssZ
    }

    /**
     * Build the station signdata prefix (from tSTATION sigspec fields).
     * Returns uppercase concatenated values.
     */
    fun buildStationSigndata(station: StationLocation): String {
        val fields = buildStationFieldMap(station)
        return STATION_SIGSPEC_FIELDS.mapNotNull { fieldId ->
            fields[fieldId]?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        }.joinToString("")
    }

    /**
     * Build map of GABBI field name to value for a station location.
     */
    fun buildStationFieldMap(station: StationLocation): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (station.grid.isNotEmpty()) map["GRIDSQUARE"] = station.grid
        if (station.cqZone.isNotEmpty()) map["CQZ"] = station.cqZone
        if (station.ituZone.isNotEmpty()) map["ITUZ"] = station.ituZone
        if (station.iota.isNotEmpty()) map["IOTA"] = station.iota
        if (station.state.isNotEmpty() && station.stateFieldName.isNotEmpty()) {
            map[station.stateFieldName] = station.state
        }
        if (station.county.isNotEmpty() && station.countyFieldName.isNotEmpty()) {
            map[station.countyFieldName] = station.county
        }
        if (station.park.isNotEmpty() && station.parkFieldName.isNotEmpty()) {
            map[station.parkFieldName] = station.park
        }
        return map
    }

    /**
     * Build the contact signdata for a QSO (station prefix + QSO fields), uppercase.
     */
    fun buildContactSigndata(stationSigndata: String, qso: QsoRecord): String {
        val sb = StringBuilder(stationSigndata)
        fun append(v: String) {
            if (v.isNotEmpty())
                sb.append(v.trim().uppercase())
        }
        append(qso.band)
        append(qso.rxBand)
        append(qso.callsign)
        append(qso.freq)
        append(qso.rxFreq)
        append(qso.mode)
        append(qso.propMode)
        append(qso.date.format(DATE_FORMAT))
        append(qso.time.format(TIME_FORMAT))
        append(qso.satName)
        return sb.toString()
    }

    /**
     * Write a full GAbbi tq7 document to a string.
     */
    fun buildGabbi(
        certAlias: String,
        certPemData: String,    // PEM base64 content without headers
        station: StationLocation,
        qsos: List<SignedQso>
    ): String {
        val sb = StringBuilder()

        // Ident
        sb.adifLine("Ident", IDENT)
        sb.append("\n")

        // tCERT
        sb.append("<Rec_Type:5>tCERT\n")
        sb.adifLine("CERT_UID", "1")
        sb.append("<CERTIFICATE:${certPemData.length}>")
        sb.append(certPemData)
        sb.append("<eor>\n\n")

        // tSTATION
        sb.append("<Rec_Type:8>tSTATION\n")
        sb.adifLine("STATION_UID", "1")
        sb.adifLine("CERT_UID", "1")
        sb.adifLine("CALL", station.callSign)
        sb.adifLine("DXCC", station.dxcc.toString())
        val stationFields = buildStationFieldMap(station)
        for ((fieldName, fieldValue) in stationFields) {
            sb.adifLine(fieldName, fieldValue)
        }
        sb.append("<eor>\n\n")

        // tCONTACT
        for (sq in qsos) {
            sb.append("<Rec_Type:8>tCONTACT\n")
            sb.adifLine("STATION_UID", "1")
            sb.adifLine("CALL", sq.qso.callsign)
            sb.adifLine("BAND", sq.qso.band)
            sb.adifLine("MODE", sq.qso.mode)
            if (sq.qso.freq.isNotEmpty()) sb.adifLine("FREQ", sq.qso.freq)
            if (sq.qso.rxFreq.isNotEmpty()) sb.adifLine("FREQ_RX", sq.qso.rxFreq)
            if (sq.qso.propMode.isNotEmpty()) sb.adifLine("PROP_MODE", sq.qso.propMode)
            if (sq.qso.satName.isNotEmpty()) sb.adifLine("SAT_NAME", sq.qso.satName)
            if (sq.qso.rxBand.isNotEmpty()) sb.adifLine("BAND_RX", sq.qso.rxBand)
            sb.adifLine("QSO_DATE", sq.qso.date.format(DATE_FORMAT))
            sb.adifLine("QSO_TIME", sq.qso.time.format(TIME_FORMAT))
            // Signature field (type '6' for base64)
            sb.append("<$SIGN_FIELD:${sq.signatureB64.length}:6>${sq.signatureB64}")
            sb.adifLine("SIGNDATA", sq.signdata)
            sb.append("<eor>\n")
        }

        return sb.toString()
    }

    /**
     * Compress GAbbi content to .tq8 gzip
     */
    fun compressToTq8(gabbi: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gz ->
            gz.write(gabbi.toByteArray(Charsets.US_ASCII))
        }
        return out.toByteArray()
    }

    private fun StringBuilder.adifLine(name: String, value: String) {
        if (value.isNotEmpty()) {
            append("<$name:${value.length}>$value\n")
        }
    }
}

data class SignedQso(
    val qso: QsoRecord,
    val signdata: String,
    val signatureB64: String
)
