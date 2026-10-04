# Hermes Droid Security Audit

Date: 2026-10-04 (pre-v0.3.0 release audit)
Scope: the whole client app surface — manifest, network layer, secret storage,
input parsing, dependencies, build config, CI/release pipelines — "sampai ke
paling dalam" per the owner's request.

Method: full read of every security-relevant file (not spot checks), pattern
sweeps for dangerous idioms, live verification of the one third-party behavior
the token's confidentiality depends on (OkHttp redirect handling, verified
against the 4.12.0 source, lines 323-328 of `RetryAndFollowUpInterceptor.kt`),
and an OSV.dev query per pinned dependency. Local JVM verification: 211 tests
passing including 19 new CleartextPolicy tests.

## Summary

| # | Finding | Severity | Status |
|---|---------|----------|--------|
| 1 | Cleartext HTTP save to public host saved silently | Medium | FIXED (this commit) |
| 2 | App-wide `cleartextTrafficPermitted` in network_security_config | Low | Accepted (documented trade-off) |
| 3 | OkHttp redirects could leak Authorization header cross-host | Info (verified safe) | No action needed |
| 4 | Token field visible by default (masked, with toggle) | Info | No action needed |
| 4 | No FLAG_SECURE on the token settings screen | Info | Accepted |
| 5 | allowBackup=true with datastore excluded | Info | Verified OK |
| 6 | Pinned dependencies clean of known vulns | Info | Verified OK (OSV) |
| 7 | No logging of token or request bodies anywhere | Info | Verified OK |

No High or Critical findings. The app's posture for a self-hosted client is
sound: hardware-backed token encryption, no logging, no exported attack
surface beyond the launcher activity, no WebView, no code exec, profile names
validated against a strict charset.

## Finding 1 — Cleartext save gate (FIXED)

**Severity: Medium.** The bearer token guards an endpoint that can run
terminal commands on the Hermes instance. Before this audit, typing
`http://some-public-host:8642` in Settings saved without any warning, and
every subsequent request would send the token in plaintext over the open
internet — trivially readable by anyone on-path (coffee-shop wifi, hostile
carrier, corporate middlebox).

**Fix (this commit):** `CleartextPolicy.kt` classifies the target host:
loopback (127/8, ::1, localhost), RFC1918 private (10/8, 172.16/12,
192.168/16), link-local (169.254/16, fe80::/10), IPv6 unique-local (fc00::/7),
and private DNS suffixes (.local/.lan/.home.arpa/.internal) are "local" and
save silently. Everything else over plain `http://` — i.e. a public DNS name
or public IP — triggers a confirmation dialog before the save. HTTPS never
warns. Android's network-security-config cannot express RFC1918 CIDR ranges
declaratively (it matches hostnames, not IP ranges), so this app-level
classifier is the right layer. 19 unit tests cover the classification.

## Finding 2 — App-wide cleartext permission (accepted trade-off)

**Severity: Low (documented, deliberate).** `<base-config
cleartextTrafficPermitted="true">` permits cleartext app-wide rather than
scoping it to loopback via `<domain-config>`. The skill's default fix (scope
to 127.0.0.1) would break the legitimate primary use-case: users point this
client at self-hosted instances by raw LAN IP typed by hand, and
network-security-config cannot match an arbitrary future LAN IP. With the
app-level gate (Finding 1) in place, the user explicitly confirms any
cleartext-to-public-host connection; transport policy stays permissive on
purpose. The config file carries a detailed comment explaining exactly this.

## Finding 3 — Authorization header on cross-host redirect (verified safe)

OkHttp's `RetryAndFollowUpInterceptor` strips the `Authorization` header when
a redirect changes host/connection (`canReuseConnectionFor` check, verified
against the okhttp 4.12.0 source). A malicious redirect can't exfiltrate the
bearer token to a different host. Same-host redirects keep the header, which
is fine. No action needed.

## Finding 4 — Token field default state

The token entry field is masked (PasswordVisualTransformation) with an
explicit show/hide toggle. Standard practice; no change.

## Finding 5 — FLAG_SECURE

MainActivity does not set FLAG_SECURE. The only sensitive content on screen
is the token field in Settings (masked by default). Applying FLAG_SECURE
app-wide would break screenshots everywhere. Accepted; revisit if a screen
ever shows the full token in cleartext.

## Finding 6 — allowBackup + datastore exclusion (verified OK)

`allowBackup="true"` with `datastore/` excluded in both
`backup_rules.xml` (legacy) and `data_extraction_rules.xml` (API 31+: cloud
backup and device-to-device transfer). No token material leaves the app via
adb backup or cloud transfer. The token is AES/GCM-encrypted at rest anyway,
so even a hypothetical backup copy would be useless ciphertext without the
non-exportable Keystore key.

## Finding 7 — Dependencies

OSV.dev queried per pinned version: okhttp 4.12.0, retrofit 3.0.0,
kotlinx-serialization-json 1.9.0 — zero known advisories. All other deps are
AndroidX/Compose/Kotlin libs pinned via BOM/catalog; no dynamic version
resolution, no SNAPSHOT builds.

## Verified-clean checks (no findings)

- **No TLS trust overrides**: no `trustAllCerts`, no custom `TrustManager`,
  no `HostnameVerifier` override anywhere in the codebase.
- **No logging of secrets**: no `HttpLoggingInterceptor` at all, no
  `Log.*`/`println` in production code paths; `ConnectionConfig.toString()`
  redacts the token; TokenCrypto/AuthInterceptor/streams document and enforce
  no-logging.
- **No exported attack surface**: MainActivity is the only exported
  component, launcher-only, no deeplink/intent filters — another app cannot
  inject a base URL or token via Intent.
- **No WebView, no addJavascriptInterface, no Runtime.exec/ProcessBuilder**.
- **Profile path injection (v0.3.0 feature)**: `ProfileRoute.VALID`
  (`^[A-Za-z0-9._-]+$`) blocks path traversal, header injection, and
  profile-name confusion; `normalize()` rejects anything invalid. Tested.
- **URL parsing**: UrlNormalizer validates scheme (http/https only), port
  range, and authority structure; profile prefix composes onto a
  path-normalized base. No CRLF or header injection vectors found.
- **Build/CI**: release minify+shrink with serialization-aware ProGuard
  rules; CI `permissions: contents: read`, release `contents: write` only;
  no secrets in CI; debug artifact uploaded is fine (debug builds have no
  secrets baked in).
- **Release signing**: four GitHub Actions secrets hold keystore + passwords;
  the keystore itself lives off-repo. Signing fully operational.
- **R8/ProGuard keep rules** match the DTO model package, so no reflection
  surprise on release builds.

## Recommendation

The one real finding is fixed. Cut v0.3.0 after CI is green on this change.
