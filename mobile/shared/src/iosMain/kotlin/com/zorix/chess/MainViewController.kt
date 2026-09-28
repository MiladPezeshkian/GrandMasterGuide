package com.zorix.chess

import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.window.ComposeUIViewController
import com.zorix.chess.controller.ChessController
import com.zorix.chess.controller.KeyValueStore
import com.zorix.chess.engine.IosEngineHost
import com.zorix.chess.ui.PlatformActions
import com.zorix.chess.ui.ZorixApp
import com.zorix.chess.ui.theme.ZorixTheme
import kotlinx.coroutines.MainScope
import platform.Foundation.NSBundle
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/** Settings and the current game, kept in NSUserDefaults. */
private class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun getString(key: String): String? = defaults.stringForKey(key)
    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }
}

/** One controller for the lifetime of the app (the iOS counterpart of the Android ViewModel). */
private object AppState {
    val controller: ChessController by lazy {
        val c = ChessController(MainScope(), IosEngineHost(), UserDefaultsStore())
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            c.onBackground()
        }
        center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, NSOperationQueue.mainQueue) { _ ->
            c.onForeground()
        }
        c
    }
}

private object IosPlatform : PlatformActions {
    override val versionName: String =
        NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "1.0.0"
    override val maxThreads: Int = NSProcessInfo.processInfo.activeProcessorCount.toInt().coerceAtLeast(1)

    override fun sharePgn(pgn: String) {
        val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val sheet = UIActivityViewController(activityItems = listOf(pgn), applicationActivities = null)
        sheet.popoverPresentationController?.sourceView = root.view
        root.presentViewController(sheet, animated = true, completion = null)
    }
}

/** Entry point used by the Swift app (iosApp/ZorixChess/ZorixChessApp.swift). */
fun MainViewController(): UIViewController = ComposeUIViewController {
    val controller = AppState.controller
    val state by controller.state.collectAsState()
    ZorixTheme {
        ZorixApp(state, controller, IosPlatform)
    }
}
