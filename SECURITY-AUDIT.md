# Hermes Droid Security Audit

## Full-depth audit, 2026-10-09 (pre-release, branch `security/full-audit`)

Role: defensive OWASP MASVS/MASTG review of my own open-source client. The app
holds a full-control bearer token for a self-hosted Hermes Agent, so a client
flaw can hand control of the agent to someone else. This pass goes to the
deepest layer and fixes every confirmed finding on one local branch for a single
security release.

### Threat model
1. Same-network MITM while in cleartext LAN mode.
2. A malicious or compromised server/profile: every server response (SSE frames,
   REST DTOs) is untrusted input.
3. Other apps, backups, or someone holding the unlocked device.
4. Agent output carrying prompt-injected content aimed at the UI or at the
   person approving a tool call.
5. Supply-chain / CI attackers (dependencies, workflows, release pipeline).
The Hermes Agent server itself is out of scope and was not probed.

### Method
Manual source review of every file under `app/` is primary. Corroborated with
ripgrep idiom sweeps, live OSV.dev dependency queries, reading the pinned
okhttp/okio source for behavior the token depends on, and executing the pure
classifiers against adversarial inputs. Build/lint could NOT run on this
aarch64 Termux host (no Android SDK / aapt2), so `./gradlew lintDebug` and
`assembleDebug` are `[unverified]` here and must be confirmed by CI. Pure
`core/` + `dto/` + parser logic was compiled and unit-tested on the JVM
(kotlinx-serialization compiler plugin + JUnit): 235 tests pass on this branch.

### Findings (this pass)

| ID | Severity | CWE | MASVS | File:line | Status |
|----|----------|-----|-------|-----------|--------|
| F-1 | Medium (DoS: unauthenticated/MITM server crash-loops the app; CVSS ~5.3 AV:N/AC:L/A:L) | CWE-400 | MASVS-CODE | ChatStreamer.kt:47, SessionChatStreamer.kt:47, RunEventStreamer.kt:40 | Fixed `a6e0727` |
| F-2 | Medium (approval-gate deception via prompt injection; CVSS ~5.0 integrity of the human decision) | CWE-451 | MASVS-PLATFORM | RunSseParser.kt:73/98, SessionSseParser.kt:82, SessionMappers.kt:70 | Fixed `833d0a8` |
| F-3 | Low (profile mis-routing / least astonishment; no cross-host escape) | CWE-20 | MASVS-CODE | ProfileRoute.kt:30 | Fixed `d171483` |

**F-1: Unbounded SSE line read (DoS).** All three SSE streamers consumed the
untrusted body with okio `readUtf8Line()`, which has no size bound. A hostile or
compromised server (threat 2), or a MITM in cleartext LAN mode (threat 1), can
stream a line that never contains a newline; okio grows its buffer until the
process OOMs and the app crash-loops. Fix: a pure okio `SseLineReader` that
scans only the first `limit + 1` bytes for a newline and throws
`SseLineTooLongException` past a 16 MiB cap; all three streamers read through it.
Proof: `SseLineReaderTest` (8 cases, incl. an unterminated over-limit line that
throws and a short line that is unaffected by a huge trailing tail), run on the
JVM with a real okio `Buffer`.

**F-2: Trojan-Source characters on the approval gate (CWE-451).** The agent's
output is untrusted and can carry prompt-injected content. Bidi overrides /
isolates and zero-width or invisible format characters let a string render
differently from its real byte order (CVE-2021-42574). On the run-approval
dialog a dangerous `command` could be made to display as something benign while
the human taps Allow; a tool-name chip could hide part of itself. Fix: a pure
`SafeText.forControlDisplay` replaces every bidi/zero-width/C0/C1 control char
with U+FFFD, applied ONLY to security-sensitive control identifiers (the
approval `command` + `tool`, SSE tool names, the persisted tool-name mapper).
Prose (assistant/user chat text, reasoning) is deliberately left untouched, so
legitimate RTL scripts, CJK and emoji render normally, only invisible/directional
format characters are neutralized. Proof: `SafeTextTest` (RLO, bidi isolates,
ZWSP/BOM, C0/C1 with tab+newline preserved, and prose/RTL/emoji pass-through) +
two `RunSseParserTest` regressions (RLO in a command, zero-width in a tool name).

**F-3: `.`/`..` accepted as a profile name (CWE-20).** The profile name is
composed into `/p/<profile>/`; the charset validator accepted the pure
dot-segments `.` and `..`, which an HTTP stack then collapses, silently routing
the user to a different profile instead of flagging an invalid name. No
cross-host or authority escape (host and scheme unchanged), so this is
input-validation, not SSRF. Fix: reject exactly `.` and `..` in `normalize()`.
Proof: `ProfileRouteTest.dotSegmentsRejected`.

