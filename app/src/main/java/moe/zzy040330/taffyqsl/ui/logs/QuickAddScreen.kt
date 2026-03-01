package moe.zzy040330.taffyqsl.ui.logs

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import moe.zzy040330.taffyqsl.R
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    fileName: String,
    onDismiss: () -> Unit,
    onSaved: (count: Int) -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val vm: QuickAddViewModel = viewModel(
        key = "quick_add_$fileName",
        factory = QuickAddViewModel.factory(app, fileName)
    )

    val input by vm.input.collectAsState()
    val parsedLines by vm.parsedLines.collectAsState()
    val isSaving by vm.isSaving.collectAsState()

    val allQsos = parsedLines.flatMap { it.qsos }
    val validCount = allQsos.count { it.isValid }
    val errorCount = allQsos.count { !it.isValid }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val errorColor = MaterialTheme.colorScheme.error
    val disabledColor = MaterialTheme.colorScheme.onSurfaceVariant

    fun tokenColor(kind: TokenKind): Color = when (kind) {
        is TokenKind.Callsign -> primaryColor
        is TokenKind.Time -> tertiaryColor
        is TokenKind.Date -> secondaryColor
        is TokenKind.Frequency -> Color(0xFF7B5EA7)
        is TokenKind.Mode -> Color(0xFFBF360C)
        is TokenKind.Satellite -> Color(0xFFF57F17)
        is TokenKind.Unknown -> errorColor
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // Height is constrained here (not on the sheet modifier) so that
        // draggableAnchors uses fullHeight = screenHeight and the sheet
        // surface always extends to the very bottom of the screen.
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
            ) {
                Text(
                    stringResource(R.string.quick_add),
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.quick_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // Multi-line input
                val inputTextStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    BasicTextField(
                        value = input,
                        onValueChange = { vm.onInputChanged(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp, max = 160.dp)
                            .padding(12.dp),
                        textStyle = inputTextStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(
                                    stringResource(R.string.quick_add_placeholder),
                                    style = inputTextStyle.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                            inner()
                        }
                    )
                }

                // Token highlight preview
                if (parsedLines.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val annotated = buildAnnotatedString {
                        parsedLines.forEachIndexed { lineIdx, line ->
                            if (lineIdx > 0) append("\n")
                            line.tokens.forEachIndexed { tokenIdx, token ->
                                if (tokenIdx > 0) append(" ")
                                if (token.isDuplicate || token.kind is TokenKind.Unknown) {
                                    withStyle(
                                        SpanStyle(
                                            color = disabledColor,
                                            textDecoration = TextDecoration.LineThrough
                                        )
                                    ) {
                                        append(token.raw)
                                    }
                                } else {
                                    val color = tokenColor(token.kind)
                                    withStyle(
                                        SpanStyle(
                                            color = color,
                                            background = color.copy(alpha = 0.10f),
                                            fontWeight = if (token.isAmbiguous) FontWeight.Bold
                                            else FontWeight.Normal
                                        )
                                    ) {
                                        append(token.raw)
                                        if (token.isAmbiguous) append("?")
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        annotated,
                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                // Parsed QSO cards
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    parsedLines.forEachIndexed { lineIdx, line ->
                        // Ambiguous token chips for this line
                        val ambiguousTokens = line.tokens.mapIndexedNotNull { idx, t ->
                            if (t.isAmbiguous) Pair(idx, t) else null
                        }
                        if (ambiguousTokens.isNotEmpty()) {
                            item(key = "amb_$lineIdx") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ambiguousTokens.forEach { (idx, token) ->
                                        AmbiguousTokenChip(
                                            token = token,
                                            onClick = { vm.resolveAmbiguous(lineIdx, idx) }
                                        )
                                    }
                                }
                            }
                        }
                        // One card per input line (multiple callsigns)
                        item(key = "card_$lineIdx") {
                            ParsedLineCard(line = line)
                        }
                    }
                    if (parsedLines.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    stringResource(R.string.quick_add_empty_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Bottom action bar
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (errorCount > 0) {
                        Text(
                            pluralStringResource(
                                R.plurals.quick_add_errors_skipped,
                                errorCount,
                                errorCount
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }

                    Button(
                        onClick = {
                            vm.saveValidQsos { count ->
                                onSaved(count)
                                onDismiss()
                            }
                        },
                        enabled = validCount > 0 && !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(
                                if (errorCount > 0)
                                    stringResource(R.string.quick_add_save_valid, validCount)
                                else
                                    stringResource(R.string.quick_add_save_all, validCount)
                            )
                        }
                    }
                }
            }
        } // BoxWithConstraints
    }
}

// Line card (one per input line, groups all callsigns)

@Composable
private fun ParsedLineCard(line: ParsedLineResult) {
    val firstQso = line.qsos.firstOrNull()
    val allValid = line.qsos.isNotEmpty() && line.qsos.all { it.isValid }
    val duplicateTokens = line.tokens.filter { it.isDuplicate }

    val borderColor = if (allValid)
        MaterialTheme.colorScheme.outlineVariant
    else
        MaterialTheme.colorScheme.error

    val containerColor = if (allValid)
        MaterialTheme.colorScheme.surfaceContainerLow
    else
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)

    Card(
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (allValid && firstQso != null) {
                Text(
                    line.qsos.joinToString(" · ") { it.callsign },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                // Shared context (freq, mode, band, satellite)
                val details = buildString {
                    firstQso.freqMhz?.let { append("%.3f MHz".format(it)) }
                    if (firstQso.mode.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(firstQso.mode)
                    }
                    if (firstQso.band.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(firstQso.band)
                    }
                    firstQso.satellite?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it.name)
                    }
                }
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall)
                }

                val timeStr = firstQso.utcTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                Text(
                    "${firstQso.utcDate} · ${timeStr}Z",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // Error: show any callsigns found + error messages
                val validCallsigns = line.qsos.filter { it.callsign.isNotEmpty() }
                if (validCallsigns.isNotEmpty()) {
                    Text(
                        validCallsigns.joinToString(" · ") { it.callsign },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                // All QSOs in a line share the same context errors; show from first
                firstQso?.errors?.forEach { err ->
                    val msg = when (err) {
                        QuickAddViewModel.ERR_NO_CALLSIGN ->
                            stringResource(R.string.quick_add_error_no_callsign)

                        QuickAddViewModel.ERR_NO_TIME ->
                            stringResource(R.string.quick_add_error_no_time)

                        QuickAddViewModel.ERR_NO_FREQ_OR_SAT ->
                            stringResource(R.string.quick_add_error_no_freq_or_sat)

                        else -> err
                    }
                    Text(
                        "⚠ $msg",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Ignored duplicate tokens
            duplicateTokens.forEach { token ->
                Text(
                    "⚠ Ignored: ${token.raw}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ******** Ambiguous token chip ********

@Composable
private fun AmbiguousTokenChip(
    token: ParsedToken,
    onClick: () -> Unit
) {
    // Chip label shows exactly what will appear in the input box after clicking.
    // e.g. "1230 -> 1230kHz"  or  "0302 -> 03-02 (dd-MM / MM-dd)"
    val label = if (token.altReplacement != null) {
        "${token.raw} → ${token.altReplacement}"
    } else {
        token.raw
    }
    SuggestionChip(
        onClick = onClick,
        label = {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    )
}
