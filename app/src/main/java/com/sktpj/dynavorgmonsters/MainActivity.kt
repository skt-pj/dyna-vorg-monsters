package com.sktpj.dynavorgmonsters

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager

class MainActivity : Activity() {
    private companion object {
        const val TAG = "DVMSelection"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        Log.i(TAG, "app_start version=${BuildConfig.VERSION_NAME}")
        showCharacterSelect()
    }

    private fun showCharacterSelect() {
        setContentView(
            CharacterSelectView(this) { player, enemy ->
                Log.i(TAG, "battle_transition_begin player=${player.id} enemy=${enemy.id}")
                val battleView = SpriteGameContainer(this, player, enemy)
                setContentView(battleView)
                Log.i(TAG, "battle_transition_complete player=${player.id} enemy=${enemy.id}")
            },
        )
    }
}
