package com.flipfate.lbsroc.entry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.flipfate.lbsroc.MainActivity

class EntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = EntrySettings(this)
        setContent {
            EntryScreen(
                settings = settings,
                openBrowser = ::openBrowser,
                openGame = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            )
        }
    }
}
