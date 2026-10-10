package com.zorix.chess.ui.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zorix.chess.coach.Speakable
import com.zorix.chess.controller.AppController
import com.zorix.chess.controller.speechLanguage
import com.zorix.chess.core.Side
import com.zorix.chess.learn.Course
import com.zorix.chess.learn.Lesson
import com.zorix.chess.learn.LessonSession
import com.zorix.chess.platform.LocalAppLocale
import com.zorix.chess.platform.PlatformBackHandler
import com.zorix.chess.play.Bot
import com.zorix.chess.play.Bots
import com.zorix.chess.resources.*
import com.zorix.chess.ui.MainScreen
import com.zorix.chess.ui.PlatformActions
import com.zorix.chess.ui.board.rememberPieceImages
import com.zorix.chess.ui.components.LocalSpeechStatus
import com.zorix.chess.ui.components.LocalVoiceOn
import com.zorix.chess.ui.SpeedTipDialog
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.learn.CourseScreen
import com.zorix.chess.ui.learn.LearnHub
import com.zorix.chess.ui.learn.LessonPlayer
import com.zorix.chess.ui.onboarding.Onboarding
import com.zorix.chess.ui.play.GameScreen
import com.zorix.chess.ui.play.PlayHub
import com.zorix.chess.ui.puzzles.PuzzlesScreen
import com.zorix.chess.ui.review.ReviewScreen
import com.zorix.chess.ui.settings.SettingsScreen
import com.zorix.chess.ui.editor.BuilderScreen
import com.zorix.chess.ui.board.boardColors
import com.zorix.chess.ui.theme.ZorixTheme
import com.zorix.chess.controller.AppThemeId
import com.zorix.chess.ui.splash.SplashScreen
import com.zorix.chess.ui.theme.ZorixColors
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private enum class Tab { SETTINGS, PLAY, ANALYSIS, LEARN, PUZZLES }

private sealed interface Overlay {
    data object Game : Overlay
    data object Review : Overlay
    data object Builder : Overlay
    data class CourseView(val course: Course) : Overlay
    data class LessonView(val course: Course, val lesson: Lesson, val session: LessonSession) : Overlay
}

private val RTL = setOf("fa", "ckb")

/** Resolves the app language: the setting, or the system language when supported, else English. */
private fun resolve(setting: String?, system: String): String {
    setting?.let { return it }
    val s = system.lowercase()
    return when {
        s.startsWith("fa") -> "fa"
        s.startsWith("ckb") || s.startsWith("ku") -> "ckb"
        else -> "en"
    }
}

/** Root of the app: intro, first-launch onboarding, then the five sections. */
@Composable
fun ZorixApp(app: AppController, platform: PlatformActions) {
    val boardState by app.board.state.collectAsState()
    val speechStatus by app.speechStatus.collectAsState()
    val language = boardState.settings.language
    var splashDone by rememberSaveable { mutableStateOf(false) }
    val light = boardState.settings.appTheme == AppThemeId.SKY
    LaunchedEffect(light) { platform.setLightSystemBars(light) }
    ZorixTheme(boardState.settings.appTheme) {
    CompositionLocalProvider(LocalAppLocale provides language, LocalSpeechStatus provides speechStatus, LocalVoiceOn provides app.voiceReady(boardState.settings)) {
        key(language) {
            val lang = resolve(language, LocalAppLocale.current)
            LaunchedEffect(lang) { app.setLanguage(lang) }
            val direction = if (lang in RTL) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                // The Surface also sets the default text colour for the theme (light text on the dark style).
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
                Box(Modifier.fillMaxSize()) {
                    val profile by app.profile.profile.collectAsState()
                    if (!profile.onboarded) {
                        Onboarding(
                            language = language ?: lang,
                            onLanguage = { code -> app.board.updateSettings { it.copy(language = code) } },
                            onDone = { name, exp -> app.profile.completeOnboarding(name, exp) },
                        )
                    } else {
                        Shell(app, platform, lang)
                    }
                    var tipShown by rememberSaveable { mutableStateOf(false) }
                    if (splashDone && profile.onboarded && boardState.settings.speedTip && !tipShown) {
                        SpeedTipDialog(
                            onDismiss = { tipShown = true },
                            onNever = { tipShown = true; app.board.updateSettings { it.copy(speedTip = false) } },
                        )
                    }
                    AnimatedVisibility(visible = !splashDone, enter = fadeIn(), exit = fadeOut()) {
                        SplashScreen(engine = boardState.engine, onFinished = { splashDone = true })
                    }
                }
                }
            }
        }
    }
    }
}

