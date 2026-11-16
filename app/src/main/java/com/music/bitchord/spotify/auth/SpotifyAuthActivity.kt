package com.music.bitchord.spotify.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.music.bitchord.MainActivity
import com.music.bitchord.R
import com.music.bitchord.data.DebugLog as Log
import kotlinx.coroutines.launch

class SpotifyAuthActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data
        if (uri == null) {
            finish()
            return
        }

        val code = uri.getQueryParameter("code")
        val error = uri.getQueryParameter("error")

        if (!error.isNullOrBlank()) {
            Log.e("SpotifyAuthActivity", "OAuth error: $error")
            Toast.makeText(this, "Spotify login failed: $error", Toast.LENGTH_LONG).show()
            returnToMain()
            return
        }

        if (code.isNullOrBlank()) {
            Log.w("SpotifyAuthActivity", "No code in redirect URI: $uri")
            finish()
            return
        }

        lifecycleScope.launch {
            val result = SpotifyAuthManager.exchangeCode(applicationContext, code)
            result.onSuccess {
                Toast.makeText(applicationContext, "Connected to Spotify!", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(applicationContext, "Spotify setup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
            returnToMain()
        }
    }

    private fun returnToMain() {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(mainIntent)
        finish()
    }
}

