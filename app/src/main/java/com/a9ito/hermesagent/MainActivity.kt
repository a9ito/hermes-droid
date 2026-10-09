package com.a9ito.hermesagent

import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.a9ito.hermesagent.ui.HermesAgentApp as HermesAgentUi

/** Single activity hosting the whole Compose UI. No XML layouts. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // --- Security hardening (whole-window) ---------------------------------
        // This window shows a full-control bearer token (Settings) and agent
        // output that can include sensitive command results (chat/run
        // transcripts), so the capture, autofill, and tapjacking surfaces are
        // locked down once at the window level rather than per screen.

        // FLAG_SECURE keeps the window out of the recents thumbnail, screenshots,
        // and non-secure external displays, so a revealed token or transcript is
        // not captured off screen. Tradeoff: in-app screenshots are blocked.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        // Opt the whole view tree out of the Autofill framework so the token (or
        // any field) is never offered to, or saved by, an autofill provider.
        window.decorView.importantForAutofill =
            View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS

        // Drop touches that arrive while the window is obscured by an overlay from
        // another app, so a malicious overlay cannot trick a tap on a destructive
        // control or the run-approval dialog. Set on the content view, through
        // which every touch passes, as the Compose-era equivalent of
        // android:filterTouchesWhenObscured.
        findViewById<View>(android.R.id.content)?.filterTouchesWhenObscured = true

        setContent {
            HermesAgentUi()
        }
    }
}
