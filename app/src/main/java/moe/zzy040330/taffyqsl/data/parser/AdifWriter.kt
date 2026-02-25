package moe.zzy040330.taffyqsl.data.parser

import moe.zzy040330.taffyqsl.domain.model.QsoRecord
import java.io.OutputStream

/**
 * Write a single ADIF field in format: <NAME:SIZE>VALUE
 */
fun adifField(name: String, value: String): String {
    return if (value.isEmpty()) "" else "<$name:${value.length}>$value"
}

/**
 * Write ADIF with optional type indicator
 */
fun adifFieldTyped(name: String, type: Char, value: String): String {
    return if (value.isEmpty()) "" else "<$name:${value.length}:$type>$value"
}


/**
 * Writes QSO records in ADIF format.
 */
class AdifWriter(private val output: OutputStream) {

    fun write(records: List<QsoRecord>) {
        val sb = StringBuilder()
        sb.append("TaffyQSL ADIF Export\n")
        sb.append("<EOH>\n\n")
        for (rec in records) {
            sb.append(adifField("CALL", rec.callsign)).append('\n')
            sb.append(
                adifField(
                    "QSO_DATE",
                    rec.date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                )
            ).append('\n')
            sb.append(
                adifField(
                    "TIME_ON",
                    rec.time.format(java.time.format.DateTimeFormatter.ofPattern("HHmmss"))
                )
            ).append('\n')
            sb.append(adifField("BAND", rec.band)).append('\n')
            sb.append(adifField("MODE", rec.mode)).append('\n')
            if (rec.submode.isNotEmpty()) sb.append(adifField("SUBMODE", rec.submode)).append('\n')
            if (rec.freq.isNotEmpty()) sb.append(adifField("FREQ", rec.freq)).append('\n')
            if (rec.rxFreq.isNotEmpty()) sb.append(adifField("FREQ_RX", rec.rxFreq)).append('\n')
            if (rec.rxBand.isNotEmpty()) sb.append(adifField("BAND_RX", rec.rxBand)).append('\n')
            if (rec.propMode.isNotEmpty()) sb.append(adifField("PROP_MODE", rec.propMode))
                .append('\n')
            if (rec.satName.isNotEmpty()) sb.append(adifField("SAT_NAME", rec.satName)).append('\n')
            sb.append("<EOR>\n\n")
        }
        output.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
    }
}
