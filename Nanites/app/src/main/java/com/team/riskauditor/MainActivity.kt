package com.team.riskauditor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.team.riskauditor.ui.AuditorApp
import com.team.riskauditor.ui.theme.NanitesTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as App).container
        setContent {
            NanitesTheme {
                AuditorApp(container)
            }
        }
    }
}
