package com.zorix.chess.data

import android.content.Context
import com.zorix.chess.controller.KeyValueStore

/** Settings and the current game, kept in private SharedPreferences. */
class SharedPreferencesStore(context: Context) : KeyValueStore {

    private val prefs = context.applicationContext.getSharedPreferences("zorix_chess", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        val editor = prefs.edit()
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }
}
