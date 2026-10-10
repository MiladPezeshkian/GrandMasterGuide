package com.zorix.chess.ui.play

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.zorix.chess.controller.GameOutcome
import com.zorix.chess.controller.Profile
import com.zorix.chess.controller.Settings
import com.zorix.chess.core.Side
import com.zorix.chess.play.Bot
import com.zorix.chess.play.BotTier
import com.zorix.chess.play.Bots
import com.zorix.chess.play.PlayController
import com.zorix.chess.play.PlayState
import com.zorix.chess.resources.*
import com.zorix.chess.ui.board.BoardArrow
import com.zorix.chess.ui.board.ChessBoard
import com.zorix.chess.ui.board.PieceImages
import com.zorix.chess.ui.board.boardColors
import com.zorix.chess.ui.color
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.CoachAvatar
import com.zorix.chess.ui.components.HelpButton
import com.zorix.chess.ui.components.HelpTopic
import com.zorix.chess.ui.components.CoachBubble
import com.zorix.chess.ui.components.MoveStrip
import com.zorix.chess.ui.components.Pill
import com.zorix.chess.ui.components.PrimaryButton
import com.zorix.chess.ui.components.ScreenHeader
import com.zorix.chess.ui.components.SecondaryButton
import com.zorix.chess.ui.components.StarRow
import com.zorix.chess.ui.components.ZCard
import com.zorix.chess.ui.label
import com.zorix.chess.ui.symbol
import com.zorix.chess.ui.theme.ZorixColors
import org.jetbrains.compose.resources.stringResource

val BotTier.color: Color
    get() = when (this) {
        BotTier.BEGINNER -> Color(0xFF6CCB5F)
        BotTier.NOVICE -> Color(0xFF3FB5A6)
        BotTier.INTERMEDIATE -> Color(0xFF4F9CFF)
        BotTier.ADVANCED -> Color(0xFF8C6CFF)
        BotTier.EXPERT -> Color(0xFFFF9F45)
        BotTier.MASTER -> Color(0xFFFF6B6B)
        BotTier.GRANDMASTER -> Color(0xFFE3202B)
        BotTier.ZORIX -> Color(0xFFFFC53D)
    }

@Composable
fun tierName(tier: BotTier): String = stringResource(
    when (tier) {
        BotTier.BEGINNER -> Res.string.tier_beginner
        BotTier.NOVICE -> Res.string.tier_novice
        BotTier.INTERMEDIATE -> Res.string.tier_intermediate
        BotTier.ADVANCED -> Res.string.tier_advanced
        BotTier.EXPERT -> Res.string.tier_expert
        BotTier.MASTER -> Res.string.tier_master
        BotTier.GRANDMASTER -> Res.string.tier_grandmaster
        BotTier.ZORIX -> Res.string.tier_zorix
    },
)

@Composable
fun botName(bot: Bot): String = stringResource(Res.string.bot_name, bot.level)

/** The level map: 20 Zorix opponents, unlocked by winning. */
@Composable
fun PlayHub(profile: Profile, active: PlayState, onResume: () -> Unit, onStart: (Bot, Side?) -> Unit) {
    val recommended = Bots.recommended(profile.rating)
    var chosen by remember { mutableStateOf<Bot?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(stringResource(Res.string.play_title), subtitle = stringResource(Res.string.play_subtitle_plain), actions = { HelpButton(HelpTopic.PLAY) })
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (active.active && active.result == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ZCard(Modifier.fillMaxWidth(), onClick = onResume, color = ZorixColors.RedDeep) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(AppIcons.Play, null, tint = Color.White)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(Res.string.play_resume), style = MaterialTheme.typography.titleMedium, color = Color.White)
                                Text("${botName(active.bot)} · ${tierName(active.bot.tier)}", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(Res.string.play_levels_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
            }
            items(Bots.all) { bot ->
                BotCard(bot, locked = false, stars = profile.levelStars[bot.level] ?: 0, recommended = bot == recommended) {
                    chosen = bot
                }
            }
        }
    }
    chosen?.let { bot ->
        AlertDialog(
            onDismissRequest = { chosen = null },
            title = { Text(botName(bot)) },
            text = {
                Column {
                    Text(tierName(bot.tier), color = bot.tier.color, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(Res.string.play_choose_color))
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { chosen = null; onStart(bot, Side.WHITE) }) { Text(stringResource(Res.string.side_white)) }
                    TextButton(onClick = { chosen = null; onStart(bot, Side.BLACK) }) { Text(stringResource(Res.string.side_black)) }
                    TextButton(onClick = { chosen = null; onStart(bot, null) }) { Text(stringResource(Res.string.play_random)) }
                }
            },
        )
    }
}

