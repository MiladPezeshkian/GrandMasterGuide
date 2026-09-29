package com.zorix.chess.ui.app

import com.zorix.chess.controller.AppController
import com.zorix.chess.controller.EngineHost
import com.zorix.chess.controller.KeyValueStore
import com.zorix.chess.controller.Speech
import com.zorix.chess.resources.Res
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.ExperimentalResourceApi

/** Creates the app controller with the bundled lesson and puzzle files. */
@OptIn(ExperimentalResourceApi::class)
fun createAppController(scope: CoroutineScope, host: EngineHost, store: KeyValueStore, speech: Speech = Speech.None): AppController =
    AppController(scope, host, store, speech) { path -> Res.readBytes(path) }
