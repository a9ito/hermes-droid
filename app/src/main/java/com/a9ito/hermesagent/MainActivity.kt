package com.a9ito.hermesagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.a9ito.hermesagent.ui.HermesAgentApp as HermesAgentUi

/** Single activity hosting the whole Compose UI. No XML layouts. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            HermesAgentUi()
        }
    }
}
