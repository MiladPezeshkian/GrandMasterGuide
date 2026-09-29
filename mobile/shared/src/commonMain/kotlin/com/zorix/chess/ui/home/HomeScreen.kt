package com.zorix.chess.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zorix.chess.controller.Profile
import com.zorix.chess.learn.Course
import com.zorix.chess.learn.Lesson
import com.zorix.chess.play.Bot
import com.zorix.chess.resources.*
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.CoachBubble
import com.zorix.chess.ui.components.HeroCard
import com.zorix.chess.ui.components.PrimaryButton
import com.zorix.chess.ui.components.SectionTitle
import com.zorix.chess.ui.components.StatTile
import com.zorix.chess.ui.components.ZCard
import com.zorix.chess.ui.components.ZorixWordmark
import com.zorix.chess.ui.theme.ZorixColors
import com.zorix.chess.ui.localized
import com.zorix.chess.ui.play.botName
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Start screen: the player's ratings, what to learn next, and quick ways to play. */
@Composable
fun HomeScreen(
    profile: Profile,
    lang: String,
    next: Pair<Course, Lesson>?,
    lessonsTotal: Int,
    recommended: Bot,
    onContinue: (Course, Lesson) -> Unit,
    onPlay: (Bot) -> Unit,
    onPuzzles: () -> Unit,
    onAnalysis: () -> Unit,
    onSettings: () -> Unit,
    onSpeak: (String) -> Unit,
) {
    val tips = listOf(
        stringResource(Res.string.tip_day_1), stringResource(Res.string.tip_day_2), stringResource(Res.string.tip_day_3),
        stringResource(Res.string.tip_day_4), stringResource(Res.string.tip_day_5), stringResource(Res.string.tip_day_6),
        stringResource(Res.string.tip_day_7),
    )
    val tip = remember(profile.xp / 25) { tips[(profile.xp / 25) % tips.size] }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(Res.drawable.zc_emblem), null, Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                ZorixWordmark(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(AppIcons.Settings, stringResource(Res.string.action_settings)) }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(Res.string.home_hello, profile.name.ifBlank { stringResource(Res.string.default_player) }),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
            Text(stringResource(Res.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(AppIcons.Trophy, profile.rating.toString(), stringResource(Res.string.stat_rating), Modifier.weight(1f))
                StatTile(AppIcons.Puzzle, profile.puzzleRating.toString(), stringResource(Res.string.stat_puzzle_rating), Modifier.weight(1f), tint = ZorixColors.Line2)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(AppIcons.School, "${profile.lessonsCompleted}/$lessonsTotal", stringResource(Res.string.stat_lessons), Modifier.weight(1f), tint = ZorixColors.Best)
                StatTile(AppIcons.Bolt, profile.xp.toString(), stringResource(Res.string.stat_xp), Modifier.weight(1f), tint = Color(0xFFFFC53D))
            }

            SectionTitle(stringResource(Res.string.home_continue))
            if (next != null) {
                val (course, lesson) = next
                HeroCard(Modifier.fillMaxWidth(), onClick = { onContinue(course, lesson) }) {
                    Column {
                        Text(course.title.localized(lang), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.75f))
                        Spacer(Modifier.height(4.dp))
                        Text(lesson.title.localized(lang), style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Play, null, tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(Res.string.home_start_lesson), color = Color.White, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else {
                ZCard(Modifier.fillMaxWidth()) { Text(stringResource(Res.string.home_all_done)) }
            }

            SectionTitle(stringResource(Res.string.home_play_title))
            ZCard(Modifier.fillMaxWidth(), onClick = { onPlay(recommended) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Trophy, null, tint = ZorixColors.RedBright, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.home_play_recommended), style = MaterialTheme.typography.titleMedium)
                        Text("${botName(recommended)} · ${recommended.elo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(AppIcons.ChevronRight, null)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZCard(Modifier.weight(1f), onClick = onPuzzles) {
                    Column {
                        Icon(AppIcons.Puzzle, null, tint = ZorixColors.Line2)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(Res.string.tab_puzzles), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(Res.string.home_puzzle_streak, profile.bestPuzzleStreak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ZCard(Modifier.weight(1f), onClick = onAnalysis) {
                    Column {
                        Icon(AppIcons.Analysis, null, tint = ZorixColors.Best)
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(Res.string.tab_analysis), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(Res.string.home_analysis_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            SectionTitle(stringResource(Res.string.home_tip_title))
            CoachBubble(tip, onSpeak = { onSpeak(tip) })
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                PrimaryButton(stringResource(Res.string.home_play_button), { onPlay(recommended) }, icon = AppIcons.Play)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
