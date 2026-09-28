# Hermes Agent (Android)

A native Android companion client for controlling your own running
[Hermes Agent](https://hermes-agent.nousresearch.com) instance from your phone —
chat with it, browse and manage its sessions, inspect its skills and toolsets,
check its status, and point it at whichever instance you run.

Built with Kotlin + Jetpack Compose and a Material 3 **Expressive** UI.

## Features

- **Chat** — a quick, stateless conversation over the OpenAI-compatible
  `/v1/chat/completions` endpoint, with streaming (SSE) replies. Good for
  one-off questions; history lives only on the phone for the current screen.
- **Sessions** — the persistent side. List, open, create, rename, fork and
  delete the real server-side sessions your instance keeps (`/api/sessions`).
  Opening one loads its stored transcript and streams each new turn through
  `/api/sessions/{id}/chat/stream`, so the conversation is durable and shared
  with every other Hermes surface (CLI, Discord, desktop). Each session can be
  locked to a specific model from the picker in its app bar.
- **Tools** — a read-only viewer for the instance's installed **skills**
  (`/v1/skills`) and configurable **toolsets** (`/v1/toolsets`), including each
  toolset's enabled/configured state and the concrete tools it expands to.
- **Status** — the instance's readiness, gateway state, active agents,
  connected platforms, and model, from `/health/detailed` + `/v1/models`.
- **Settings** — point the app at your Hermes API server (host/URL, port,
  token). The token is encrypted with an AndroidKeyStore-backed AES/GCM key and
  is never logged or stored in plaintext.

Chat, Sessions, Tools and Status are gated behind a saved host + token.

## How to use

You need a Hermes Agent instance running its **API server**, and network
reachability from your phone to that instance.

### 1. Turn on the API server on your instance

The app talks to the Hermes API-server platform, not the CLI. Enable it and note
the key:

```bash
# On the machine running Hermes:
hermes gateway            # starts the gateway with the API server platform
```

The API server listens on `127.0.0.1:8642` by default and authenticates with a
bearer token read from `API_SERVER_KEY` (in `~/.hermes/.env`). If it isn't set,
add one and restart the gateway:

```bash
echo 'API_SERVER_KEY=pick-a-long-random-string' >> ~/.hermes/.env
```

### 2. Make the instance reachable from the phone

`127.0.0.1` on the instance is not reachable from your phone — you need an
address the phone can hit. Pick one:

- **Same LAN (plain HTTP):** bind the API server to your LAN IP and use
  `http://<computer-lan-ip>:8642` (e.g. `http://192.168.1.50:8642`). The app
  already permits cleartext HTTP to loopback and private ranges
  (`10/8`, `172.16/12`, `192.168/16`), so LAN works without TLS.
- **Anywhere (HTTPS tunnel):** put the API server behind a reverse proxy or
  tunnel (Cloudflare Tunnel, Tailscale, nginx + TLS, etc.) and use the
  `https://…` URL. Public/non-private hosts must be HTTPS — cleartext to a
  public IP is intentionally blocked.

> Security note: the API-server key is full control of your agent. Prefer a
> tunnel with TLS over exposing port 8642 to the open internet, and never share
> the key.

### 3. Configure the app

Open **Settings** and enter:

- **Host / URL** — e.g. `http://192.168.1.50:8642` or `https://hermes.example.com`
- **Token** — the `API_SERVER_KEY` value

Save. The Status tab should turn green (readiness OK, gateway running). If it
can't connect, re-check the URL, the port, and that the phone can actually reach
the host (same Wi-Fi / tunnel up).

### 4. Use it

- **Sessions** is the main event: your durable conversations, shared with the
  rest of Hermes. Create one with **+**, tap to open, long-lived history and all.
- **Chat** is the throwaway scratchpad.
- **Tools** shows what the instance can do.

## Tech

- Kotlin, Jetpack Compose, single `:app` module, manual DI (no Hilt/Dagger).
- `minSdk` 26, `compileSdk`/`targetSdk` 36 (Android 16).
- Material 3 Expressive (`androidx.compose.material3` 1.4.0) with dynamic color
  on Android 12+ and a hand-picked static fallback palette below that.
- Retrofit + OkHttp + kotlinx.serialization for REST; OkHttp for SSE streaming.
- Pure, Android-free `core/` + `dto/` (state reducers, SSE parser, DTO mappers)
  covered by JVM unit tests; UI/ViewModels/crypto verified in CI.
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

Only the latest release is guaranteed working — older tags marked
"⚠️ BROKEN" in their notes should not be used.
