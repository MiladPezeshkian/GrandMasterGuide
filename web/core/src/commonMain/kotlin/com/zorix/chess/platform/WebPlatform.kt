package com.zorix.chess.platform

import kotlinx.coroutines.CoroutineDispatcher

// The app's platform functions used by its chess logic (the app's own Platform.kt also has Compose ones).

/** Dispatcher for engine I/O. */
expect val ioDispatcher: CoroutineDispatcher

/** Milliseconds since the Unix epoch. */
expect fun epochMillis(): Long

/** Today's date in PGN format, e.g. "2026.09.28". */
expect fun pgnDate(): String
