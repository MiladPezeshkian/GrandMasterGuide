package com.zorix.chess.platform

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun epochMillis(): Long = System.currentTimeMillis()

actual fun pgnDate(): String = SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)