### Status of earlier findings (re-verified against current code)

| Earlier finding | Current status |
|-----------------|----------------|
| A, `fc`/`fd` public host misread as private IPv6 | Fixed earlier (#28); re-verified present and effective by executing the classifier against `fc2.com`/`fd.example.net`. |
| B, userinfo `@` disguises a public host as local | Fixed earlier (#29); re-verified: `UrlNormalizer` rejects `@`, `hostOf` strips userinfo. |
| 1, cleartext save gate | In force; extended coverage re-verified (suffix-append, IPv4-mapped, trailing-dot forms all warn). |
| 2, app-wide cleartext permitted | Accepted, deliberate (LAN-by-IP use case); gate makes public cleartext explicit. |
| 3, Authorization on cross-host redirect | Verified safe against okhttp 4.12.0 `canReuseConnectionFor` (host && port && scheme), so https→http downgrade also strips the header. |
| 4, profile route traversal | Hardened further by F-3. |
| 5, reasoning `model_options` | Verified safe (fixed enum ladder + boolean, no free text). |
| 6, token field masked / no FLAG_SECURE | Unchanged; FLAG_SECURE remains a documented residual recommendation. |
| 7, allowBackup + datastore excluded | Verified OK (both backup_rules.xml and data_extraction_rules.xml exclude `datastore/`). |
| 8, dependencies | Re-queried OSV.dev live this pass; all CLEAN (see area 7). |
| 9, no logging of secrets | Re-verified: zero `Log.*`/`println`/`printStackTrace`, no logging interceptor. |

### Coverage of the 10 required areas
1. **Secrets & storage, checked.** AES-256/GCM, fresh 12-byte IV per encrypt,
   128-bit tag, non-exportable AndroidKeyStore key; `decrypt` fails closed to
   null (never plaintext fallback). No SharedPreferences; two DataStore files
   (connection with encrypted token; appearance with no secrets), both excluded
   from backup. No logging, no clipboard copy of the token (only assistant text).
   `FLAG_SECURE` and autofill-disable are residual recommendations (behavior
   change, not applied).
2. **Transport, checked.** Cleartext loopback/LAN gate executed against
   userinfo, suffix-append (`192.168.1.1.evil.com`), IPv4-mapped IPv6,
   trailing-dot, and `172.16/12` boundary forms: no public/spoof target evades
   the warning. No trust-all/hostname-verifier bypass; Authorization stripped on
   cross-host and downgrade redirects; no logging interceptor; streaming client
   keeps connect/write timeouts.
3. **Request construction, checked.** No `@Path(encoded = true)`; Retrofit
   percent-encodes ids. `UrlNormalizer` rejects userinfo and non-http(s) schemes;
   profile charset plus the new `.`/`..` rejection (F-3).
4. **Untrusted content, checked.** No markdown/HTML renderer, no link opening,
   no WebView, no remote image loader (so no `intent:`/`javascript:`/`file:` and
   no IP/token leak via images). SSE line growth bounded (F-1); JSON parse failures
   degrade to `Ignored`; attachments are SAF `GetContent`, image-only, 4 MB / 4
   per turn. EXIF-GPS stripping is a residual recommendation.
5. **Control surfaces, checked.** Approval/tool/command text neutralized (F-2);
   destructive session/job deletes have confirm dialogs; approve carries the
   server `request_id`; run submit disabled while active (no double-submit).
   `filterTouchesWhenObscured` (tapjacking) is a residual recommendation (no clean
   Compose API).
6. **Android platform, checked.** Single exported launcher `MainActivity`, no
   deep links / intent filters / providers / services / receivers; `INTERNET` the
   only permission; `debuggable` not set; no `FileProvider`, no `PendingIntent`,
   no foreground service, no WebView. Release build: R8 minify + resource shrink
   on; no secrets in `BuildConfig` or resources.
7. **Supply chain & CI, partial.** OSV.dev live: okhttp 4.12.0, retrofit 3.0.0,
   converter-kotlinx-serialization 3.0.0, okio 3.6.0, kotlinx-serialization-json
   1.9.0, kotlinx-coroutines-core 1.10.2, and the AndroidX set, all CLEAN.
   Gradle wrapper pins `distributionSha256Sum`; repos are https (google +
   mavenCentral). CI `permissions: contents: read`, release `contents: write`;
   no `pull_request_target`. **Not checkable here:** third-party Actions are
   SHA-pinned but their current advisories, and the release secret decode/cleanup,
   can only be confirmed in CI `[unverified]`. No `gradle/verification-metadata.xml`
   (recommended hardening).
8. **Secrets in repo, checked.** Full-history scan across all refs for
   `*.jks/*.keystore/*.p12/*.pem/*.key/*.env/*.pass/local.properties` and for
   `sk-/ghp_/github_pat_/PRIVATE KEY/AKIA` tokens: none found. `.gitignore` covers
   keystores, `local.properties`, `*.apk`, `*.aab`.
9. **Privacy, checked.** No analytics, crash reporting, or third-party hosts;
   the only outbound target is the user-configured Hermes instance. en/id
   localization format-specifier parity verified (no injectable mismatch).
10. **Robustness, checked.** `core/` reducers, SSE parsers and DTO mappers
    tolerate hostile input: unknown/garbled frames → `Ignored` (never a crash or
    phantom delta), oversized lines bounded (F-1), malformed `content` extracted
    safely. 235 JVM tests cover these paths.

### Residual risks (recommendations, not code, out of this release's scope)
- No `FLAG_SECURE`: a revealed token or transcript can appear in the recents
  thumbnail / screenshots. Behavior change; recommend opt-in.
- Token field does not disable autofill: a token could be captured by an autofill
  provider. Recommend `importantForAutofill="no"`.
- No TLS certificate pinning for HTTPS deployments. New feature.
- Attached images keep EXIF/GPS; forwarded to the agent. Recommend optional strip.
- Approval dialog lacks tapjacking protection (`filterTouchesWhenObscured`); no
  clean Compose equivalent today.
- Supply-chain hardening: add Gradle dependency-verification metadata; keep
  first-party Actions SHA-pinned.
- Transcript/answer total growth is inherent to streaming (each line is now
  bounded by F-1); recommend a soft total cap.

### Verification status
Compiled and unit-tested on-device via the pure-JVM harness: 235 tests pass.
`./gradlew lintDebug`, `assembleDebug`, and the full Android build are
`[unverified]` on this host (no Android SDK/aapt2) and must be confirmed green in
CI before any tag. Branch `security/full-audit` was NOT pushed and NO tag was
created.

---

## Date: 2026-10-04 / 2026-10-09 (earlier passes, prior state)
First pass: 2026-10-04 (shipped the cleartext save gate, PR #27).
Scope: the whole client app surface, to the deepest layer the owner asked for
("sampai ke paling dalam"), manifest, network layer, secret storage, token
lifecycle, URL/profile routing, input parsing, the new per-turn reasoning
controls, dependencies, build config, and the CI/release pipelines.

Method: full re-read of every security-relevant file (not spot checks), pattern
sweeps for dangerous idioms, and live verification of the facts the token's
confidentiality depends on:
- OkHttp strips the `Authorization` header on a cross-host redirect, verified
  against the real okhttp 4.12.0 source (`RetryAndFollowUpInterceptor.kt` line
  326-327: `if (!userResponse.request.url.canReuseConnectionFor(url))
  requestBuilder.removeHeader("Authorization")`).
- Dependency advisories re-queried live against OSV.dev for okhttp 4.12.0,
  retrofit 3.0.0, kotlinx-serialization-json 1.9.0, okio 3.6.0, all CLEAN.
- The profile-name classifier and the cleartext classifier were executed against
  adversarial inputs (path-traversal profile names, public hostnames crafted to
  look local) to prove the behavior rather than assume it.

Local JVM verification: the pure core + dto + their unit tests compile with the
kotlinx-serialization compiler plugin and pass (217 test methods, 19 classes).
The UI/Compose/Retrofit/okhttp layers are CI-verified only (aapt2/AGP do not run
on this aarch64 Termux host).

## Summary

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| B | Userinfo in the base URL (`localhost@8.8.8.8`) disguised a public host as local, bypassing the cleartext gate | Medium | FIXED (this pass) |
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

No High or Critical findings. Two real defects (A and B) were found by this
deeper pass and fixed; both were latent holes in the pass-1 cleartext gate
itself, and both let the terminal-exec bearer token leak in cleartext to a
public host with no warning under a crafted base URL. The app's posture for a
self-hosted client is otherwise sound: hardware-backed token encryption, no
logging, no exported attack surface beyond the launcher activity, no WebView, no
code exec, no reflection, no dynamic class loading, strict profile charset,
images-only attachments with size and count caps.

## Finding B, userinfo disguises a public host as local (FIXED)

**Severity: Medium.** The cleartext gate decides "warn or not" from the host it
extracts from the base URL. A URL authority may legally carry *userinfo* before
the host: `scheme://user:pass@host:port/`. Neither `UrlNormalizer` nor
`CleartextPolicy.hostOf` accounted for it, so a crafted authority split the two
layers against each other. Verified by executing both components:

```
input host field: localhost:8642@8.8.8.8:9999
UrlNormalizer -> ACCEPTED  http://localhost:8642@8.8.8.8:9999/
CleartextPolicy.hostOf -> "localhost"   isLocalHost=true   requiresConfirmation=FALSE
OkHttp actually connects to -> 8.8.8.8:9999  (the host after '@'; userinfo is stripped at connect)
```

So the classifier saw the fake local host `localhost` and saved silently, while
every request then went in cleartext to the real public host `8.8.8.8` carrying
the bearer token. Same class of bypass as Finding A, by a different mechanism.

**Fix (this pass), defense in depth at both layers:**
- `UrlNormalizer` now rejects any authority containing `@` as
  `InvalidHost`, a Hermes base URL has no legitimate use for userinfo (the only
  credential is the Bearer token, which rides in the Authorization header), so a
  save with userinfo never persists.
- `CleartextPolicy.hostOf` now strips userinfo (`substringAfterLast('@')`) before
  reading the host, so even if a userinfo URL reached the classifier by any other
  path, it resolves the real host after `@`, never the deceptive part before it.

5 new tests (3 on `UrlNormalizer`, 2 on `CleartextPolicy`) pin the rejection and
the real-host resolution. 217 pure-JVM tests green.

## Finding A, public `fc*`/`fd*` hostnames misclassified as local (FIXED)

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
commands) would then travel in cleartext to a public host, exactly the case
Finding 1's gate exists to catch. It silently undermined that mitigation for a
slice of hostnames.

**Fix (this pass):** gate the ULA/link-local prefix checks on the host actually
being an IPv6 literal (it is the only shape that still contains `:` after
`hostOf` strips brackets and the port). A hostname without a colon can never be
an IPv6 address, so it falls through to the normal public-host path and the
warning fires. Genuine `fc00::/7`, `fd..`, and `fe80::/10` literals still
classify as local. 3 regression tests added (public fc/fd names warn, real ULA
literals stay local). 214 pure-JVM tests green.

## Finding 1, Cleartext save gate (in force)

**Severity: Medium.** The bearer token guards a terminal-exec endpoint; before
pass 1, typing `http://some-public-host:8642` saved with no warning and leaked
the token in plaintext to anyone on-path. `CleartextPolicy` classifies the host
(loopback 127/8, ::1, localhost; RFC1918 10/8, 172.16/12, 192.168/16;
link-local 169.254/16 + fe80::/10; IPv6 ULA fc00::/7; and `.local`/`.lan`/
`.home.arpa`/`.internal` suffixes are "local" and save silently). Everything
else over plain `http://` triggers a confirmation dialog. HTTPS never warns.
Finding A above repairs a hole in this classifier.

## Finding 2, App-wide cleartext permission (accepted trade-off)

**Severity: Low (deliberate).** `<base-config cleartextTrafficPermitted="true">`
permits cleartext app-wide rather than scoping to loopback via `<domain-config>`.
The skill's default fix (scope to 127.0.0.1) would break the primary use case:
users point the client at a self-hosted instance by raw LAN IP typed by hand, and
network-security-config cannot match an arbitrary future LAN IP (it matches
hostnames, not CIDR ranges). With the app-level gate (Findings 1 + A) the user
explicitly confirms any cleartext-to-public-host save. The config file carries a
comment stating exactly this.

## Finding 3, Authorization header on cross-host redirect (verified safe)

OkHttp's `RetryAndFollowUpInterceptor` removes the `Authorization` header when a
redirect cannot reuse the connection, confirmed in the pinned 4.12.0 source this
pass (`RetryAndFollowUpInterceptor` line 326-327, and the predicate in
`Util.kt`):

```kotlin
fun HttpUrl.canReuseConnectionFor(other: HttpUrl): Boolean =
    host == other.host && port == other.port && scheme == other.scheme
```

Because the check includes `scheme`, even a same-host **https -> http downgrade**
redirect strips the token, not just a host change. A malicious redirect cannot
exfiltrate the token to a different host or downgrade it onto cleartext.
Same-host, same-scheme redirects keep the header, which is correct.

## Finding 4, Profile name in URL routing (verified safe)

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

## Finding 5, Per-turn reasoning model_options (new, verified safe)

The new reasoning/fast control (`core/ReasoningControl.kt`) builds a
`model_options` JSON object from a fixed enum ladder
(`none/minimal/low/medium/high/xhigh/max/ultra`) plus a boolean `fast`. Every
value emitted is a compile-time constant from the enum's `wire` field or a
literal boolean, no user free-text enters the object, so there is no injection
surface, and a default turn emits no `model_options` at all. It carries no
credential or PII. Pinned by 10 unit tests.

## Finding 6, Token field + FLAG_SECURE

The token field is masked (`PasswordVisualTransformation`) with an explicit
show/hide toggle. `MainActivity` does not set `FLAG_SECURE`; the only sensitive
on-screen content is that masked field, and an app-wide FLAG_SECURE would break
screenshots everywhere. Accepted; revisit if a screen ever shows a full token in
cleartext. The long-press "copy message" in session chat copies only the
assistant's answer text, never the token.

## Finding 7, allowBackup + datastore exclusion (verified OK)

`allowBackup="true"` with `datastore/` excluded in both `backup_rules.xml`
(legacy) and `data_extraction_rules.xml` (API 31+ cloud backup + device
transfer). No token material leaves via adb backup or cloud transfer, and the
token is AES/GCM-encrypted with a non-exportable Keystore key anyway, so a
hypothetical backup copy is useless ciphertext.

## Finding 8, Dependencies (re-queried this pass)

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
  launcher-only, no deep links or intent filters, another app cannot inject a
  base URL or token via Intent. No `ContentProvider`, no exported receiver/service.
- **No WebView, no `addJavascriptInterface`, no `Runtime.exec`/`ProcessBuilder`,
  no reflection (`Class.forName`/`setAccessible`), no dynamic class loading.**
- **Attachments**: images only, obtained via the system `GetContent` picker
  (the user explicitly grants each read). `ImageAttachmentLoader` rejects
  non-`image/*` content and anything over `MAX_BYTES` (4 MB); both chat paths cap
  at `MAX_PER_TURN` (4) images. Encoded as a `data:` URL in a pure builder.
- **URL parsing**: `UrlNormalizer` enforces http/https only, rejects userinfo
  (`@` in the authority, Finding B), validates the port range (1..65535), and
  guarantees a trailing-slash base; no scheme smuggling.
- **IP-obfuscation probe (this pass)**: the cleartext classifier was run against
  decimal/octal/hex/single-integer host forms. Kotlin's `toIntOrNull` parses an
  octet as decimal, so `010.0.0.1` reads as `10.0.0.1`, still genuinely in
  10/8, so classifier-local matches reality-local. Non-dotted-quad forms (hex
  octets, a single 32-bit integer) fail the four-octet test and fall through to
  "public", which only ever adds a warning. No public address is ever
  classified local (the only dangerous direction).
- **Approval command is display-only**: the run approval dialog shows the
  server-advertised `command` string in a plain `Text` with `maxLines`; the app
  never executes it. The only action is POSTing the chosen approval verb back.
- **SSE parsers fail closed**: both `SessionSseParser` and `RunSseParser` wrap
  JSON decode in `runCatching` and map anything unparseable to `Ignored`, so a
  malformed or hostile frame cannot crash the stream or emit a phantom delta.
  Only a fixed allowlist of event names is acted on; unknown events are ignored.
- **No polymorphic deserialization**: no `PolymorphicSerializer`,
  `SerializersModule`, class discriminator, or contextual serializer anywhere , 
  there is no deserialization-gadget surface. Every `Json` instance that touches
  server data sets `ignoreUnknownKeys`.
- **Attachment OOM**: `readBytes()` runs inside a `catch (Throwable)` (so an
  `OutOfMemoryError` on a huge stream degrades to "unreadable" rather than
  crashing), the size cap rejects >4 MB, and the picker is `image/*`-filtered.
- **Build/CI**: release build uses R8 minify + resource shrink with
  serialization-aware keep rules scoped to the DTO package. CI runs with
  `permissions: contents: read` and holds no secrets (runs on forks/PRs);
  release runs with `contents: write` only, decodes the keystore from a secret
  into `RUNNER_TEMP`, and verifies the APK signature with `apksigner` before
  publishing. No secret is ever committed (`.gitignore` covers `*.jks`,
  `*.keystore`, `keystore.properties`, `*.p12`, `local.properties`); a tracked-file
  sweep confirms none are in the repo.

## Recommendation

Findings A and B are fixed and verified (217 pure-JVM tests green). With them in
place there are no outstanding High, Critical, Medium, or Low findings beyond the
two accepted/documented trade-offs (app-wide cleartext permission, no
FLAG_SECURE). The client is in a releasable security posture. Cut v0.3.0 once CI
is green on the merged fixes.
