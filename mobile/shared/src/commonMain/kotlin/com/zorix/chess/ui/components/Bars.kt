package com.zorix.chess.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorix.chess.resources.*
import org.jetbrains.compose.resources.StringResource
import com.zorix.chess.controller.MoveFeedback
import com.zorix.chess.controller.feedbackKey
import com.zorix.chess.core.Game
import com.zorix.chess.ui.color
import com.zorix.chess.ui.symbol
import com.zorix.chess.core.Side
import com.zorix.chess.ui.theme.ZorixColors

/** Actions of the top-bar overflow menu. */
enum class MenuAction(val labelRes: StringResource, val icon: ImageVector) {
    NEW_GAME(Res.string.action_new_game, AppIcons.Add),
    EDIT_POSITION(Res.string.action_edit_position, AppIcons.Edit),
    LOAD_FEN(Res.string.action_load_fen, AppIcons.Paste),
    COPY_FEN(Res.string.action_copy_fen, AppIcons.Copy),
    COPY_PGN(Res.string.action_copy_pgn, AppIcons.Copy),
    SHARE_PGN(Res.string.action_share_pgn, AppIcons.Share),
    ABOUT(Res.string.action_about, AppIcons.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZorixTopBar(
    analysisOn: Boolean,
    onToggleAnalysis: (Boolean) -> Unit,
    onMenu: (MenuAction) -> Unit,
    onLearn: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(Res.drawable.zc_emblem),
                        contentDescription = null,
                        modifier = Modifier.height(26.dp).width(43.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    ZorixWordmark(fontSize = 15.sp)
                }
            }
        },
        actions = {
            if (onLearn != null) {
                IconButton(onClick = onLearn) {
                    Icon(AppIcons.School, contentDescription = stringResource(Res.string.action_learn), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            IconToggleButton(checked = analysisOn, onCheckedChange = onToggleAnalysis) {
                Icon(
                    AppIcons.Analysis,
                    contentDescription = stringResource(Res.string.action_analysis),
                    tint = if (analysisOn) ZorixColors.RedBright else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(AppIcons.More, contentDescription = stringResource(Res.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuAction.entries.forEachIndexed { i, action ->
                        if (i == 3 || i == 6) HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(action.labelRes)) },
                            leadingIcon = { Icon(action.icon, null) },
                            onClick = {
                                menuOpen = false
                                onMenu(action)
                            },
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

/** Turn indicator shown above the board. */
@Composable
fun TurnIndicator(side: Side, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (side == Side.WHITE) ZorixColors.EvalWhite else Color(0xFF050507)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (side == Side.WHITE) Res.string.turn_white else Res.string.turn_black),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Horizontal move list with navigation. Tapping a move jumps to that position. */
@Composable
fun MoveStrip(
    game: Game,
    onGoTo: (Int) -> Unit,
    modifier: Modifier = Modifier,
    feedback: Map<String, MoveFeedback> = emptyMap(),
) {
    val line = game.fullLine
    val current = game.plies.size
    val blackStarts = game.start.sideToMove == Side.BLACK
    val listState = rememberLazyListState()
    LaunchedEffect(current, line.size) {
        if (current > 0) listState.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onGoTo(0) }, enabled = current > 0) {
                Icon(AppIcons.FirstPage, stringResource(Res.string.action_first))
            }
            IconButton(onClick = { onGoTo(current - 1) }, enabled = current > 0) {
                Icon(AppIcons.ChevronLeft, stringResource(Res.string.action_previous))
            }
            if (line.isEmpty()) {
                Text(
                    stringResource(Res.string.moves_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            } else {
                LazyRow(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    itemsIndexed(line) { index, ply ->
                        val offset = if (blackStarts) index + 1 else index
                        val number = game.start.fullmoveNumber + offset / 2
                        val whiteMove = offset % 2 == 0
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (whiteMove || index == 0) {
                                Text(
                                    if (whiteMove) "$number." else "$number…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 6.dp, end = 2.dp),
                                )
                            }
                            val isCurrent = index == current - 1
                            val isFuture = index >= current
                            Text(
                                ply.san,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                ),
                                color = when {
                                    isCurrent -> Color.White
                                    isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) ZorixColors.Red else Color.Transparent)
                                    .clickable { onGoTo(index + 1) }
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                            )
                            feedback[feedbackKey(ply.before.fen(), ply.move)]?.let { f ->
                                Text(
                                    f.quality.symbol,
                                    color = f.quality.color,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                    modifier = Modifier.padding(end = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
            IconButton(onClick = { onGoTo(current + 1) }, enabled = current < line.size) {
                Icon(AppIcons.ChevronRight, stringResource(Res.string.action_next))
            }
            IconButton(onClick = { onGoTo(line.size) }, enabled = current < line.size) {
                Icon(AppIcons.LastPage, stringResource(Res.string.action_last))
            }
        }
    }
}

/** Bottom action bar with the big "Best move" button in the middle. */
@Composable
fun ActionBar(
    canUndo: Boolean,
    canRedo: Boolean,
    thinking: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBestMove: () -> Unit,
    onStop: () -> Unit,
    onFlip: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    applyNavigationInsets: Boolean = true,
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (applyNavigationInsets) Modifier.navigationBarsPadding() else Modifier)
                .heightIn(min = 76.dp)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionItem(AppIcons.Undo, stringResource(Res.string.action_undo), canUndo, onUndo)
            ActionItem(AppIcons.Redo, stringResource(Res.string.action_redo), canRedo, onRedo)
            BestMoveButton(thinking, onBestMove, onStop)
            ActionItem(AppIcons.Flip, stringResource(Res.string.action_flip), true, onFlip)
            ActionItem(AppIcons.Settings, stringResource(Res.string.action_settings), true, onSettings)
        }
    }
}

@Composable
private fun RowScope.ActionItem(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

@Composable
private fun RowScope.BestMoveButton(thinking: Boolean, onBestMove: () -> Unit, onStop: () -> Unit) {
    Box(
        Modifier
            .weight(2.1f)
            .padding(horizontal = 4.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFF23A44), Color(0xFFC3121C))))
            .clickable(onClick = if (thinking) onStop else onBestMove),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (thinking) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Icon(AppIcons.Bulb, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (thinking) Res.string.action_stop else Res.string.action_best_move),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 16.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
