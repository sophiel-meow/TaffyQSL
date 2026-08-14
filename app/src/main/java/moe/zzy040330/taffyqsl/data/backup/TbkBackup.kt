package moe.zzy040330.taffyqsl.data.backup

import android.util.Base64
import android.util.Xml
import moe.zzy040330.taffyqsl.data.db.DuplicateQsoEntity
import moe.zzy040330.taffyqsl.domain.model.StationLocation
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object TbkBackup {

    data class TbkUserCert(
        val callSign: String,
        val dxcc: Int,
        val serial: Long,
        val signedCertPem: String,
        val privateKeyAdifB64: String
    )

    data class TbkLocation(
        val name: String,
        val callSign: String,
        val dxcc: Int,
        val grid: String = "",
        val cqZone: String = "",
        val ituZone: String = "",
        val iota: String = "",
        val extra: Map<String, String> = emptyMap()
    )

    data class TbkDupe(
        val key: String,
        val data: String
    )

    data class TbkContent(
        val userCerts: List<TbkUserCert> = emptyList(),
        val locations: List<TbkLocation> = emptyList(),
        val dupes: List<TbkDupe> = emptyList()
    )

    /** The decoded key record from a <PrivateKey> blob. */
    data class KeyRecord(
        val callsign: String = "",
        val privateKeyPem: String = "",
        val publicKeyPem: String = ""
    )

    fun serialize(content: TbkContent): ByteArray {
        val xml = buildXml(content)
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gz -> gz.write(xml.toByteArray(StandardCharsets.UTF_8)) }
        return out.toByteArray()
    }

    fun parse(bytes: ByteArray): TbkContent {
        val xml = runCatching {
            GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
        }.getOrElse {
            // Tolerate a .tbk that was not actually gzip-compressed.
            bytes
        }.toString(StandardCharsets.UTF_8)
        return parseXml(xml)
    }

    private fun buildXml(content: TbkContent): String {
        val sw = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(sw)
        serializer.startDocument("UTF-8", null)
        serializer.startTag(null, "TQSL_Configuration")

        serializer.startTag(null, "Certificates")
        content.userCerts.forEach { cert ->
            serializer.startTag(null, "UserCert")
            serializer.attribute(null, "CallSign", cert.callSign)
            serializer.attribute(null, "dxcc", cert.dxcc.toString())
            serializer.attribute(null, "serial", cert.serial.toString())
            serializer.startTag(null, "SignedCert")
            serializer.text(cert.signedCertPem)
            serializer.endTag(null, "SignedCert")
            if (cert.privateKeyAdifB64.isNotEmpty()) {
                serializer.startTag(null, "PrivateKey")
                serializer.text(cert.privateKeyAdifB64)
                serializer.endTag(null, "PrivateKey")
            }
            serializer.endTag(null, "UserCert")
        }
        serializer.endTag(null, "Certificates")

        serializer.startTag(null, "Locations")
        content.locations.forEach { loc ->
            serializer.startTag(null, "Location")
            serializer.attribute(null, "name", loc.name)
            serializer.attribute(null, "CALL", loc.callSign)
            serializer.attribute(null, "DXCC", loc.dxcc.toString())
            if (loc.grid.isNotEmpty()) serializer.attribute(null, "GRIDSQUARE", loc.grid)
            if (loc.cqZone.isNotEmpty()) serializer.attribute(null, "CQZ", loc.cqZone)
            if (loc.ituZone.isNotEmpty()) serializer.attribute(null, "ITUZ", loc.ituZone)
            if (loc.iota.isNotEmpty()) serializer.attribute(null, "IOTA", loc.iota)
            loc.extra.forEach { (name, value) ->
                if (value.isNotEmpty()) serializer.attribute(null, name, value)
            }
            serializer.endTag(null, "Location")
        }
        serializer.endTag(null, "Locations")

        serializer.startTag(null, "TQSLSettings")
        serializer.endTag(null, "TQSLSettings")

        serializer.startTag(null, "DupeDb")
        content.dupes.forEach { dupe ->
            serializer.startTag(null, "Dupe")
            serializer.attribute(null, "key", dupe.key)
            serializer.attribute(null, "data", dupe.data)
            serializer.endTag(null, "Dupe")
        }
        serializer.endTag(null, "DupeDb")

        serializer.endTag(null, "TQSL_Configuration")
        serializer.endDocument()
        return sw.toString()
    }

    private fun parseXml(xml: String): TbkContent {
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xml))

        val certs = mutableListOf<TbkUserCert>()
        val locations = mutableListOf<TbkLocation>()
        val dupes = mutableListOf<TbkDupe>()
        var currentCert: TbkUserCertBuilder? = null
        var elementBody = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "UserCert" -> currentCert = TbkUserCertBuilder(
                            callSign = parser.getAttributeValue(null, "CallSign") ?: "",
                            dxcc = parser.getAttributeValue(null, "dxcc")?.toIntOrNull() ?: 0,
                            serial = parser.getAttributeValue(null, "serial")?.toLongOrNull() ?: 0L
                        )
                        "SignedCert", "PrivateKey" -> elementBody = ""
                        "Location" -> {
                            val attrs = mutableMapOf<String, String>()
                            for (i in 0 until parser.attributeCount) {
                                attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
                            }
                            locations.add(parseLocation(attrs))
                        }
                        "Dupe" -> {
                            dupes.add(
                                TbkDupe(
                                    key = parser.getAttributeValue(null, "key") ?: "",
                                    data = parser.getAttributeValue(null, "data") ?: "D"
                                )
                            )
                        }
                    }
                }
                XmlPullParser.TEXT -> elementBody += parser.text ?: ""
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "SignedCert" -> currentCert?.signedCertPem = elementBody.trim()
                        "PrivateKey" -> currentCert?.privateKeyAdifB64 = elementBody.trim()
                        "UserCert" -> {
                            currentCert?.let { certs.add(it.build()) }
                            currentCert = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return TbkContent(certs, locations, dupes)
    }

    private class TbkUserCertBuilder(
        val callSign: String,
        val dxcc: Int,
        val serial: Long
    ) {
        var signedCertPem: String = ""
        var privateKeyAdifB64: String = ""
        fun build() = TbkUserCert(callSign, dxcc, serial, signedCertPem, privateKeyAdifB64)
    }

    private fun parseLocation(attrs: Map<String, String>): TbkLocation {
        val base = setOf("name", "CALL", "DXCC", "GRIDSQUARE", "CQZ", "ITUZ", "IOTA")
        return TbkLocation(
            name = attrs["name"] ?: "",
            callSign = attrs["CALL"] ?: "",
            dxcc = attrs["DXCC"]?.toIntOrNull() ?: 0,
            grid = attrs["GRIDSQUARE"] ?: "",
            cqZone = attrs["CQZ"] ?: "",
            ituZone = attrs["ITUZ"] ?: "",
            iota = attrs["IOTA"] ?: "",
            extra = attrs.filterKeys { it !in base }
        )
    }

    fun encodeKeyRecord(
        callSign: String,
        privateKeyPem: String,
        publicKeyPem: String,
        dxcc: Int
    ): String {
        val sb = StringBuilder()
        adifField(sb, "CALLSIGN", callSign)
        adifField(sb, "PRIVATE_KEY", privateKeyPem)
        adifField(sb, "PUBLIC_KEY", publicKeyPem)
        if (dxcc != 0) adifField(sb, "TQSL_CRQ_DXCC_ENTITY", dxcc.toString())
        sb.append("<eor>\n\n")
        return Base64.encodeToString(
            sb.toString().toByteArray(StandardCharsets.UTF_8),
            Base64.NO_WRAP
        )
    }

    fun decodeKeyRecord(raw: String): KeyRecord {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return KeyRecord()

        runCatching {
            val text = String(Base64.decode(trimmed, Base64.DEFAULT), StandardCharsets.UTF_8)
            val fields = parseAdif(text)
            val key = KeyRecord(
                callsign = fields["CALLSIGN"] ?: "",
                privateKeyPem = fields["PRIVATE_KEY"] ?: "",
                publicKeyPem = fields["PUBLIC_KEY"] ?: ""
            )
            if (key.privateKeyPem.isNotEmpty()) return key
        }

        if (trimmed.contains("-----BEGIN")) {
            return KeyRecord(privateKeyPem = trimmed)
        }
        return KeyRecord()
    }

    private fun adifField(sb: StringBuilder, name: String, value: String) {
        sb.append("<$name:${value.length}>")
        sb.append(value)
        sb.append("\n\n")
    }

    private fun parseAdif(text: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        var i = 0
        while (i < text.length) {
            if (text[i] != '<') {
                i++
                continue
            }
            val close = text.indexOf('>', i)
            if (close < 0) break
            val header = text.substring(i + 1, close)
            i = close + 1
            if (header.equals("eor", ignoreCase = true)) break
            val len = header.split(':').getOrNull(1)?.toIntOrNull() ?: break
            if (i + len > text.length) break
            fields[header.substringBefore(':').uppercase()] = text.substring(i, i + len)
            i += len
        }
        return fields
    }

    private val STATE_FIELDS = setOf(
        "US_STATE", "CA_PROVINCE", "CN_PROVINCE", "RU_OBLAST",
        "AU_STATE", "JA_PREFECTURE", "FI_KUNTA"
    )
    private val COUNTY_FIELDS = setOf("US_COUNTY", "JA_CITY_GUN_KU")
    private val PARK_FIELDS = setOf("US_PARK", "CA_US_PARK", "DX_US_PARK")

    fun fromStation(station: StationLocation): TbkLocation {
        val extra = mutableMapOf<String, String>()
        if (station.state.isNotEmpty() && station.stateFieldName.isNotEmpty()) {
            extra[station.stateFieldName] = station.state
        }
        if (station.county.isNotEmpty() && station.countyFieldName.isNotEmpty()) {
            extra[station.countyFieldName] = station.county
        }
        if (station.park.isNotEmpty() && station.parkFieldName.isNotEmpty()) {
            extra[station.parkFieldName] = station.park
        }
        return TbkLocation(
            name = station.name,
            callSign = station.callSign,
            dxcc = station.dxcc,
            grid = station.grid,
            cqZone = station.cqZone,
            ituZone = station.ituZone,
            iota = station.iota,
            extra = extra
        )
    }

    fun toStation(tbk: TbkLocation): StationLocation {
        var state = ""
        var stateField = ""
        var county = ""
        var countyField = ""
        var park = ""
        var parkField = ""
        tbk.extra.forEach { (name, value) ->
            when {
                name in STATE_FIELDS || name.contains("STATE") || name.contains("PROVINCE") ||
                    name.contains("OBLAST") || name.contains("PREFECTURE") || name.contains("KUNTA") -> {
                    state = value
                    stateField = name
                }
                name in COUNTY_FIELDS || name.contains("COUNTY") || name.contains("CITY_GUN_KU") -> {
                    county = value
                    countyField = name
                }
                name in PARK_FIELDS || name.contains("PARK") -> {
                    park = value
                    parkField = name
                }
            }
        }
        return StationLocation(
            name = tbk.name.ifEmpty { tbk.callSign },
            callSign = tbk.callSign,
            dxcc = tbk.dxcc,
            grid = tbk.grid,
            state = state,
            stateFieldName = stateField,
            county = county,
            countyFieldName = countyField,
            park = park,
            parkFieldName = parkField,
            cqZone = tbk.cqZone,
            ituZone = tbk.ituZone,
            iota = tbk.iota
        )
    }

    fun fromDupe(entity: DuplicateQsoEntity): TbkDupe = TbkDupe(key = entity.key, data = "D")

    fun toDupeEntity(tbk: TbkDupe): DuplicateQsoEntity {
        val parts = tbk.key.split("|")
        return if (parts.size == 7) {
            DuplicateQsoEntity(
                key = tbk.key,
                callsign = parts[0],
                band = parts[1],
                mode = parts[2],
                date = parts[3],
                time = parts[4],
                certAlias = parts[5],
                stationName = parts[6]
            )
        } else {
            DuplicateQsoEntity(
                key = tbk.key,
                callsign = "",
                band = "",
                mode = "",
                date = "",
                time = "",
                certAlias = "",
                stationName = ""
            )
        }
    }
}
