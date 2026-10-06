package com.zorix.chess.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zorix.chess.resources.*
import com.zorix.chess.ui.theme.ZorixColors
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringArrayResource
import org.jetbrains.compose.resources.stringResource

/** Every screen has a "?" button that explains what the screen is for and how to use it. */
enum class HelpTopic(val title: StringResource, val sections: StringArrayResource) {
    ANALYSIS(Res.string.help_analysis_title, Res.array.help_analysis),
    BUILDER(Res.string.help_builder_title, Res.array.help_builder),
    PLAY(Res.string.help_play_title, Res.array.help_play),
    GAME(Res.string.help_game_title, Res.array.help_game),
    REVIEW(Res.string.help_review_title, Res.array.help_review),
    LEARN(Res.string.help_learn_title, Res.array.help_learn),
    LESSON(Res.string.help_lesson_title, Res.array.help_lesson),
    PUZZLES(Res.string.help_puzzles_title, Res.array.help_puzzles),
    SETTINGS(Res.string.help_settings_title, Res.array.help_settings),
}

@Composable
fun HelpButton(topic: HelpTopic, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) {
        Icon(AppIcons.Help, contentDescription = stringResource(Res.string.action_help), tint = ZorixColors.RedBright)
    }
    if (open) HelpDialog(topic) { open = false }
}

@Composable
fun HelpDialog(topic: HelpTopic, onDismiss: () -> Unit) {
    val sections = stringArrayResource(topic.sections)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Help, null, tint = ZorixColors.Red) },
        title = { Text(stringResource(topic.title)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                sections.forEachIndexed { i, item ->
                    val heading = item.substringBefore('|', "").trim()
                    val body = item.substringAfter('|').trim()
                    if (i > 0) Spacer(Modifier.height(12.dp))
                    if (heading.isNotEmpty()) {
                        Text(heading, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), color = ZorixColors.RedBright)
                        Spacer(Modifier.height(3.dp))
                    }
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.help_got_it)) } },
    )
}
