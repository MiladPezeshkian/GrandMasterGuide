package com.zorix.chess.platform

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineDispatcher

/** Dispatcher for blocking engine I/O. */
expect val ioDispatcher: CoroutineDispatcher

/** Milliseconds since the Unix epoch. */
expect fun epochMillis(): Long

/** Today's date in PGN format, e.g. "2026.09.28". */
expect fun pgnDate(): String

/** Handles the system back gesture/button where the platform has one (Android). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
