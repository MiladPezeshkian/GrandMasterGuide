package com.zorix.chess.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zorix.chess.R
import com.zorix.chess.controller.BoardThemeId
import com.zorix.chess.controller.EngineStatus
import com.zorix.chess.controller.Settings
import com.zorix.chess.ui.board.boardColors
import com.zorix.chess.ui.components.formatSeconds
import com.zorix.chess.ui.label
import com.zorix.chess.ui.theme.NumberStyle
import com.zorix.chess.ui.theme.ZorixColors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    engine: EngineStatus,
    maxThreads: Int,
    onChange: ((Settings) -> Settings) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))

            SectionTitle(stringResource(R.string.settings_engine))
            ValueRow(stringResource(R.string.settings_think_time), stringResource(R.string.think_time_value, formatSeconds(settings.thinkTimeMs)))
            val steps = (Settings.MAX_THINK_MS - Settings.MIN_THINK_MS) / Settings.THINK_STEP_MS
            Slider(
                value = settings.thinkTimeMs.toFloat(),
                onValueChange = { v ->
                    val snapped = ((v / Settings.THINK_STEP_MS).roundToInt() * Settings.THINK_STEP_MS)
                        .coerceIn(Settings.MIN_THINK_MS, Settings.MAX_THINK_MS)
                    onChange { it.copy(thinkTimeMs = snapped) }
                },
                valueRange = Settings.MIN_THINK_MS.toFloat()..Settings.MAX_THINK_MS.toFloat(),
                steps = steps - 1,
            )

            if (maxThreads > 1) {
                ValueRow(stringResource(R.string.settings_threads), settings.threads.toString())
                Slider(
                    value = settings.threads.toFloat(),
                    onValueChange = { v -> onChange { it.copy(threads = v.roundToInt().coerceIn(1, maxThreads)) } },
                    valueRange = 1f..maxThreads.toFloat(),
                    steps = (maxThreads - 2).coerceAtLeast(0),
                )
            }

            ValueRow(stringResource(R.string.settings_hash), stringResource(R.string.mb_value, settings.hashMb))
            ChipRow(Settings.HASH_CHOICES, settings.hashMb, { it.toString() }) { mb ->
                onChange { it.copy(hashMb = mb) }
            }
            Spacer(Modifier.height(12.dp))

            ValueRow(stringResource(R.string.settings_lines), settings.analysisLines.toString())
            ChipRow((1..Settings.MAX_LINES).toList(), settings.analysisLines, { it.toString() }) { n ->
                onChange { it.copy(analysisLines = n) }
            }

            if (engine is EngineStatus.Ready) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.settings_engine_info, engine.name, engine.build),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(R.string.settings_board))
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BoardThemeId.entries.forEach { theme ->
                        ThemeSwatch(theme, selected = theme == settings.boardTheme) { onChange { it.copy(boardTheme = theme) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SwitchRow(stringResource(R.string.settings_coordinates), settings.showCoordinates) { v -> onChange { it.copy(showCoordinates = v) } }
            SwitchRow(stringResource(R.string.settings_legal_moves), settings.showLegalMoves) { v -> onChange { it.copy(showLegalMoves = v) } }
            SwitchRow(stringResource(R.string.settings_arrows), settings.showArrows) { v -> onChange { it.copy(showArrows = v) } }
            SwitchRow(stringResource(R.string.settings_animations), settings.animateMoves) { v -> onChange { it.copy(animateMoves = v) } }
            SwitchRow(stringResource(R.string.settings_haptics), settings.haptics) { v -> onChange { it.copy(haptics = v) } }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = ZorixColors.RedBright,
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = NumberStyle, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun <T> ChipRow(values: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { v ->
            FilterChip(selected = v == selected, onClick = { onSelect(v) }, label = { Text(label(v)) })
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ThemeSwatch(theme: BoardThemeId, selected: Boolean, onClick: () -> Unit) {
    val colors = boardColors(theme)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) ZorixColors.Red else MaterialTheme.colorScheme.outline),
                    RoundedCornerShape(10.dp),
                )
                .clickable(onClick = onClick),
        ) {
            val s = size.width / 4
            for (r in 0..3) for (c in 0..3) {
                drawRect(if ((r + c) % 2 == 0) colors.light else colors.dark, Offset(c * s, r * s), Size(s, s))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(theme.label()), style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(60.dp), maxLines = 1)
    }
}