@Composable
private fun Shell(app: AppController, platform: PlatformActions, lang: String) {
    var tab by rememberSaveable { mutableStateOf(Tab.ANALYSIS) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    val boardState by app.board.state.collectAsState()
    val profile by app.profile.profile.collectAsState()
    val courses by app.courses.collectAsState()
    val playState by app.play.state.collectAsState()
    val reviewState by app.review.state.collectAsState()
    val puzzleState by app.puzzles.state.collectAsState()
    val pieces = rememberPieceImages()
    val settings = boardState.settings
    val speechLang = speechLanguage(lang)
    fun speak(text: String) = app.speak(text)

    // Only the visible screen may use the engine in the background or talk.
    LaunchedEffect(tab, overlay) {
        app.stopSpeaking()
        app.board.setVisible(tab == Tab.ANALYSIS && overlay == null)
        app.play.visible = overlay == Overlay.Game
    }
    PlatformBackHandler(enabled = overlay != null || tab != Tab.ANALYSIS) {
        when (val o = overlay) {
            is Overlay.LessonView -> overlay = Overlay.CourseView(o.course)
            null -> tab = Tab.ANALYSIS
            else -> overlay = null
        }
    }

    fun startGame(bot: Bot, side: Side?) {
        app.play.start(bot.level, side)
        overlay = Overlay.Game
    }
    fun openLesson(course: Course, lesson: Lesson) {
        overlay = Overlay.LessonView(course, lesson, app.startLesson(lesson))
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    engine = boardState.engine,
                    maxThreads = platform.maxThreads,
                    versionName = platform.versionName,
                    onChange = app.board::updateSettings,
                    profileName = profile.name,
                    onRename = app.profile::rename,
                )
                Tab.PLAY -> PlayHub(profile, playState, onResume = { overlay = Overlay.Game }, onStart = ::startGame)
                Tab.ANALYSIS -> MainScreen(boardState, app.board, platform, onBuild = { overlay = Overlay.Builder })
                Tab.LEARN -> LearnHub(courses, lang, app::progress) { overlay = Overlay.CourseView(it) }
                Tab.PUZZLES -> PuzzlesScreen(
                    puzzleState, app.puzzles, profile, settings, pieces,
                    onSpeak = { speak(Speakable.of(it, speechLang)) },
                    onBuild = { overlay = Overlay.Builder },
                )
            }
        }
        if (overlay == null) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                NavItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS }, stringResource(Res.string.tab_settings)) { Icon(AppIcons.Settings, null) }
                NavItem(tab == Tab.PLAY, { tab = Tab.PLAY }, stringResource(Res.string.tab_play)) {
                    Image(painterResource(Res.drawable.piece_wn), null, Modifier.size(24.dp))
                }
                MainNavItem(tab == Tab.ANALYSIS, { tab = Tab.ANALYSIS }, stringResource(Res.string.tab_analysis))
                NavItem(tab == Tab.LEARN, { tab = Tab.LEARN }, stringResource(Res.string.tab_learn)) { Icon(AppIcons.School, null) }
                NavItem(tab == Tab.PUZZLES, { tab = Tab.PUZZLES }, stringResource(Res.string.tab_puzzles)) { Icon(AppIcons.Puzzle, null) }
            }
        }
    }

    AnimatedVisibility(overlay != null, enter = slideInVertically { it / 6 } + fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (val o = overlay) {
                Overlay.Game -> GameScreen(
                    state = playState, play = app.play, profile = profile, settings = settings, pieces = pieces,
                    onBack = { overlay = null; if (playState.result != null) app.play.close() },
                    onReview = {
                        app.review.start(playState.game, playState.userSide)
                        overlay = Overlay.Review
                    },
                    onNextLevel = { startGame(it, playState.userSide) },
                )
                Overlay.Review -> ReviewScreen(
                    reviewState, app.review, settings, pieces, profile.name.ifBlank { stringResource(Res.string.default_player) },
                    onBack = { overlay = if (playState.active) Overlay.Game else null },
                    onSpeak = { speak(Speakable.of(it, speechLang)) },
                )
                Overlay.Builder -> BuilderScreen(
                    initial = boardState.game.position,
                    colors = boardColors(settings.boardTheme),
                    pieces = pieces,
                    onCancel = { overlay = null },
                    onAnalyze = { position ->
                        app.board.setPosition(position)
                        overlay = null
                        tab = Tab.ANALYSIS
                    },
                )
                is Overlay.CourseView -> CourseScreen(o.course, lang, profile.lessonStars, onBack = { overlay = null }) { openLesson(o.course, it) }
                is Overlay.LessonView -> key(o.lesson.id) {
                    val lessons = o.course.lessons
                    val next = lessons.getOrNull(lessons.indexOf(o.lesson) + 1)
                    LessonPlayer(
                        session = o.session,
                        lang = lang,
                        speechLang = speechLang,
                        settings = settings,
                        pieces = pieces,
                        onSpeak = ::speak,
                        onFinished = app::completeLesson,
                        onNextLesson = next?.let { { openLesson(o.course, it) } },
                        onClose = { overlay = Overlay.CourseView(o.course) },
                    )
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(selected: Boolean, onClick: () -> Unit, label: String, icon: @Composable () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = ZorixColors.RedBright,
            indicatorColor = ZorixColors.Red,
        ),
    )
}

/** The analysis board is the heart of the app: its tab sits in the middle as a large round button. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.MainNavItem(selected: Boolean, onClick: () -> Unit, label: String) {
    Column(
        // A fixed height: NavigationBar does not bound its row, so filling the height would take the whole screen.
        Modifier.weight(1f).height(80.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(54.dp)
                .shadow(if (selected) 10.dp else 4.dp, CircleShape)
                .clip(CircleShape)
                .background(ZorixColors.ButtonGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Analysis, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) ZorixColors.RedBright else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
