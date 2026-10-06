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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zorix.chess.controller.AppController
import com.zorix.chess.data.SharedPreferencesStore
import com.zorix.chess.engine.AndroidEngineHost
import com.zorix.chess.speech.AndroidSpeech
import com.zorix.chess.ui.PlatformActions
import com.zorix.chess.ui.app.ZorixApp
import com.zorix.chess.ui.app.createAppController
import com.zorix.chess.ui.theme.ZorixTheme

/** Hosts the platform-independent [AppController] so it survives rotation. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    val speech = AndroidSpeech(application)

    val app: AppController = createAppController(
        scope = viewModelScope,
        host = AndroidEngineHost(application),
        store = SharedPreferencesStore(application),
        speech = speech,
    )

    override fun onCleared() {
        app.close()
        speech.shutdown()
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

            override fun setLightSystemBars(light: Boolean) {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = light
                    isAppearanceLightNavigationBars = light
                }
            }

            override fun sharePgn(pgn: String) {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name) + " PGN")
                    putExtra(Intent.EXTRA_TEXT, pgn)
                }
                startActivity(Intent.createChooser(send, null))
            }
        }

        setContent {
            ZorixTheme {
                ZorixApp(viewModel.app, platform)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.app.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Stop analysing in the background to save battery.
        if (!isChangingConfigurations) viewModel.app.onBackground()
    }
}
