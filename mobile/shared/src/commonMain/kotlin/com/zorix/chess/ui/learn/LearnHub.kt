package com.zorix.chess.ui.learn

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zorix.chess.controller.LearnProgress
import com.zorix.chess.learn.Course
import com.zorix.chess.learn.Lesson
import com.zorix.chess.resources.*
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.Progress
import com.zorix.chess.ui.components.ScreenHeader
import com.zorix.chess.ui.components.StarRow
import com.zorix.chess.ui.components.ZCard
import com.zorix.chess.ui.localized
import com.zorix.chess.ui.theme.ZorixColors
import org.jetbrains.compose.resources.stringResource

private val courseColors = listOf(Color(0xFF6CCB5F), Color(0xFFE3202B), Color(0xFFFF9F45), Color(0xFF4F9CFF), Color(0xFF8C6CFF), Color(0xFF3FB5A6))
private val courseIcons: List<ImageVector> get() = listOf(AppIcons.School, AppIcons.Trophy, AppIcons.Bolt, AppIcons.Flag, AppIcons.Book, AppIcons.Chart)

/** All courses with the player's progress. */
@Composable
fun LearnHub(
    courses: List<Course>,
    lang: String,
    progress: (Course) -> LearnProgress,
    onOpen: (Course) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        val total = courses.sumOf { it.lessons.size }
        ScreenHeader(stringResource(Res.string.learn_title), subtitle = stringResource(Res.string.learn_subtitle, total))
        if (courses.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(courses.size) { i ->
                val c = courses[i]
                val p = progress(c)
                val color = courseColors[i % courseColors.size]
                ZCard(Modifier.fillMaxWidth(), onClick = { onOpen(c) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(courseIcons[i % courseIcons.size], null, tint = color, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.title.localized(lang), style = MaterialTheme.typography.titleMedium)
                            Text(c.description.localized(lang), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Progress(p.done.toFloat() / p.total.coerceAtLeast(1), color = color)
                            Spacer(Modifier.height(4.dp))
                            Text(stringResource(Res.string.learn_progress, p.done, p.total), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Chapters and lessons of one course. Lessons unlock in order; finished lessons show their stars. */
@Composable
fun CourseScreen(
    course: Course,
    lang: String,
    stars: Map<String, Int>,
    onBack: () -> Unit,
    onLesson: (Lesson) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScreenHeader(course.title.localized(lang), onBack = onBack, subtitle = stringResource(Res.string.learn_progress, course.lessons.count { it.id in stars }, course.lessons.size))
        val all = course.lessons
        // A lesson is open when the previous one is done (or it is the first of a chapter already reached).
        val firstOpen = all.indexOfFirst { it.id !in stars }.let { if (it < 0) all.size else it }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            for (ch in course.chapters) {
                item(key = "h" + ch.id) {
                    Text(ch.title.localized(lang), style = MaterialTheme.typography.titleMedium, color = ZorixColors.RedBright, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
                }
                items(ch.lessons, key = { it.id }) { l ->
                    val index = all.indexOf(l)
                    val done = stars[l.id]
                    val open = done != null || index <= firstOpen + 2
                    LessonRow(l, lang, index + 1, done, open) { if (open) onLesson(l) }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, lang: String, number: Int, stars: Int?, open: Boolean, onClick: () -> Unit) {
    ZCard(Modifier.fillMaxWidth().padding(bottom = 8.dp), onClick = if (open) onClick else null, padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(
                    when {
                        stars != null -> ZorixColors.Best
                        open -> ZorixColors.Red
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    stars != null -> Icon(AppIcons.Check, null, tint = Color(0xFF101014), modifier = Modifier.size(20.dp))
                    open -> Text(number.toString(), color = Color.White, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                    else -> Icon(AppIcons.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title.localized(lang), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.lesson_meta, lesson.exercises, lesson.level), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (stars != null) StarRow(stars, size = 14.dp)
        }
    }
}
