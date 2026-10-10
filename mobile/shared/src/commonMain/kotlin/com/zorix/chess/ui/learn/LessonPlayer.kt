package com.zorix.chess.ui.learn

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.sp
import com.zorix.chess.coach.Speakable
import com.zorix.chess.controller.PendingPromotion
import com.zorix.chess.controller.Settings
import com.zorix.chess.core.Squares
import com.zorix.chess.learn.LessonResult
import com.zorix.chess.learn.LessonSession
import com.zorix.chess.learn.Step
import com.zorix.chess.learn.StepStatus
import com.zorix.chess.learn.StepUi
import com.zorix.chess.resources.*
import com.zorix.chess.ui.board.BoardArrow
import com.zorix.chess.ui.board.ChessBoard
import com.zorix.chess.ui.board.PieceImages
import com.zorix.chess.ui.board.boardColors
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.CoachBubble
import com.zorix.chess.ui.components.Pill
import com.zorix.chess.ui.components.PrimaryButton
import com.zorix.chess.ui.components.Progress
import com.zorix.chess.ui.components.SecondaryButton
import com.zorix.chess.ui.components.StarRow
import com.zorix.chess.ui.localized
import com.zorix.chess.ui.theme.ZorixColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

private val STAR = Color(0xFFFFC53D)

/** Plays one lesson: intro, then every exercise, then the result with stars. */
@Composable
fun LessonPlayer(
    session: LessonSession,
    lang: String,
    speechLang: String,
    settings: Settings,
    pieces: PieceImages,
    onSpeak: (String) -> Unit,
    onFinished: (LessonResult) -> Unit,
    onNextLesson: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val ui by session.ui.collectAsState()
    val result by session.result.collectAsState()
    var started by rememberSaveable(session.lesson.id) { mutableStateOf(false) }
    DisposableEffect(session) { onDispose { session.close() } }
    LaunchedEffect(result) { result?.let(onFinished) }
    val lesson = session.lesson

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(AppIcons.Close, stringResource(Res.string.action_close)) }
            Column(Modifier.weight(1f)) {
                Text(lesson.title.localized(lang), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Progress(if (!started) 0f else (ui.index + if (ui.status == StepStatus.SOLVED) 1 else 0).toFloat() / session.stepCount)
            }
            com.zorix.chess.ui.components.HelpButton(com.zorix.chess.ui.components.HelpTopic.LESSON)
        }
        val r = result
        when {
            r != null -> ResultPage(r, lang, onNextLesson, onClose)
            !started -> IntroPage(lesson.intro.localized(lang), { onSpeak(Speakable.of(lesson.intro.localized(speechLang), speechLang)) }) { started = true }
            else -> StepPage(session, ui, lang, speechLang, settings, pieces, onSpeak)
        }
    }
}

@Composable
private fun IntroPage(text: String, onSpeak: () -> Unit, onStart: () -> Unit) {
    // The coach reads the introduction aloud (when the voice is switched on), like a lesson video.
    LaunchedEffect(text) {
        delay(NARRATION_DELAY_MS)
        onSpeak()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        CoachBubble(text, onSpeak = onSpeak, waitForVoice = true)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(Res.string.lesson_start), onStart, Modifier.fillMaxWidth(), icon = AppIcons.Play)
    }
}

