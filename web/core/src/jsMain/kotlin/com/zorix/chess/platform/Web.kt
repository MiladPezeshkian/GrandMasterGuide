package com.zorix.chess.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.js.Date

/** The browser has one thread: engine I/O is asynchronous (a Web Worker), so the default dispatcher suffices. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

actual fun epochMillis(): Long = Date.now().toLong()

actual fun pgnDate(): String {
    val d = Date()
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${d.getFullYear()}.${two(d.getMonth() + 1)}.${two(d.getDate())}"
}
