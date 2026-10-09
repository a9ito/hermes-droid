# Hermes Droid Security Audit

Date: 2026-10-09 (second pass, pre-v0.3.0 release)
First pass: 2026-10-04 (shipped the cleartext save gate, PR #27).
Scope: the whole client app surface, to the deepest layer the owner asked for
("sampai ke paling dalam") — manifest, network layer, secret storage, token
lifecycle, URL/profile routing, input parsing, the new per-turn reasoning
controls, dependencies, build config, and the CI/release pipelines.

Method: full re-read of every security-relevant file (not spot checks), pattern
sweeps for dangerous idioms, and live verification of the facts the token's
confidentiality depends on:
- OkHttp strips the `Authorization` header on a cross-host redirect — verified
  against the real okhttp 4.12.0 source (`RetryAndFollowUpInterceptor.kt` line
  326-327: `if (!userResponse.request.url.canReuseConnectionFor(url))
  requestBuilder.removeHeader("Authorization")`).
- Dependency advisories re-queried live against OSV.dev for okhttp 4.12.0,
  retrofit 3.0.0, kotlinx-serialization-json 1.9.0, okio 3.6.0 — all CLEAN.
- The profile-name classifier and the cleartext classifier were executed against
  adversarial inputs (path-traversal profile names, public hostnames crafted to
  look local) to prove the behavior rather than assume it.

Local JVM verification: the pure core + dto + their unit tests compile with the
kotlinx-serialization compiler plugin and pass (214 test methods, 19 classes).
The UI/Compose/Retrofit/okhttp layers are CI-verified only (aapt2/AGP do not run
on this aarch64 Termux host).

## Summary

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| A | `isLocalHost` treated any host starting with `fc`/`fd` as a private IPv6 ULA, so a public `http://` host like `fc2.com` skipped the cleartext warning | Medium | FIXED (this pass) |
| 1 | Cleartext HTTP save to a public host saved silently | Medium | FIXED (pass 1, still in force) |
| 2 | App-wide `cleartextTrafficPermitted` in network_security_config | Low | Accepted (documented trade-off) |
| 3 | OkHttp redirect could leak Authorization header cross-host | Info (verified safe) | No action needed |
| 4 | Profile name used in URL path routing (path traversal / injection) | Info (verified safe) | No action needed |
| 5 | Per-turn reasoning `model_options` (new in this release) | Info (verified safe) | No action needed |
| 6 | Token field masked by default; no FLAG_SECURE | Info | Accepted |
| 7 | allowBackup=true with datastore excluded | Info | Verified OK |
| 8 | Pinned dependencies clean of known vulns | Info | Verified OK (OSV, re-run) |
| 9 | No logging of token, request bodies, or reasoning anywhere | Info | Verified OK |

No High or Critical findings. One real defect (A) was found by this deeper pass
and fixed; it was a latent bug in the pass-1 cleartext gate itself. The app's
posture for a self-hosted client is otherwise sound: hardware-backed token
encryption, no logging, no exported attack surface beyond the launcher activity,
no WebView, no code exec, no reflection, no dynamic class loading, strict profile
charset, images-only attachments with size and count caps.

## Finding A — public `fc*`/`fd*` hostnames misclassified as local (FIXED)

**Severity: Medium.** `CleartextPolicy.isLocalHost` is the app-level guard that
decides whether saving a plain-`http://` connection warns the user (public host)
or saves silently (loopback / private LAN). The IPv6 unique-local check was:

```kotlin
if (h.startsWith("fc") || h.startsWith("fd")) return true  // unique-local fc00::/7
```

This has no `:` requirement, so it also matches ordinary **public DNS names**
that merely begin with those two letters. Verified by executing the exact logic:

```
fc2.com            isLocalHost=true   requiresCleartextConfirmation=false
fcbarcelona.com    isLocalHost=true   requiresCleartextConfirmation=false
fd.example.net     isLocalHost=true   requiresCleartextConfirmation=false
fcm.googleapis.com isLocalHost=true   requiresCleartextConfirmation=false
```

Impact: pointing the client at such a host over plain HTTP would save with **no
warning**, and the bearer token (which guards an endpoint that can run terminal
commands) would then travel in cleartext to a public host — exactly the case
Finding 1's gate exists to catch. It silently undermined that mitigation for a
slice of hostnames.

**Fix (this pass):** gate the ULA/link-local prefix checks on the host actually
being an IPv6 literal (it is the only shape that still contains `:` after
`hostOf` strips brackets and the port). A hostname without a colon can never be
an IPv6 address, so it falls through to the normal public-host path and the
warning fires. Genuine `fc00::/7`, `fd..`, and `fe80::/10` literals still
classify as local. 3 regression tests added (public fc/fd names warn, real ULA
literals stay local). 214 pure-JVM tests green.

## Finding 1 — Cleartext save gate (in force)

**Severity: Medium.** The bearer token guards a terminal-exec endpoint; before
pass 1, typing `http://some-public-host:8642` saved with no warning and leaked
the token in plaintext to anyone on-path. `CleartextPolicy` classifies the host
(loopback 127/8, ::1, localhost; RFC1918 10/8, 172.16/12, 192.168/16;
link-local 169.254/16 + fe80::/10; IPv6 ULA fc00::/7; and `.local`/`.lan`/
`.home.arpa`/`.internal` suffixes are "local" and save silently). Everything
else over plain `http://` triggers a confirmation dialog. HTTPS never warns.
Finding A above repairs a hole in this classifier.

## Finding 2 — App-wide cleartext permission (accepted trade-off)

**Severity: Low (deliberate).** `<base-config cleartextTrafficPermitted="true">`
permits cleartext app-wide rather than scoping to loopback via `<domain-config>`.
The skill's default fix (scope to 127.0.0.1) would break the primary use case:
users point the client at a self-hosted instance by raw LAN IP typed by hand, and
network-security-config cannot match an arbitrary future LAN IP (it matches
hostnames, not CIDR ranges). With the app-level gate (Findings 1 + A) the user
explicitly confirms any cleartext-to-public-host save. The config file carries a
comment stating exactly this.

## Finding 3 — Authorization header on cross-host redirect (verified safe)

OkHttp's `RetryAndFollowUpInterceptor` removes the `Authorization` header when a
redirect cannot reuse the connection (host/scheme/port change) — confirmed in the
pinned 4.12.0 source this pass. A malicious redirect cannot exfiltrate the token
to a different host. Same-host redirects keep the header, which is correct.

## Finding 4 — Profile name in URL routing (verified safe)

v0.3.0 composes `/p/<profile>/` onto the base URL from a user-typed profile name.
`ProfileRoute.normalize` rejects anything outside `^[A-Za-z0-9._-]+$` (returns
null, which the Settings form shows as an invalid-field error), so a save never
persists a bad name. Executed against adversarial inputs:

```
"has space"  -> rejected (null)      "x/../y"   -> rejected (null)
"..%2f"      -> rejected (null)      "slash/x"  -> rejected (null)
"..",".","..-" -> accepted as literal path segments, NOT dot-collapsed by the app
```

The dotted names `.` / `..` pass the charset filter, but they are only ever
emitted as a literal `/p/../` path segment the client never resolves locally;
the host and scheme are unchanged, so there is no cross-host or
authority-escape vector, only a path the server will 404. No CRLF/space/`@`
reaches the URL. Acceptable; the charset is deliberately conservative.

## Finding 5 — Per-turn reasoning model_options (new, verified safe)

The new reasoning/fast control (`core/ReasoningControl.kt`) builds a
`model_options` JSON object from a fixed enum ladder
(`none/minimal/low/medium/high/xhigh/max/ultra`) plus a boolean `fast`. Every
value emitted is a compile-time constant from the enum's `wire` field or a
literal boolean — no user free-text enters the object, so there is no injection
surface, and a default turn emits no `model_options` at all. It carries no
credential or PII. Pinned by 10 unit tests.

## Finding 6 — Token field + FLAG_SECURE

The token field is masked (`PasswordVisualTransformation`) with an explicit
show/hide toggle. `MainActivity` does not set `FLAG_SECURE`; the only sensitive
on-screen content is that masked field, and an app-wide FLAG_SECURE would break
screenshots everywhere. Accepted; revisit if a screen ever shows a full token in
cleartext. The long-press "copy message" in session chat copies only the
assistant's answer text, never the token.

## Finding 7 — allowBackup + datastore exclusion (verified OK)

`allowBackup="true"` with `datastore/` excluded in both `backup_rules.xml`
(legacy) and `data_extraction_rules.xml` (API 31+ cloud backup + device
transfer). No token material leaves via adb backup or cloud transfer, and the
token is AES/GCM-encrypted with a non-exportable Keystore key anyway, so a
hypothetical backup copy is useless ciphertext.

## Finding 8 — Dependencies (re-queried this pass)

OSV.dev batch query returned zero advisories for okhttp 4.12.0, retrofit 3.0.0,
kotlinx-serialization-json 1.9.0, and okio 3.6.0. All other deps are
AndroidX/Compose/Kotlin pinned via the version catalog + Compose BOM; no dynamic
version ranges, no SNAPSHOTs. The Gradle wrapper pins a
`distributionSha256Sum`, so the toolchain download is integrity-checked.

## Verified-clean checks (no findings)

- **Token at rest**: `TokenCrypto` uses an AndroidKeyStore AES-256/GCM key with a
  random 12-byte IV per encryption, stored as `Base64(iv|ciphertext+tag)`. The
  key is non-exportable; the plaintext token exists only transiently in memory to
  build the `Authorization` header. `decrypt` fails closed to null (degrades to
  "no token"), never crashes.
- **Token in transit**: injected by a single `AuthInterceptor` from an
  `AtomicReference`, never declared per-method, never placed in any error type.
  `ConnectionConfig.toString()` redacts it.
- **No TLS trust overrides**: no `trustAllCerts`, custom `TrustManager`, or
  `HostnameVerifier` anywhere.
- **No logging of secrets**: no `HttpLoggingInterceptor`, no `Log.*`/`println` in
  production code. The "activity log" on the Runs screen (`state.log`) is an
  in-model `RunLogLine` list rendered to the UI, not Logcat, and contains only
  server-sent run events. Private reasoning deltas (`delta.reasoning_content`) are
  intentionally dropped in the stateless chat streamer.
- **No exported attack surface**: `MainActivity` is the only exported component,
  launcher-only, no deep links or intent filters — another app cannot inject a
  base URL or token via Intent. No `ContentProvider`, no exported receiver/service.
- **No WebView, no `addJavascriptInterface`, no `Runtime.exec`/`ProcessBuilder`,
  no reflection (`Class.forName`/`setAccessible`), no dynamic class loading.**
- **Attachments**: images only, obtained via the system `GetContent` picker
  (the user explicitly grants each read). `ImageAttachmentLoader` rejects
  non-`image/*` content and anything over `MAX_BYTES` (4 MB); both chat paths cap
  at `MAX_PER_TURN` (4) images. Encoded as a `data:` URL in a pure builder.
- **URL parsing**: `UrlNormalizer` enforces http/https only, validates the port
  range (1..65535), and guarantees a trailing-slash base; no scheme smuggling.
- **Build/CI**: release build uses R8 minify + resource shrink with
  serialization-aware keep rules scoped to the DTO package. CI runs with
  `permissions: contents: read` and holds no secrets (runs on forks/PRs);
  release runs with `contents: write` only, decodes the keystore from a secret
  into `RUNNER_TEMP`, and verifies the APK signature with `apksigner` before
  publishing. No secret is ever committed (`.gitignore` covers `*.jks`,
  `*.keystore`, `keystore.properties`, `*.p12`, `local.properties`); a tracked-file
  sweep confirms none are in the repo.

## Recommendation

Finding A is fixed and verified. With it in place there are no outstanding High,
Critical, Medium, or Low findings beyond the two accepted/documented trade-offs.
Cut v0.3.0 once CI is green on this change.