@Composable
private fun ResultPage(result: LessonResult, lang: String, onNext: (() -> Unit)?, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(AppIcons.Trophy, null, tint = STAR, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.lesson_done_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(result.lesson.title.localized(lang), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        StarRow(result.stars, size = 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (result.mistakes == 0) Res.string.lesson_perfect else Res.string.lesson_mistakes, result.mistakes),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        if (onNext != null) PrimaryButton(stringResource(Res.string.lesson_next), onNext, Modifier.fillMaxWidth(), icon = AppIcons.ArrowForward)
        Spacer(Modifier.height(10.dp))
        SecondaryButton(stringResource(Res.string.lesson_back_to_course), onClose, Modifier.fillMaxWidth())
    }
}

@Composable
private fun StepPage(session: LessonSession, ui: StepUi, lang: String, speechLang: String, settings: Settings, pieces: PieceImages, onSpeak: (String) -> Unit) {
    val step = ui.step
    // Narration: explanation cards when they appear, and the coach's explanation after each solved move.
    LaunchedEffect(ui.index, step is Step.Theory) {
        if (step is Step.Theory) {
            delay(NARRATION_DELAY_MS)
            onSpeak(Speakable.of(step.prompt.localized(speechLang), speechLang))
        }
    }
    LaunchedEffect(ui.speech) { ui.speech?.let(onSpeak) }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val boardSize = min(maxWidth - 24.dp, maxHeight * 0.62f)
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                // Task and feedback
                PromptBar(step, ui, lang)
                Spacer(Modifier.height(8.dp))
                val pos = ui.position
                if (pos != null && step !is Step.Quiz || (step is Step.Quiz && pos != null)) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val highlights = HashMap<Int, Color>()
                        ui.stars.forEach { highlights[it] = STAR.copy(alpha = 0.55f) }
                        ui.marks.forEach { highlights[it] = ZorixColors.Line2.copy(alpha = 0.45f) }
                        ui.hintSquare?.let { highlights[it] = ZorixColors.Best.copy(alpha = 0.6f) }
                        if (step is Step.Squares && ui.status == StepStatus.WRONG) ui.marks.forEach { highlights[it] = MaterialTheme.colorScheme.error.copy(alpha = 0.6f) }
                        ChessBoard(
                            position = pos!!,
                            lastMove = ui.lastMove,
                            flipped = ui.flipped,
                            colors = boardColors(settings.boardTheme),
                            pieces = pieces,
                            modifier = Modifier.size(boardSize).clip(RoundedCornerShape(6.dp)),
                            arrows = ui.arrows.map { BoardArrow(it.from, it.to, ZorixColors.Best.copy(alpha = 0.85f), 0.17f) },
                            showCoordinates = settings.showCoordinates || step is Step.Squares,
                            showLegalMoves = settings.showLegalMoves,
                            animateMoves = settings.animateMoves,
                            interactive = ui.interactive && step !is Step.Squares && step !is Step.Theory && step !is Step.Quiz,
                            promotion = ui.pendingPromotion?.let { (f, t) -> PendingPromotion(f, t, pos.sideToMove) },
                            highlights = highlights,
                            onMove = session::onMove,
                            onPromotion = session::onPromotion,
                            onSquareTap = if (step is Step.Squares) session::onSquare else null,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                when (step) {
                    is Step.Theory -> CoachBubble(step.prompt.localized(lang), onSpeak = { onSpeak(Speakable.of(step.prompt.localized(speechLang), speechLang)) }, waitForVoice = true)
                    is Step.Quiz -> QuizOptions(step, ui, lang, session::onOption)
                    else -> ui.message?.let { m -> CoachBubble(m, onSpeak = { onSpeak(ui.speech ?: Speakable.of(m, speechLang)) }, waitForVoice = ui.speech != null) }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        Controls(session, ui)
    }
}

@Composable
private fun PromptBar(step: Step, ui: StepUi, lang: String) {
    val text = when (step) {
        is Step.Theory -> null
        is Step.Squares -> ui.target?.let { stringResource(Res.string.lesson_find_square, Squares.name(it)) } ?: stringResource(Res.string.lesson_squares_done, ui.correct, step.count)
        else -> step.prompt.localized(lang).ifBlank { null }
    }
    val status = when (ui.status) {
        StepStatus.WRONG -> stringResource(Res.string.puzzle_wrong) to MaterialTheme.colorScheme.error
        StepStatus.SOLVED -> if (step is Step.Theory) null else stringResource(Res.string.puzzle_solved) to ZorixColors.Best
        StepStatus.FAILED -> stringResource(Res.string.lesson_failed) to MaterialTheme.colorScheme.error
        StepStatus.ACTIVE -> null
    }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // The task itself never leaves the screen: the verdict on the last answer shows under it.
        if (step is Step.Squares) {
            step.prompt.localized(lang).takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        AnimatedContent(targetState = text, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "prompt") { t ->
            if (t != null) {
                Text(
                    t,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = if (step is Step.Squares && ui.target != null) 26.sp else 17.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        AnimatedContent(targetState = status, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) }, label = "verdict") { s ->
            if (s != null) {
                Text(
                    s.first,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = s.second,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(s.second.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        val extra = when (step) {
            is Step.Stars -> stringResource(Res.string.lesson_moves_par, ui.moves, step.par)
            is Step.Capture -> stringResource(Res.string.lesson_moves_par, ui.moves, step.par)
            is Step.Squares -> stringResource(Res.string.lesson_round, (ui.round + 1).coerceAtMost(step.count), step.count)
            is Step.Play -> if (ui.busy) stringResource(Res.string.zorix_thinking) else null
            is Step.Mate -> if (ui.busy) stringResource(Res.string.lesson_checking) else null
            else -> null
        }
        extra?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun QuizOptions(step: Step.Quiz, ui: StepUi, lang: String, onOption: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(step.prompt.localized(lang), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
        step.options.forEachIndexed { i, opt ->
            val chosen = ui.chosen == i
            val correct = ui.status == StepStatus.SOLVED && i == step.answer
            val wrong = chosen && ui.status == StepStatus.WRONG
            Box(
                Modifier.fillMaxWidth().padding(bottom = 8.dp).clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            correct -> ZorixColors.Best.copy(alpha = 0.22f)
                            wrong -> MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .border(2.dp, if (correct) ZorixColors.Best else if (wrong) MaterialTheme.colorScheme.error else Color.Transparent, RoundedCornerShape(16.dp))
                    .clickable(enabled = ui.status != StepStatus.SOLVED) { onOption(i) }
                    .padding(16.dp),
            ) { Text(opt.localized(lang), style = MaterialTheme.typography.bodyLarge) }
        }
        ui.message?.let { CoachBubble(it) }
    }
}

@Composable
private fun Controls(session: LessonSession, ui: StepUi) {
    val step = ui.step
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val puzzleLike = step is Step.Puzzle || step is Step.Mate || step is Step.Best || step is Step.Line || step is Step.GoalStep
        if (ui.index > 0) {
            IconButton(onClick = session::previous) { Icon(AppIcons.ChevronLeft, stringResource(Res.string.action_back)) }
        }
        if (puzzleLike && ui.status != StepStatus.SOLVED) {
            SecondaryButton(stringResource(Res.string.puzzle_hint), session::hint, Modifier.weight(1f), icon = AppIcons.Bulb)
            SecondaryButton(stringResource(Res.string.puzzle_solution), session::showSolution, Modifier.weight(1f), icon = AppIcons.Play)
        } else if ((step is Step.Play || step is Step.Stars || step is Step.Capture) && ui.status != StepStatus.SOLVED) {
            SecondaryButton(stringResource(Res.string.puzzle_retry), session::retry, Modifier.weight(1f), icon = AppIcons.Replay)
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (ui.status == StepStatus.SOLVED) {
            PrimaryButton(stringResource(Res.string.puzzle_next), session::next, Modifier.weight(1.3f), icon = AppIcons.ArrowForward)
        } else if (ui.status == StepStatus.FAILED) {
            PrimaryButton(stringResource(Res.string.puzzle_retry), session::retry, Modifier.weight(1.3f), icon = AppIcons.Replay)
        }
    }
}

/** Lets the screen settle (and earlier speech stop) before the coach starts reading. */
private const val NARRATION_DELAY_MS = 300L
