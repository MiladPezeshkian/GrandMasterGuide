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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zorix.chess.resources.*
import com.zorix.chess.controller.AppThemeId
import com.zorix.chess.controller.BoardThemeId
import com.zorix.chess.controller.EngineStatus
import com.zorix.chess.controller.Settings
import com.zorix.chess.ui.board.boardColors
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.formatSeconds
import com.zorix.chess.ui.label
import com.zorix.chess.ui.theme.NumberStyle
import com.zorix.chess.ui.theme.ZorixColors
import com.zorix.chess.ui.theme.paletteOf
import com.zorix.chess.ui.AboutDialog
import com.zorix.chess.ui.components.HelpButton
import com.zorix.chess.ui.components.HelpTopic
import com.zorix.chess.ui.components.ScreenHeader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Brush
import kotlin.math.roundToInt

/** The settings tab: profile, appearance, language, coach, engine and board options, and About. */
@Composable
fun SettingsScreen(
    settings: Settings,
    engine: EngineStatus,
    maxThreads: Int,
    versionName: String,
    onChange: ((Settings) -> Settings) -> Unit,
    profileName: String,
    onRename: (String) -> Unit,
) {
    var showAbout by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(Res.string.settings_title), actions = { HelpButton(HelpTopic.SETTINGS) })
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            SectionTitle(stringResource(Res.string.settings_profile))
            var name by remember(profileName) { mutableStateOf(profileName) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24); onRename(name) },
                singleLine = true,
                label = { Text(stringResource(Res.string.onboard_name_hint)) },
                leadingIcon = { Icon(AppIcons.Person, null) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(Res.string.settings_appearance))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppThemeId.entries.forEach { theme ->
                    AppThemeCard(theme, selected = settings.appTheme == theme, modifier = Modifier.weight(1f)) {
                        onChange { it.copy(appTheme = theme) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(Res.string.settings_language))
            val languages = listOf(null to stringResource(Res.string.language_system), "fa" to "فارسی", "ckb" to "کوردی", "en" to "English")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                languages.forEach { (code, label) ->
                    FilterChip(
                        selected = settings.language == code,
                        onClick = { onChange { it.copy(language = code) } },
                        label = { Text(label) },
                        leadingIcon = if (code == null) ({ Icon(AppIcons.Language, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(Res.string.settings_learning))
            SwitchRow(stringResource(Res.string.settings_coach), settings.coachMode) { v -> onChange { it.copy(coachMode = v) } }
            Hint(stringResource(Res.string.settings_coach_hint))
            SwitchRow(stringResource(Res.string.settings_voice), settings.voice) { v -> onChange { it.copy(voice = v) } }
            Hint(stringResource(Res.string.settings_voice_hint))
            SwitchRow(stringResource(Res.string.settings_explain_bot), settings.explainBotMoves) { v -> onChange { it.copy(explainBotMoves = v) } }
            Hint(stringResource(Res.string.settings_explain_bot_hint))
            Spacer(Modifier.height(24.dp))

            SectionTitle(stringResource(Res.string.settings_engine))
            ValueRow(stringResource(Res.string.settings_think_time), stringResource(Res.string.think_time_value, formatSeconds(settings.thinkTimeMs)))
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
            Hint(stringResource(Res.string.settings_think_time_hint))

            if (maxThreads > 1) {
                Spacer(Modifier.height(12.dp))
                ValueRow(stringResource(Res.string.settings_threads), settings.threads.toString())
                Slider(
                    value = settings.threads.toFloat(),
                    onValueChange = { v -> onChange { it.copy(threads = v.roundToInt().coerceIn(1, maxThreads)) } },
                    valueRange = 1f..maxThreads.toFloat(),
                    steps = (maxThreads - 2).coerceAtLeast(0),
                )
                Hint(stringResource(Res.string.settings_threads_hint, maxThreads))
            }

            Spacer(Modifier.height(12.dp))
            ValueRow(stringResource(Res.string.settings_hash), stringResource(Res.string.mb_value, settings.hashMb))
            ChipRow(Settings.HASH_CHOICES, settings.hashMb, { it.toString() }) { mb ->
                onChange { it.copy(hashMb = mb) }
            }
            Hint(stringResource(Res.string.settings_hash_hint))
            Spacer(Modifier.height(12.dp))

            ValueRow(stringResource(Res.string.settings_lines), settings.analysisLines.toString())
            ChipRow((1..Settings.MAX_LINES).toList(), settings.analysisLines, { it.toString() }) { n ->
                onChange { it.copy(analysisLines = n) }
            }
            Hint(stringResource(Res.string.settings_lines_hint))

            if (engine is EngineStatus.Ready) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.settings_engine_info, stringResource(Res.string.engine_name), engine.build),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(Res.string.settings_board))
            Text(stringResource(Res.string.settings_theme), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BoardThemeId.entries.forEach { theme ->
                        ThemeSwatch(theme, selected = theme == settings.boardTheme) { onChange { it.copy(boardTheme = theme) } }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SwitchRow(stringResource(Res.string.settings_coordinates), settings.showCoordinates) { v -> onChange { it.copy(showCoordinates = v) } }
            SwitchRow(stringResource(Res.string.settings_legal_moves), settings.showLegalMoves) { v -> onChange { it.copy(showLegalMoves = v) } }
            SwitchRow(stringResource(Res.string.settings_arrows), settings.showArrows) { v -> onChange { it.copy(showArrows = v) } }
            SwitchRow(stringResource(Res.string.settings_animations), settings.animateMoves) { v -> onChange { it.copy(animateMoves = v) } }
            SwitchRow(stringResource(Res.string.settings_haptics), settings.haptics) { v -> onChange { it.copy(haptics = v) } }

            Spacer(Modifier.height(24.dp))
            SectionTitle(stringResource(Res.string.action_about))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clickable { showAbout = true }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Info, null, tint = ZorixColors.Red)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(Res.string.about_version, versionName), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showAbout) AboutDialog(versionName) { showAbout = false }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
}

/** A small preview of an app style: its background, a card and the accent colour. */
@Composable
private fun AppThemeCard(theme: AppThemeId, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = paletteOf(theme)
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) ZorixColors.Red else MaterialTheme.colorScheme.outline), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(p.background)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp).clip(RoundedCornerShape(9.dp)).background(Brush.verticalGradient(p.buttonGradient)))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.height(8.dp).weight(1f).clip(RoundedCornerShape(4.dp)).background(p.surfaceHighest))
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(8.dp)).background(p.surfaceHigh))
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(if (theme == AppThemeId.ZORIX) Res.string.theme_zorix else Res.string.theme_sky),
            style = MaterialTheme.typography.labelLarge,
            color = p.text,
        )
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
