package com.zorix.chess.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.timeIntervalSince1970

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

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

actual object LocalAppLocale {
    private const val LANGUAGE_KEY = "AppleLanguages"
    private val default: String = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    private val LocalLocale = staticCompositionLocalOf { default }

    actual val current: String
        @Composable get() = LocalLocale.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val defaults = NSUserDefaults.standardUserDefaults
        if (value == null) defaults.removeObjectForKey(LANGUAGE_KEY) else defaults.setObject(listOf(value), forKey = LANGUAGE_KEY)
        return LocalLocale.provides(value ?: default)
    }
}
