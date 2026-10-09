# Release notes

House style: `## What's Changed`, sectioned bullets, no em-dash. Versions are
derived from the pushed git tag (release.yml strips the leading `v` for
`versionName`; `versionCode` is the CI run number), so there is no committed
version literal to bump: the version is chosen when the tag is pushed.

## v0.3.3

A second security-hardening release. It closes the defense-in-depth items left
open after v0.3.2 (which fixed the three confirmed audit findings). No feature
changes, same storage format and signing key, so existing users keep their saved
connection and token.

### Security

- The app window is now marked secure, so a visible token or conversation does
  not show up in the recents thumbnail, in screenshots, or on a non-secure
  external display. In-app screenshots are blocked as a result.
- The token field (and the rest of the UI) is opted out of the system autofill
  framework, so the token is never offered to or stored by an autofill provider.
- Touches that arrive while another app is drawing over the window are now
  ignored, so an overlay cannot trick a tap on a delete control or the
  tool-approval dialog (tapjacking).
- Location (GPS) metadata is stripped from a photo before it is attached and
  sent, so a picture's coordinates are not forwarded to the agent.

### Notes

- Verified on a developer device with the project's JVM unit tests (235 passing).
  These are UI and image-handling changes, so the full Android build and lint run
  in CI; a green CI run on the tagged commit is required before release, and the
  screen-capture, autofill, and overlay behaviors want a quick on-device check.
- Still tracked for later: TLS certificate pinning for HTTPS instances, Gradle
  dependency verification, and a soft cap on total transcript length.

### Upgrading from v0.3.2

Install over it, no settings change needed. The APK is signed with the same key,
so your saved connection and token are kept.

## v0.3.2

A security-hardening release. Three issues found in a full-depth audit of the
client are fixed. No feature changes, and existing users keep their saved
connection and token (same storage format, same signing key).

### Security

- Bounded the Server-Sent Events reader so a malicious or compromised server (or
  a man in the middle on a cleartext LAN connection) can no longer stream an
  endless line to exhaust memory and crash the app. Normal streaming is
  unchanged.
- Neutralized invisible and direction-changing characters in the text shown at
  the tool-approval gate and in tool names, so agent output cannot be crafted to
  make a command look different from what it actually runs. Normal chat text,
  including right-to-left scripts and emoji, is unaffected.
- Rejected the profile names "." and ".." which could route a request to a
  different profile than the one typed. Other profile names are unaffected.

### Notes

- Verified on a developer device with the project's JVM unit tests (235 passing).
  The full Android build and lint run in CI; a green CI run on the tagged commit
  is required before release.
- Residual hardening tracked for a later release (not in this one): optional
  FLAG_SECURE, disabling autofill on the token field, certificate pinning,
  stripping image EXIF location, and Gradle dependency verification.

### Upgrading from v0.3.1

Install over it, no settings change needed. The APK is signed with the same key,
so your saved connection and token are kept.
