package com.zorix.chess.platform

import androidx.compose.runtime.Composable
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.timeIntervalSince1970

actual fun epochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

actual fun pgnDate(): String {
    val formatter = NSDateFormatter()
    formatter.locale = NSLocale(localeIdentifier = "en_US_POSIX")
    formatter.dateFormat = "yyyy.MM.dd"
    return formatter.stringFromDate(NSDate())
}

/** iOS has no back button; edge-swipe dismissal is handled by the editor's close button. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