@Composable
private fun BotCard(bot: Bot, locked: Boolean, stars: Int, recommended: Boolean, onClick: () -> Unit) {
    val tint = bot.tier.color
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(2.dp, if (recommended && !locked) ZorixColors.Red else Color.Transparent, RoundedCornerShape(20.dp))
            .clickable(enabled = !locked, onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = if (locked) 0.25f else 0.9f)), contentAlignment = Alignment.Center) {
                if (locked) Icon(AppIcons.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                else Text(bot.level.toString(), color = Color(0xFF101014), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black))
            }
            Spacer(Modifier.weight(1f))
            StarRow(stars, size = 14.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(botName(bot), style = MaterialTheme.typography.titleSmall, color = if (locked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        Text(tierName(bot.tier), style = MaterialTheme.typography.labelMedium, color = tint)
        if (recommended && !locked) {
            Spacer(Modifier.height(6.dp))
            Pill(stringResource(Res.string.play_recommended), ZorixColors.Red)
        }
    }
}

/** The game against Zorix with the coach at your side. */
@Composable
fun GameScreen(
    state: PlayState,
    play: PlayController,
    profile: Profile,
    settings: Settings,
    pieces: PieceImages,
    onBack: () -> Unit,
    onReview: () -> Unit,
    onNextLevel: (Bot) -> Unit,
) {
    var confirmResign by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(
            botName(state.bot),
            onBack = onBack,
            subtitle = tierName(state.bot.tier),
            actions = {
                IconButton(onClick = play::speakAgain) { Icon(AppIcons.VolumeUp, stringResource(Res.string.action_listen)) }
                HelpButton(HelpTopic.GAME)
            },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boardSize = min(maxWidth - 24.dp, maxHeight * 0.58f)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                PlayerBar(
                    name = botName(state.bot), isZorix = true,
                    thinking = state.botThinking, modifier = Modifier.width(boardSize),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    val hint = state.hint?.bestMove
                    ChessBoard(
                        position = state.game.position,
                        lastMove = state.game.lastMove,
                        flipped = state.userSide == Side.BLACK,
                        colors = boardColors(settings.boardTheme),
                        pieces = pieces,
                        modifier = Modifier.size(boardSize).clip(RoundedCornerShape(6.dp)),
                        arrows = listOfNotNull(hint?.let { BoardArrow(it.from, it.to, ZorixColors.Best.copy(alpha = 0.9f), 0.18f) }),
                        showCoordinates = settings.showCoordinates,
                        showLegalMoves = settings.showLegalMoves,
                        animateMoves = settings.animateMoves,
                        interactive = state.userToMove && !state.botThinking,
                        promotion = state.pendingPromotion,
                        onMove = play::onUserMove,
                        onPromotion = { t -> if (t == null) play.onPromotionCancelled() else play.onPromotionChosen(t) },
                    )
                }
                PlayerBar(name = profile.name.ifBlank { stringResource(Res.string.default_player) }, isZorix = false, thinking = false, modifier = Modifier.width(boardSize))
                MoveStrip(state.game, onGoTo = {}, modifier = Modifier.padding(horizontal = 8.dp), feedback = state.feedback)
                Column(Modifier.padding(horizontal = 12.dp)) {
                    val fb = state.lastFeedback
                    when {
                        state.hint != null -> CoachBubble(state.hint.display, waitForVoice = true, badge = { Pill(stringResource(Res.string.action_hint), ZorixColors.Best, textColor = Color(0xFF101014)) }, onSpeak = play::speakAgain)
                        state.coachBusy -> CoachBubble(null, busy = true)
                        fb != null && (state.botMessage == null || !state.userToMove) -> CoachBubble(
                            fb.message.ifBlank { null },
                            badge = { Pill("${fb.quality.symbol} ${stringResource(fb.quality.label())}", fb.quality.color, textColor = Color(0xFF101014)) },
                            onSpeak = play::speakAgain,
                            waitForVoice = true,
                        )
                        state.botMessage != null -> CoachBubble(state.botMessage.display, title = "Zorix", onSpeak = play::speakAgain, waitForVoice = true)
                    }
                    if (state.drawDeclined) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.play_draw_declined), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            GameAction(AppIcons.Bulb, stringResource(Res.string.action_hint), enabled = state.userToMove && !state.hintBusy, busy = state.hintBusy, onClick = play::hint)
            GameAction(AppIcons.Undo, stringResource(Res.string.action_takeback), enabled = state.result == null && state.game.plies.isNotEmpty() && !state.botThinking, onClick = play::takeback)
            GameAction(null, stringResource(Res.string.action_draw), text = "½", enabled = state.userToMove, onClick = play::offerDraw)
            GameAction(AppIcons.Flag, stringResource(Res.string.action_resign), enabled = state.result == null, onClick = { confirmResign = true })
        }
    }
    if (confirmResign) {
        AlertDialog(
            onDismissRequest = { confirmResign = false },
            title = { Text(stringResource(Res.string.resign_title)) },
            text = { Text(stringResource(Res.string.resign_body)) },
            confirmButton = { TextButton(onClick = { confirmResign = false; play.resign() }) { Text(stringResource(Res.string.action_resign)) } },
            dismissButton = { TextButton(onClick = { confirmResign = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    if (state.result != null) {
        ResultDialog(state, onReview = onReview, onRematch = play::rematch, onClose = onBack, onNext = {
            if (state.bot.level < Bots.all.size) onNextLevel(Bots.byLevel(state.bot.level + 1))
        })
    }
}

@Composable
private fun PlayerBar(name: String, isZorix: Boolean, thinking: Boolean, modifier: Modifier) {
    Row(modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isZorix) CoachAvatar(32.dp) else Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(AppIcons.Person, null, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(name, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.weight(1f))
        if (thinking) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.zorix_thinking), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GameAction(icon: androidx.compose.ui.graphics.vector.ImageVector?, label: String, enabled: Boolean, onClick: () -> Unit, text: String? = null, busy: Boolean = false) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val c = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            when {
                busy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                icon != null -> Icon(icon, null, tint = c)
                else -> Text(text.orEmpty(), color = c, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = c)
    }
}

@Composable
private fun ResultDialog(state: PlayState, onReview: () -> Unit, onRematch: () -> Unit, onClose: () -> Unit, onNext: () -> Unit) {
    val r = state.result ?: return
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(if (r.outcome == GameOutcome.WIN) AppIcons.Trophy else AppIcons.Flag, null, tint = if (r.outcome == GameOutcome.WIN) Color(0xFFFFC53D) else MaterialTheme.colorScheme.onSurfaceVariant) },
        title = {
            Text(
                stringResource(
                    when (r.outcome) {
                        GameOutcome.WIN -> Res.string.result_win
                        GameOutcome.DRAW -> Res.string.result_draw
                        GameOutcome.LOSS -> Res.string.result_loss
                    },
                ),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    stringResource(Res.string.result_review_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(Res.string.action_review), onReview, Modifier.fillMaxWidth(), icon = AppIcons.Chart)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton(stringResource(Res.string.action_rematch), onRematch, Modifier.weight(1f), icon = AppIcons.Replay)
                    if (r.outcome == GameOutcome.WIN && state.bot.level < Bots.all.size) {
                        SecondaryButton(stringResource(Res.string.action_next_level), onNext, Modifier.weight(1f), icon = AppIcons.ArrowForward)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(Res.string.action_close)) } },
    )
}
