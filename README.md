# Hermes Agent (Android)

A native Android companion client for controlling your own running
[Hermes Agent](https://hermes-agent.nousresearch.com) instance from your phone —
chat with it, check its status, and point it at whichever instance you run.

Built with Kotlin + Jetpack Compose and a Material 3 **Expressive** UI.

## Features

- **Settings** — point the app at your Hermes API server (host/URL, port, token).
  The token is encrypted with an AndroidKeyStore-backed AES/GCM key and is never
  logged or stored in plaintext.
- **Chat** — talk to your instance over its OpenAI-compatible
  `/v1/chat/completions` endpoint, with streaming (SSE) replies and in-session
  history. Gated behind a saved host + token.
- **Status** — shows the instance's readiness, gateway state, active agents,
  connected platforms, and model, from `/health/detailed` + `/v1/models`.

## Tech

- Kotlin, Jetpack Compose, single `:app` module, manual DI (no Hilt/Dagger).
- `minSdk` 26, `compileSdk`/`targetSdk` 36 (Android 16).
- Material 3 Expressive (`androidx.compose.material3` 1.4.0) with dynamic color
  on Android 12+ and a hand-picked static fallback palette below that.
- Retrofit + OkHttp + kotlinx.serialization for REST; OkHttp for SSE streaming.
- English + Indonesian localization; adding a locale is "add one file."

## Build

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # Android lint
```

Requires JDK 17. The Gradle wrapper pins the required Gradle version.

## Releases

Signed APK + AAB are published to **GitHub Releases** when a `v*.*.*` tag is
pushed (see `.github/workflows/release.yml`). Signing material is supplied via
repository secrets and is never committed.

## Configuration

The app talks to the Hermes **API server** (`hermes gateway` with the API server
platform enabled). Point Settings at that server's address and paste the
`API_SERVER_KEY` value as the token.
