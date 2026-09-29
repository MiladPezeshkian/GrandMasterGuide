package com.zorix.chess.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zorix.chess.controller.Experience
import com.zorix.chess.resources.*
import com.zorix.chess.ui.components.AppIcons
import com.zorix.chess.ui.components.PrimaryButton
import com.zorix.chess.ui.components.ZorixWordmark
import com.zorix.chess.ui.theme.ZorixColors
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val LANGS = listOf("fa" to "فارسی", "ckb" to "کوردی", "en" to "English")

/** First launch: language, the player's name (the coach uses it) and their chess experience. */
@Composable
fun Onboarding(
    language: String?,
    onLanguage: (String) -> Unit,
    onDone: (name: String, experience: Experience) -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var experience by rememberSaveable { mutableStateOf(Experience.RULES) }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = { (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260))) togetherWith fadeOut(tween(150)) },
            label = "onboarding",
        ) { p ->
            Column(
                Modifier.widthIn(max = 520.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(24.dp))
                Image(painterResource(Res.drawable.zc_emblem), null, Modifier.size(if (p == 0) 120.dp else 72.dp))
                Spacer(Modifier.height(12.dp))
                ZorixWordmark(fontSize = if (p == 0) 24.sp else 18.sp)
                Spacer(Modifier.height(28.dp))
                when (p) {
                    0 -> {
                        Text(stringResource(Res.string.onboard_welcome), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(Res.string.onboard_welcome_body), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(28.dp))
                        Text(stringResource(Res.string.settings_language), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            for ((code, label) in LANGS) {
                                Choice(label, selected = language == code, modifier = Modifier.weight(1f)) { onLanguage(code) }
                            }
                        }
                        Spacer(Modifier.height(32.dp))
                        PrimaryButton(stringResource(Res.string.onboard_start), { page = 1 }, Modifier.fillMaxWidth(), icon = AppIcons.ArrowForward)
                    }
                    1 -> {
                        Text(stringResource(Res.string.onboard_name_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.onboard_name_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(20.dp))
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(24) },
                            singleLine = true,
                            label = { Text(stringResource(Res.string.onboard_name_hint)) },
                            leadingIcon = { Icon(AppIcons.Person, null) },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { if (name.isNotBlank()) page = 2 }),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                        )
                        Spacer(Modifier.height(24.dp))
                        PrimaryButton(stringResource(Res.string.onboard_next), { page = 2 }, Modifier.fillMaxWidth(), icon = AppIcons.ArrowForward, enabled = name.isNotBlank())
                    }
                    else -> {
                        Text(stringResource(Res.string.onboard_level_title, name.trim()), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(Res.string.onboard_level_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(18.dp))
                        for (e in Experience.entries) {
                            LevelOption(e.title(), e.description(), selected = experience == e) { experience = e }
                            Spacer(Modifier.height(10.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        PrimaryButton(stringResource(Res.string.onboard_finish), { onDone(name.trim(), experience) }, Modifier.fillMaxWidth(), icon = AppIcons.Check)
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) ZorixColors.Red else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun LevelOption(title: String, body: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(2.dp, if (selected) ZorixColors.Red else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(AppIcons.Check, null, tint = ZorixColors.Red)
        }
    }
}

@Composable
fun Experience.title(): String = stringResource(titleRes())

@Composable
fun Experience.description(): String = stringResource(bodyRes())

private fun Experience.titleRes(): StringResource = when (this) {
    Experience.NEW -> Res.string.exp_new
    Experience.RULES -> Res.string.exp_rules
    Experience.CASUAL -> Res.string.exp_casual
    Experience.CLUB -> Res.string.exp_club
    Experience.STRONG -> Res.string.exp_strong
}

private fun Experience.bodyRes(): StringResource = when (this) {
    Experience.NEW -> Res.string.exp_new_body
    Experience.RULES -> Res.string.exp_rules_body
    Experience.CASUAL -> Res.string.exp_casual_body
    Experience.CLUB -> Res.string.exp_club_body
    Experience.STRONG -> Res.string.exp_strong_body
}
