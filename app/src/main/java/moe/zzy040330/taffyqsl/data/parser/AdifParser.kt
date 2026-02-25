package moe.zzy040330.taffyqsl.data.parser

import java.io.InputStream

data class AdifField(val name: String, val value: String)

/**
 * Streaming ADIF parser. Parses fields one at a time from the input stream.
 * Format: <FIELD_NAME:SIZE[:TYPE]>DATA ... <EOR>
 */
class AdifParser(private val input: InputStream) : AutoCloseable {

    private val text = input.bufferedReader(Charsets.ISO_8859_1).readText()
    private var pos = 0
    private var lineNumber = 1

    private val fieldPattern = Regex("""<(\w+)(?::(\d+)(?::[^>]*)?)?>""", RegexOption.IGNORE_CASE)

    /**
     * Read all QSO records.
     * Returns list of field maps, one per QSO.
     */
    fun readAllQsos(): List<Map<String, String>> {
        val qsos = mutableListOf<Map<String, String>>()
        // Skip header (everything before the first <EOH> or first QSO data)
        skipHeader()
        var currentRecord = mutableMapOf<String, String>()
        while (pos < text.length) {
            val field = nextField() ?: break
            when {
                field.name.equals("EOR", ignoreCase = true) -> {
                    if (currentRecord.isNotEmpty()) {
                        qsos.add(currentRecord.toMap())
                        currentRecord = mutableMapOf()
                    }
                }

                field.name.equals("EOH", ignoreCase = true) -> {
                    currentRecord = mutableMapOf()
                }

                else -> {
                    currentRecord[field.name.uppercase()] = field.value
                }
            }
        }
        return qsos
    }

    private fun skipHeader() {
        // Look for <EOH>: if found, skip past it. If not, assume no header.
        val eohIdx = text.indexOf("<EOH>", ignoreCase = true)
        pos = if (eohIdx >= 0) {
            eohIdx + 5
        } else {
            // Check for first QSO record marker or just start from beginning
            0
        }
    }

    private fun nextField(): AdifField? {
        // Find next '<'
        val ltIdx = text.indexOf('<', pos)
        if (ltIdx < 0) return null
        val gtIdx = text.indexOf('>', ltIdx)
        if (gtIdx < 0) return null

        val tag = text.substring(ltIdx + 1, gtIdx)
        pos = gtIdx + 1

        // Parse tag: FIELD_NAME or FIELD_NAME:SIZE or FIELD_NAME:SIZE:TYPE
        val parts = tag.split(':')
        val name = parts[0].trim()

        if (name.equals("EOR", ignoreCase = true) || name.equals("EOH", ignoreCase = true)) {
            return AdifField(name.uppercase(), "")
        }

        val size = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val value = if (size > 0 && pos + size <= text.length) {
            val v = text.substring(pos, pos + size)
            pos += size
            v
        } else {
            ""
        }

        return AdifField(name.uppercase(), value.trim())
    }

    override fun close() {
        input.close()
    }
}


