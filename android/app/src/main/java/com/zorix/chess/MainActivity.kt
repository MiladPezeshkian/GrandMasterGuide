package com.zorix.chess

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zorix.chess.controller.ChessController
import com.zorix.chess.data.SharedPreferencesStore
import com.zorix.chess.engine.AndroidEngineHost
import com.zorix.chess.ui.PlatformActions
import com.zorix.chess.ui.ZorixApp
import com.zorix.chess.ui.theme.ZorixTheme

/** Hosts the platform-independent [ChessController] so it survives rotation. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val controller = ChessController(
        scope = viewModelScope,
        host = AndroidEngineHost(application),
        store = SharedPreferencesStore(application),
    )

    override fun onCleared() {
        controller.close()
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 12+ system splash with the ZC emblem; the animated Compose intro continues from it.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val platform = object : PlatformActions {
            override val versionName: String = BuildConfig.VERSION_NAME
            override val maxThreads: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

            override fun sharePgn(pgn: String) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " PGN")
                    putExtra(Intent.EXTRA_TEXT, pgn)
                }
                startActivity(Intent.createChooser(send, getString(R.string.action_share_pgn)))
            }
        }

        setContent {
            ZorixTheme {
                val state by viewModel.controller.state.collectAsStateWithLifecycle()
                ZorixApp(state, viewModel.controller, platform)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.controller.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Stop analysing in the background to save battery.
        if (!isChangingConfigurations) viewModel.controller.onBackground()
    }
}
