package moe.zzy040330.taffyqsl.ui.logs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.zzy040330.taffyqsl.data.AppPreferences
import moe.zzy040330.taffyqsl.data.DateFormatOption
import moe.zzy040330.taffyqsl.data.config.ConfigRepository
import moe.zzy040330.taffyqsl.data.config.SatelliteInfo
import moe.zzy040330.taffyqsl.data.db.AppDatabase
import moe.zzy040330.taffyqsl.data.db.QsoRecordEntity
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

sealed class TokenKind {
    data class Callsign(val value: String) : TokenKind()
    data class Time(val localTime: LocalTime) : TokenKind()
    data class Date(val localDate: LocalDate) : TokenKind()
    data class Frequency(val freqMhz: Double) : TokenKind()
    data class Mode(val name: String) : TokenKind()
    data class Satellite(val info: SatelliteInfo) : TokenKind()
    object Unknown : TokenKind()
}

/**
 * A classified token from a quick-entry line.
 *
 * @param altKind        Alternative interpretation for ambiguous 4-digit numbers.
 * @param altReplacement Text to substitute into the input box when the user picks [altKind].
 *                       Chosen so that SHORT_DATE_REGEX or FREQ_UNIT_REGEX will parse it
 *                       unambiguously under the current DateFormatOption.
 * @param isDuplicate    True if another token of the same kind already appeared on this line;
 *                       this token is ignored in context resolution and shown struck-through.
 */
data class ParsedToken(
    val raw: String,
    val kind: TokenKind,
    val altKind: TokenKind? = null,
    val altReplacement: String? = null,
    val isDuplicate: Boolean = false
) {
    val isAmbiguous: Boolean get() = altKind != null && altReplacement != null
}

