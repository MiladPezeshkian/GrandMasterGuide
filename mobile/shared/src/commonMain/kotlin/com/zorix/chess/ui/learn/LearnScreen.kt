package com.zorix.chess.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zorix.chess.controller.PendingPromotion
import com.zorix.chess.core.Move
import com.zorix.chess.core.PieceType
import com.zorix.chess.core.Position
import com.zorix.chess.core.Side
import com.zorix.chess.core.Tactics
import com.zorix.chess.learn.Puzzle
import com.zorix.chess.learn.PuzzleSet
import com.zorix.chess.resources.*
import com.zorix.chess.ui.board.BoardColors
import com.zorix.chess.ui.board.ChessBoard
import com.zorix.chess.ui.board.PieceImages
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.label
import com.zorix.chess.ui.theme.ZorixColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Learn section: checkmate puzzles graded offline and interactive rules lessons. */
@Composable
fun LearnScreen(
    colors: BoardColors,
    pieces: PieceImages,
    showCoordinates: Boolean,
    solved: Set<Int>,
    onSolved: (Int) -> Unit,
    onClose: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var solvedIds by remember { mutableStateOf(solved) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(AppIcons.Close, stringResource(Res.string.action_close)) }
                Icon(AppIcons.School, null, tint = ZorixColors.RedBright)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(Res.string.learn_title), style = MaterialTheme.typography.titleLarge)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TabButton(stringResource(Res.string.learn_puzzles), AppIcons.Puzzle, tab == 0, Modifier.weight(1f)) { tab = 0 }
                TabButton(stringResource(Res.string.learn_lessons), AppIcons.School, tab == 1, Modifier.weight(1f)) { tab = 1 }
            }
            Spacer(Modifier.height(8.dp))
            if (tab == 0) {
                PuzzlesTab(colors, pieces, showCoordinates, solvedIds) { id ->
                    solvedIds = solvedIds + id
                    onSolved(id)
                }
            } else {
                LessonsTab(colors, pieces, showCoordinates)
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ZorixColors.Red else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

// ---------------------------------------------------------------------------------------------- puzzles

private enum class PuzzleStatus { SOLVING, WRONG, KEEP_GOING, SOLVED }

@Composable
private fun PuzzlesTab(
    colors: BoardColors,
    pieces: PieceImages,
    showCoordinates: Boolean,
    solved: Set<Int>,
    onSolved: (Int) -> Unit,
) {
    val all = PuzzleSet.all
    var index by rememberSaveable { mutableIntStateOf(all.indexOfFirst { it.id !in solved }.coerceAtLeast(0)) }
    val puzzle = all[index]
    val scope = rememberCoroutineScope()

    var position by remember(puzzle.id) { mutableStateOf(Position.fromFen(puzzle.fen)) }
    var lastMove by remember(puzzle.id) { mutableStateOf<Move?>(null) }
    var stage by remember(puzzle.id) { mutableIntStateOf(0) }
    var status by remember(puzzle.id) { mutableStateOf(PuzzleStatus.SOLVING) }
    var hint by remember(puzzle.id) { mutableStateOf<Int?>(null) }
    var pending by remember(puzzle.id) { mutableStateOf<PendingPromotion?>(null) }
    var busy by remember(puzzle.id) { mutableStateOf(false) }
    val solver = Side.entries.first { it == Position.fromFen(puzzle.fen).sideToMove }

    fun attempt(move: Move) {
        if (busy || status == PuzzleStatus.SOLVED || !position.isLegal(move)) return
        busy = true
        hint = null
        val before = position
        scope.launch {
            val remaining = puzzle.mateIn - stage
            val correct = withContext(Dispatchers.Default) { Tactics.forcesMate(before, move, remaining) }
            val after = before.play(move)
            position = after
            lastMove = move
            if (correct && Tactics.isCheckmate(after)) {
                status = PuzzleStatus.SOLVED
                onSolved(puzzle.id)
            } else if (correct) {
                status = PuzzleStatus.KEEP_GOING
                stage++
                val reply = withContext(Dispatchers.Default) { Tactics.bestDefence(before, move, remaining) }
                delay(550)
                if (reply != null) {
                    position = after.play(reply)
                    lastMove = reply
                }
            } else {
                status = PuzzleStatus.WRONG
                delay(700)
                position = before
                lastMove = null
            }
            busy = false
        }
    }

    fun nextSolutionMove(): Move? =
        if (stage == 0) Move.fromUci(puzzle.solution)?.takeIf { position.isLegal(it) } ?: Tactics.matingMoves(position).firstOrNull()
        else Tactics.matingMoves(position).firstOrNull()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val done = all.count { it.id in solved }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(Res.string.learn_progress, done, all.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { done.toFloat() / all.size },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = ZorixColors.Best,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
        Spacer(Modifier.height(10.dp))
        PuzzlePicker(all, index, solved) { index = it }
        Spacer(Modifier.height(10.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(Res.string.puzzle_title, puzzle.id), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(ZorixColors.RedDeep).padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    stringResource(if (puzzle.mateIn == 1) Res.string.puzzle_mate1 else Res.string.puzzle_mate2),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ChessBoard(
                position = position,
                lastMove = lastMove,
                flipped = solver == Side.BLACK,
                colors = colors,
                pieces = pieces,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                showCoordinates = showCoordinates,
                interactive = status != PuzzleStatus.SOLVED && !busy && position.sideToMove == solver,
                promotion = pending,
                highlights = hint?.let { mapOf(it to Color(0x9926C281)) } ?: emptyMap(),
                onMove = { from, to ->
                    if (position.isPromotion(from, to)) {
                        pending = PendingPromotion(from, to, position.sideToMove)
                    } else {
                        attempt(Move(from, to))
                    }
                },
                onPromotion = { type ->
                    val p = pending
                    pending = null
                    if (p != null && type != null) attempt(Move(p.from, p.to, type))
                },
            )
        }
        Spacer(Modifier.height(12.dp))

        val (message, tint) = when (status) {
            PuzzleStatus.SOLVING -> stringResource(Res.string.puzzle_goal, stringResource(solver.label()), puzzle.mateIn - stage) to MaterialTheme.colorScheme.onSurface
            PuzzleStatus.WRONG -> stringResource(Res.string.puzzle_wrong) to MaterialTheme.colorScheme.error
            PuzzleStatus.KEEP_GOING -> stringResource(Res.string.puzzle_correct) to ZorixColors.Best
            PuzzleStatus.SOLVED -> stringResource(Res.string.puzzle_solved) to ZorixColors.Best
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp)) {
                Text(message, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = tint)
                if (hint != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(Res.string.puzzle_hint_text), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { hint = nextSolutionMove()?.from },
                enabled = status != PuzzleStatus.SOLVED && !busy,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(AppIcons.Bulb, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.puzzle_hint), maxLines = 1)
            }
            OutlinedButton(
                onClick = { nextSolutionMove()?.let { attempt(it) } },
                enabled = status != PuzzleStatus.SOLVED && !busy,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(AppIcons.Play, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.puzzle_solution), maxLines = 1)
            }
            OutlinedButton(
                onClick = {
                    position = Position.fromFen(puzzle.fen)
                    lastMove = null
                    stage = 0
                    status = PuzzleStatus.SOLVING
                    hint = null
                },
                enabled = !busy,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(AppIcons.Refresh, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(Res.string.puzzle_retry), maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { if (index > 0) index-- }, enabled = index > 0, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.puzzle_previous))
            }
            Button(onClick = { if (index < all.size - 1) index++ }, enabled = index < all.size - 1, modifier = Modifier.weight(1f)) {
                Text(stringResource(Res.string.puzzle_next))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PuzzlePicker(all: List<Puzzle>, index: Int, solved: Set<Int>, onPick: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(index) { state.animateScrollToItem((index - 2).coerceAtLeast(0)) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(all.size) { i ->
                val p = all[i]
                val isSolved = p.id in solved
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isSolved) ZorixColors.Best.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(2.dp, if (i == index) ZorixColors.Red else Color.Transparent, CircleShape)
                        .clickable { onPick(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        p.id.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSolved) ZorixColors.Best else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------- lessons

private data class Lesson(val title: StringResource, val body: StringResource, val fen: String)

private val LESSONS = listOf(
    Lesson(Res.string.lesson_king_title, Res.string.lesson_king_body, "8/8/8/8/3K4/8/8/7k w - - 0 1"),
    Lesson(Res.string.lesson_queen_title, Res.string.lesson_queen_body, "7k/8/8/8/3Q4/8/8/K7 w - - 0 1"),
    Lesson(Res.string.lesson_rook_title, Res.string.lesson_rook_body, "7k/8/8/8/3R4/8/8/K7 w - - 0 1"),
    Lesson(Res.string.lesson_bishop_title, Res.string.lesson_bishop_body, "7k/8/8/8/3B4/8/8/K7 w - - 0 1"),
    Lesson(Res.string.lesson_knight_title, Res.string.lesson_knight_body, "7k/8/8/8/3N4/8/8/K7 w - - 0 1"),
    Lesson(Res.string.lesson_pawn_title, Res.string.lesson_pawn_body, "7k/3P4/8/8/8/8/4P3/K7 w - - 0 1"),
    Lesson(Res.string.lesson_castling_title, Res.string.lesson_castling_body, "4k3/8/8/8/8/8/8/R3K2R w KQ - 0 1"),
    Lesson(Res.string.lesson_enpassant_title, Res.string.lesson_enpassant_body, "7k/8/8/3pP3/8/8/8/K7 w - d6 0 1"),
    Lesson(Res.string.lesson_checkmate_title, Res.string.lesson_checkmate_body, "6k1/5ppp/8/8/8/8/8/R5K1 w - - 0 1"),
)

@Composable
private fun LessonsTab(colors: BoardColors, pieces: PieceImages, showCoordinates: Boolean) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val lesson = LESSONS[selected]
    var position by remember(selected) { mutableStateOf(Position.fromFen(lesson.fen)) }
    var lastMove by remember(selected) { mutableStateOf<Move?>(null) }
    var pending by remember(selected) { mutableStateOf<PendingPromotion?>(null) }
    var mated by remember(selected) { mutableStateOf(false) }

    fun play(move: Move) {
        if (!position.isLegal(move)) return
        val after = position.play(move)
        lastMove = move
        mated = Tactics.isCheckmate(after)
        position = when {
            mated || after.legalMoves.isEmpty() -> after
            // A checked king must answer first; it steps out of check, then White moves again.
            after.isCheck -> {
                val escape = after.legalMoves.first()
                lastMove = escape
                after.play(escape).let { Position.of(it.pieces(), Side.WHITE, it.castling) }
            }
            // Sandbox: White keeps the move so the piece can be explored freely.
            else -> Position.of(after.pieces(), Side.WHITE, after.castling)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            items(LESSONS.size) { i ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (i == selected) ZorixColors.Red else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { selected = i }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) {
                    Text(stringResource(LESSONS[i].title), color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(lesson.title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = ZorixColors.RedBright)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(lesson.body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (mated) stringResource(Res.string.puzzle_solved) else stringResource(Res.string.lesson_try),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mated) ZorixColors.Best else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            ChessBoard(
                position = position,
                lastMove = lastMove,
                flipped = false,
                colors = colors,
                pieces = pieces,
                modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth().clip(RoundedCornerShape(6.dp)),
                showCoordinates = showCoordinates,
                interactive = !mated,
                promotion = pending,
                onMove = { from, to ->
                    if (position.isPromotion(from, to)) pending = PendingPromotion(from, to, Side.WHITE) else play(Move(from, to))
                },
                onPromotion = { type ->
                    val p = pending
                    pending = null
                    if (p != null && type != null) play(Move(p.from, p.to, type))
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            position = Position.fromFen(lesson.fen)
            lastMove = null
            mated = false
        }) {
            Icon(AppIcons.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.lesson_reset))
        }
        Spacer(Modifier.height(24.dp))
    }
}
