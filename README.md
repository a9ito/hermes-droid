# Hermes Droid

A native Android companion client for controlling your own running
[Hermes Agent](https://hermes-agent.nousresearch.com) instance from your phone —
chat with it, browse and manage its sessions, watch it work, inspect its skills
and toolsets, drive background runs and scheduled jobs, and point it at whichever
instance you run.

> **Naming:** *Hermes Droid* is this Android app. *Hermes Agent* is the agent it
> talks to — the thing you self-host and point the app at. This project is an
> independent client for that server, not the server itself.

Built with Kotlin + Jetpack Compose and a Material 3 **Expressive** UI.

## Features

- **Chat** — a quick, stateless conversation over the OpenAI-compatible
  `/v1/chat/completions` endpoint, with streaming (SSE) replies and optional
  **image attachments**. Good for one-off questions; history lives only on the
  phone for the current screen.
- **Sessions** — the persistent side. List, open, create (with a chosen model
  and system prompt), rename, fork, **pin**, **archive** and delete the real
  server-side sessions your instance keeps (`/api/sessions`). The list has live
  text **search** and a **source filter** (CLI, Discord, api_server, ...), and
  shows forked sessions. Opening one loads its stored transcript and streams
  each new turn through `/api/sessions/{id}/chat/stream`, so the conversation is
  durable and shared with every other Hermes surface (CLI, Discord, desktop).
  Each turn shows the agent's **live activity** as it works (reasoning, tool
  calls with status, mid-turn commentary), completed turns keep a
  **collapsible reasoning** trace, and the transcript can **reveal
  compaction-archived turns**. Pinned sessions float to the top; each session
  can be locked to a specific model from the picker in its app bar.
- **Runs** — submit durable background runs and watch their live SSE event
  stream, with server-side tool-call approvals, steering and stop
  (`/v1/runs`). The tab for driving longer agent work rather than a chat.
- **Tools** — a read-only viewer for the instance's installed **skills**
  (`/v1/skills`) and configurable **toolsets** (`/v1/toolsets`), including each
  toolset's enabled/configured state and the concrete tools it expands to.
- **Jobs** — view and manage the instance's scheduled/cron jobs: create,
  **edit**, pause, resume, run-now and delete (`/api/jobs`).
- **Status** — the instance's readiness, gateway state, active agents,
  connected platforms, and model, from `/health/detailed` + `/v1/models`.
- **Settings** — point the app at your Hermes Agent API server (host/URL, port,
  token), optionally selecting a **multiplex profile** (routes through
  `/p/<profile>/`). The token is encrypted with an AndroidKeyStore-backed
  AES/GCM key and is never logged or stored in plaintext. An **Appearance**
  section customizes theme mode, dynamic color, pure-black (OLED), accent
  palette, font, display size, and corner style.

Feature tabs beyond Settings are gated behind a saved host + token, and each
gates itself on the instance's reported **capabilities** so a surface the
instance doesn't support stays out of the way.

Navigation follows Material 3: **Chat**, **Sessions** and **Runs** sit in the
bottom bar; **Tools**, **Jobs**, **Status** and **Settings** live behind a
"More" menu.

## How to use

You need a Hermes Agent instance running its **API server**, and network
reachability from your phone to that instance.

### 1. Turn on the API server on your instance

The app talks to the Hermes Agent API-server platform, not the CLI. Enable it
and note the key:

```bash
# On the machine running Hermes Agent:
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
- **Runs** drives longer background work with live tool-call control.
- **Chat** is the throwaway scratchpad.
- **Tools**, **Jobs** and **Status** show and steer what the instance can do.

## Tech

- Kotlin, Jetpack Compose, single `:app` module, manual DI (no Hilt/Dagger).
- `minSdk` 26, `compileSdk`/`targetSdk` 36 (Android 16).
- Material 3 Expressive (`androidx.compose.material3` 1.4.0) with dynamic color
  on Android 12+ and a hand-picked static fallback palette below that.
- Retrofit + OkHttp + kotlinx.serialization for REST; OkHttp for SSE streaming.
- Pure, Android-free `core/` + `dto/` (state reducers, SSE parser, DTO mappers)
  covered by JVM unit tests; UI/ViewModels/crypto verified in CI.
- English + Indonesian localization; adding a locale is "add one file."

The app id stays `com.a9ito.hermesagent` for install/update continuity across
releases, even though the app is branded Hermes Droid.

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

## License

Released under the [MIT License](LICENSE) — free to use, modify and
redistribute, no warranty.