// Per-QSO result
data class ParsedQsoEntry(
    val callsign: String,
    val utcDate: LocalDate,
    val utcTime: LocalTime,
    val freqMhz: Double?,
    val rxFreqMhz: Double?,
    val band: String,
    val rxBand: String,
    val mode: String,
    val satellite: SatelliteInfo?,
    val propMode: String,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class ParsedLineResult(
    val rawLineIndex: Int, // Index in `input.split("\n")`, used when removing saved lines.
    val rawLine: String,
    val tokens: List<ParsedToken>,

    /**
     * One entry per callsign found on the line; if no callsigns, one error entry.
     * All entries share the same context (freq / mode / sat / time / date / errors).
     */
    val qsos: List<ParsedQsoEntry>
)

// ── ViewModel ──────────────────────────────────────────────────────────────────

class QuickAddViewModel(
    app: Application,
    val fileName: String
) : AndroidViewModel(app) {

    private val config = ConfigRepository.getInstance(app)
    private val prefs = AppPreferences.getInstance(app)
    private val db = AppDatabase.getInstance(app)

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _parsedLines = MutableStateFlow<List<ParsedLineResult>>(emptyList())
    val parsedLines: StateFlow<List<ParsedLineResult>> = _parsedLines.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()


    fun onInputChanged(text: String) {
        _input.value = text
        reParse()
    }

    /**
     * Replace an ambiguous token in the input with its [ParsedToken.altReplacement].
     * After replacement the new token is unambiguous; no chip will appear.
     */
    fun resolveAmbiguous(lineIdx: Int, tokenIdx: Int) {
        val line = _parsedLines.value.getOrNull(lineIdx) ?: return
        val token = line.tokens.getOrNull(tokenIdx) ?: return
        val replacement = token.altReplacement ?: return

        val inputLines = _input.value.split("\n").toMutableList()
        val rawIdx = line.rawLineIndex
        if (rawIdx >= inputLines.size) return

        val lineTokens = inputLines[rawIdx].trim().split(Regex("\\s+")).toMutableList()
        if (tokenIdx < lineTokens.size) {
            lineTokens[tokenIdx] = replacement
            inputLines[rawIdx] = lineTokens.joinToString(" ")
            _input.value = inputLines.joinToString("\n")
            reParse()
        }
    }

    /**
     * Save all valid QSOs, then remove those lines from the input.
     * Lines with any error are kept.
     */
    fun saveValidQsos(onDone: (count: Int) -> Unit) {
        val validLines = _parsedLines.value.filter { line ->
            line.qsos.isNotEmpty() && line.qsos.all { it.isValid }
        }
        val validQsos = validLines.flatMap { it.qsos }
        if (validQsos.isEmpty()) {
            onDone(0); return
        }

        val savedRawIndices = validLines.map { it.rawLineIndex }.toSet()

        _isSaving.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val entities = validQsos.map { q ->
                QsoRecordEntity(
                    fileId = fileName,
                    callsign = q.callsign,
                    bandName = q.band,
                    modeName = q.mode,
                    dateStr = q.utcDate.format(DateTimeFormatter.BASIC_ISO_DATE),
                    timeStr = q.utcTime.format(DateTimeFormatter.ofPattern("HHmmss")),
                    freq = q.freqMhz?.let { formatFreq(it) } ?: "",
                    rxFreq = q.rxFreqMhz?.let { formatFreq(it) } ?: "",
                    rxBandName = q.rxBand,
                    propMode = q.propMode,
                    satName = q.satellite?.name ?: ""
                )
            }
            db.qsoRecordDao().insertAll(entities)
            val newCount = db.qsoRecordDao().countByFile(fileName)
            db.qsoFileDao().updateQsoCount(fileName, newCount)

            withContext(Dispatchers.Main) {
                val inputLines = _input.value.split("\n")
                val remaining = inputLines.filterIndexed { idx, _ -> idx !in savedRawIndices }
                _input.value = remaining.joinToString("\n").trimEnd('\n')
                reParse()
                _isSaving.value = false
                onDone(validQsos.size)
            }
        }
    }

    // ******** Parsing ********

    private fun reParse() {
        val lines = _input.value.split("\n")
        _parsedLines.value = lines.mapIndexedNotNull { rawIdx, raw ->
            if (raw.isBlank()) null else parseLine(raw.trim(), rawIdx)
        }
    }

    private fun parseLine(raw: String, rawLineIndex: Int): ParsedLineResult {
        val tokenStrings = raw.split(Regex("\\s+"))

        // First pass: classify each token in sequence (timeFound tracks context)
        val classified = mutableListOf<ParsedToken>()
        var timeFound = false
        for (token in tokenStrings) {
            val pt = classifyToken(token, timeFound, prefs.dateFormat)
            classified.add(pt)
            if (pt.kind is TokenKind.Time) timeFound = true
        }

        // Second pass: mark duplicate context tokens (date / time / freq / mode / satellite).
        // Callsigns are exempt — multiple callsigns produce multiple QSOs.
        // Duplicates are visually struck-through and excluded from context resolution.
        var dateSeen = false
        var timeSeen = false
        var freqSeen = false
        var modeSeen = false
        var satSeen = false
        val parsedTokens = classified.map { pt ->
            when (pt.kind) {
                is TokenKind.Date ->
                    if (dateSeen) pt.copy(isDuplicate = true) else {
                        dateSeen = true; pt
                    }

                is TokenKind.Time ->
                    if (timeSeen) pt.copy(isDuplicate = true) else {
                        timeSeen = true; pt
                    }

                is TokenKind.Frequency ->
                    if (freqSeen) pt.copy(isDuplicate = true) else {
                        freqSeen = true; pt
                    }

                is TokenKind.Mode ->
                    if (modeSeen) pt.copy(isDuplicate = true) else {
                        modeSeen = true; pt
                    }

                is TokenKind.Satellite ->
                    if (satSeen) pt.copy(isDuplicate = true) else {
                        satSeen = true; pt
                    }

                else -> pt
            }
        }

        // Extract shared context (non-duplicate tokens only)
        val active = parsedTokens.filter { !it.isDuplicate }

        val callsigns = active.filter { it.kind is TokenKind.Callsign }
            .map { (it.kind as TokenKind.Callsign).value }
        val satellite = active.firstOrNull { it.kind is TokenKind.Satellite }
            ?.let { (it.kind as TokenKind.Satellite).info }
        val parsedTime = active.firstOrNull { it.kind is TokenKind.Time }
            ?.let { (it.kind as TokenKind.Time).localTime }
        val parsedDate = active.firstOrNull { it.kind is TokenKind.Date }
            ?.let { (it.kind as TokenKind.Date).localDate }
        val parsedFreq = active.firstOrNull { it.kind is TokenKind.Frequency }
            ?.let { (it.kind as TokenKind.Frequency).freqMhz }
        val parsedMode = active.firstOrNull { it.kind is TokenKind.Mode }
            ?.let { (it.kind as TokenKind.Mode).name }

        val effectiveFreq = parsedFreq ?: satellite?.downlinkFreqMhz
        val effectiveRxFreq = satellite?.uplinkFreqMhz
        val effectiveMode = parsedMode ?: satellite?.mode ?: ""
        val effectiveBand = effectiveFreq?.let { config.bandForFreq(it) } ?: ""
        val effectiveRxBand = effectiveRxFreq?.let { config.bandForFreq(it) } ?: ""
        val effectivePropMode = if (satellite != null) "SAT" else ""

        val today = if (prefs.useLocalTime)
            LocalDate.now(ZoneId.systemDefault()) else LocalDate.now(ZoneOffset.UTC)
        val inputDate = parsedDate ?: today

        val qsos = if (callsigns.isEmpty()) {
            listOf(
                buildQsoEntry(
                    "", inputDate, parsedTime,
                    effectiveFreq, effectiveRxFreq,
                    effectiveBand, effectiveRxBand,
                    effectiveMode, satellite, effectivePropMode
                )
            )
        } else {
            callsigns.map { cs ->
                buildQsoEntry(
                    cs, inputDate, parsedTime,
                    effectiveFreq, effectiveRxFreq,
                    effectiveBand, effectiveRxBand,
                    effectiveMode, satellite, effectivePropMode
                )
            }
        }

        return ParsedLineResult(rawLineIndex, raw, parsedTokens, qsos)
    }

    private fun buildQsoEntry(
        callsign: String, date: LocalDate, time: LocalTime?,
        freqMhz: Double?, rxFreqMhz: Double?,
        band: String, rxBand: String,
        mode: String, satellite: SatelliteInfo?, propMode: String
    ): ParsedQsoEntry {
        val errors = mutableListOf<String>()
        if (callsign.isBlank()) errors += ERR_NO_CALLSIGN
        if (time == null) errors += ERR_NO_TIME
        if (freqMhz == null) errors += ERR_NO_FREQ_OR_SAT

        val (utcDate, utcTime) = if (time != null && prefs.useLocalTime) {  // localtime vs UTC
            val utc = time.atDate(date).atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
            Pair(utc.toLocalDate(), utc.toLocalTime())
        } else {
            Pair(date, time ?: LocalTime.MIDNIGHT)
        }

        return ParsedQsoEntry(
            callsign, utcDate, utcTime,
            freqMhz, rxFreqMhz,
            band, rxBand,
            mode, satellite, propMode,
            errors
        )
    }

    // ******** Token classification ********

    private fun classifyToken(
        token: String,
        timeAlreadyFound: Boolean,
        dateFormat: DateFormatOption
    ): ParsedToken {

        // 1. Satellite alias
        config.findSatelliteByAlias(token)?.let {
            return ParsedToken(token, TokenKind.Satellite(it))
        }

        // 2. Mode keyword
        val upper = token.uppercase()
        if (config.modes.any { it.name == upper }) {
            return ParsedToken(token, TokenKind.Mode(upper))
        }

        // 3. Frequency with explicit unit: kHz / k
        FREQ_UNIT_REGEX.matchEntire(token)?.let { m ->
            m.groupValues[1].toDoubleOrNull()?.let { num ->
                val freqMhz = when (m.groupValues[2].lowercase()) {
                    "khz", "k" -> num / 1000.0
                    "mhz", "m" -> num
                    "ghz", "g" -> num * 1000.0
                    else -> return@let
                }
                return ParsedToken(token, TokenKind.Frequency(freqMhz))
            }
        }

        // 4. Decimal number w/ point -> frequency in MHz (e.g. 14.074) else in kHz
        if (token.contains('.')) {
            token.toDoubleOrNull()?.let { return ParsedToken(token, TokenKind.Frequency(it)) }
        }

        // 5. ITU callsign
        if (CALLSIGN_REGEX.matches(token)) {
            return ParsedToken(token, TokenKind.Callsign(token.uppercase()))
        }

        // 6. Full date with separator: 2026-03-02 or 2026/03/02 -> YYYY-MM-DD order
        FULL_DATE_REGEX.matchEntire(token)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return@let
            val a = m.groupValues[2].toIntOrNull() ?: return@let
            val b = m.groupValues[3].toIntOrNull() ?: return@let
            runCatching { LocalDate.of(y, a, b) }.getOrNull()?.let {
                return ParsedToken(token, TokenKind.Date(it))
            }
        }

        // 7. 8-digit: YYYYMMDD date
        if (token.length == 8 && token.all { it.isDigit() }) {
            runCatching { LocalDate.parse(token, DateTimeFormatter.BASIC_ISO_DATE) }
                .getOrNull()?.let { return ParsedToken(token, TokenKind.Date(it)) }
        }

        // 8. 5–6 digit: frequency in kHz: 14074 -> 14.074 MHz
        if (token.length in 5..6 && token.all { it.isDigit() }) {
            return ParsedToken(token, TokenKind.Frequency(token.toLong() / 1000.0))
        }

        // 9. Short date with explicit separator: 03-02 or 03/02
        SHORT_DATE_REGEX.matchEntire(token)?.let { m ->
            val a = m.groupValues[1].toIntOrNull() ?: return@let
            val b = m.groupValues[2].toIntOrNull() ?: return@let
            val date = if (dateFormat.shortPattern == "MMdd") {
                runCatching { LocalDate.of(LocalDate.now().year, a, b) }.getOrNull()
            } else {
                runCatching { LocalDate.of(LocalDate.now().year, b, a) }.getOrNull()
            }
            date?.let { return ParsedToken(token, TokenKind.Date(it)) }
        }

        // 10. HH:MM -> unambiguous time
        TIME_COLON_REGEX.matchEntire(token)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h in 0..23 && min in 0..59) {
                return ParsedToken(token, TokenKind.Time(LocalTime.of(h, min)))
            }
        }

        // 11. 4-digit number -> ambiguous with exactly one alternative
        //     Priority: time (default) -> date -> freq.
        if (token.length == 4 && token.all { it.isDigit() }) {
            val h = token.substring(0, 2).toInt()
            val min = token.substring(2, 4).toInt()
            val isValidTime = h in 0..23 && min in 0..59

            return when {
                !timeAlreadyFound && isValidTime -> {
                    // Default: TIME; alt: DATE (if valid) or FREQ
                    val dateResult = tryShortDate(token, dateFormat)
                    if (dateResult != null) {
                        val (date, sep) = dateResult
                        ParsedToken(
                            token, TokenKind.Time(LocalTime.of(h, min)),
                            altKind = TokenKind.Date(date), altReplacement = sep
                        )
                    } else {
                        ParsedToken(
                            token, TokenKind.Time(LocalTime.of(h, min)),
                            altKind = TokenKind.Frequency(token.toInt() / 1000.0),
                            altReplacement = "${token}kHz"
                        )
                    }
                }

                timeAlreadyFound -> {
                    // Default: DATE (if valid); alt: TIME (with colon) or FREQ
                    val dateResult = tryShortDate(token, dateFormat)
                    if (dateResult != null) {
                        val (date, sep) = dateResult
                        val altReplacement = if (isValidTime)
                            "${h.toString().padStart(2, '0')}:${min.toString().padStart(2, '0')}"
                        else "${token}kHz"
                        val altKind = if (isValidTime) TokenKind.Time(LocalTime.of(h, min))
                        else TokenKind.Frequency(token.toInt() / 1000.0)
                        ParsedToken(token, TokenKind.Date(date), altKind, altReplacement)
                    } else {
                        ParsedToken(token, TokenKind.Frequency(token.toInt() / 1000.0))
                    }
                }

                else -> ParsedToken(token, TokenKind.Frequency(token.toInt() / 1000.0))
            }
        }

        return ParsedToken(token, TokenKind.Unknown)
    }

    /**
     * Try to parse a 4-digit token as a short date, respecting [dateFormat].
     *
     * Both orderings are tried so the chip appears regardless of which way the user typed it.
     * The returned [String] is the separator form that will re-parse correctly under
     * [dateFormat] via SHORT_DATE_REGEX (e.g. "12-30" for MMdd, "30-12" for ddMM).
     *
     * Returns null if the token cannot form any valid date in either ordering.
     */
    private fun tryShortDate(
        token: String,
        dateFormat: DateFormatOption
    ): Pair<LocalDate, String>? {
        if (token.length != 4 || !token.all { it.isDigit() }) return null
        val x = token.substring(0, 2).toInt()
        val y = token.substring(2, 4).toInt()
        val xs = x.toString().padStart(2, '0')
        val ys = y.toString().padStart(2, '0')
        val year = LocalDate.now().year

        return if (dateFormat.shortPattern == "MMdd") {
            // Prefer: x=month, y=day -> replacement "xs-ys"
            runCatching { LocalDate.of(year, x, y) }.getOrNull()?.let { return Pair(it, "$xs-$ys") }
            // Fallback: x=day, y=month -> rewrite as "ys-xs" so SHORT_DATE_REGEX reads month=y first
            runCatching { LocalDate.of(year, y, x) }.getOrNull()?.let { Pair(it, "$ys-$xs") }
        } else { // ddMM
            // Prefer: x=day, y=month -> replacement "xs-ys"
            runCatching { LocalDate.of(year, y, x) }.getOrNull()?.let { return Pair(it, "$xs-$ys") }
            // Fallback: x=month, y=day -> rewrite as "ys-xs" so SHORT_DATE_REGEX reads day=y first
            runCatching { LocalDate.of(year, x, y) }.getOrNull()?.let { Pair(it, "$ys-$xs") }
        }
    }

    private fun formatFreq(mhz: Double): String =
        "%.6f".format(mhz).trimEnd('0').trimEnd('.')

    // Companion
    companion object {
        const val ERR_NO_CALLSIGN = "no_callsign"
        const val ERR_NO_TIME = "no_time"
        const val ERR_NO_FREQ_OR_SAT = "no_freq_or_sat"

        private val CALLSIGN_REGEX = Regex(
            "^([A-Z0-9]{1,4}/)?[A-Z0-9]{1,3}[0-9][A-Z]{1,5}(/[A-Z0-9]{1,4})?\$",
            RegexOption.IGNORE_CASE
        )
        private val TIME_COLON_REGEX = Regex("^(\\d{1,2}):(\\d{2})\$")
        private val FREQ_UNIT_REGEX = Regex(
            "^(\\d+\\.?\\d*)(kHz|MHz|GHz|k|m|g)\$", RegexOption.IGNORE_CASE
        )
        private val FULL_DATE_REGEX = Regex("^(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})\$")
        private val SHORT_DATE_REGEX = Regex("^(\\d{1,2})[/-](\\d{1,2})\$")

        fun factory(app: Application, fileName: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    QuickAddViewModel(app, fileName) as T
            }
    }
}
