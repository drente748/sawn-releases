# Sawn — Technical Spec

## Goal

A free, open-source Android app that does everything
[AppBlock](https://play.google.com/store/apps/details?id=cz.mobilesoft.appblock) does — block
apps and websites, on schedules and other conditions, with strict/PIN protection and usage
stats — and more, with a stronger privacy posture (local-only, no accounts, auditable).

## Non-negotiable principles

1. **Local-first.** No Sawn servers, accounts, analytics, or ad SDKs. The VPN never proxies
   browsing traffic off-device; it only filters forwarded DNS. The sole network exception is
   a user-triggered HTTPS refresh of public adult-domain lists, with no identifiers sent.
   Accessibility inspects bounded on-screen labels in memory to block apps/feeds and detect
   adult URLs; it stores and transmits nothing.
2. **Auditable.** Every permission's use is visible in code and documented in
   [SECURITY.md](SECURITY.md). This matters because a blocker asks for VPN + accessibility,
   exactly the permissions malware wants.
3. **Hard to bypass, easy to leave.** Strict mode makes impulsive disabling hard, but the
   user is always ultimately in control (no dark patterns, clean uninstall when not in strict
   mode).

## Architecture

```
                        ┌────────────────────┐
   Compose UI  ───────► │   SawnViewModel   │
   (screens)            └─────────┬──────────┘
                                  │
                        ┌─────────▼──────────┐
                        │   AppContainer      │  (service locator; swap for Hilt later)
                        │  repo / settings /  │
                        │     blockEngine     │
                        └───┬────────────┬────┘
                            │            │
                 ┌──────────▼──┐   ┌─────▼────────────┐
                 │ BlockRepo   │   │  SettingsStore   │
                 │ (Room DAO)  │   │  (DataStore)     │
                 └─────────────┘   └──────────────────┘
                            ▲
        ┌───────────────────┼───────────────────────┐
        │                   │                        │
┌───────▼────────┐  ┌───────▼───────────┐   ┌────────▼─────────┐
│ SawnVpnService│  │ AppBlockA11yService│   │  BootReceiver    │
│ DNS sinkhole    │  │ foreground-app     │   │  re-arm on boot  │
│ (websites)      │  │ block (apps)       │   └──────────────────┘
└─────────────────┘  └────────────────────┘
```

### Key files
- `vpn/SawnVpnService.kt` — establishes a local TUN, routes only DNS into it, and either
  **sinkholes** (A → 0.0.0.0) a blocked domain or **forwards** the query to the device's own
  resolver and NATs the reply back. A public resolver is used only when Android exposes no
  system resolver. Adds itself to disallowed apps and tears down health state on failure.
- `vpn/DnsPacket.kt` — minimal IPv4/UDP/DNS parsing + response building. Deliberately handles
  the common case; TCP-DNS/IPv6/EDNS remain follow-ups.
- `accessibility/AppBlockAccessibilityService.kt` — on `TYPE_WINDOW_STATE_CHANGED`, checks the
  package against the engine and launches the intercept screen. While a social app is in front,
  `feature/SocialContentDetector.kt` checks one bounded node scan for NSFW labels, known adult
  Reddit communities, and explicit search fields. `BrowserUrlMonitor` also scans bounded
  browser nodes for adult hosts that bypassed DNS.
- `feature/SocialPlatform.kt` — supported social packages, per-platform coverage copy, and
  built-in filter guidance. Settings persist independently per platform.
- `block/BlockEngine.kt` + `block/ScheduleEvaluator.kt` — the single source of truth for
  "is blocking active now, and for what?". Combines the master switch, always-on flag, active
  schedules, per-item domains, adult/SafeSearch settings, optional DoH blocking, and the
  bundled ad/tracker list.
- `block/DomainMatcher.kt` — exact/parent-domain suffix matching in time proportional to the
  host's label count, not the size of the blocklist, plus hostname validation.
- `data/` — Room (`BlockedItem`, `Schedule`, `AccessGrant`) + DataStore settings.
- `security/PinHasher.kt` — PBKDF2-HMAC-SHA256, salted; PIN never stored in plaintext.
- `security/PinLockoutPolicy.kt` — pure elapsed/wall-clock lockout policy.
- `security/AdultListFetcher.kt` — validated manual HTTPS refresh that preserves the last
  valid list on failure.

### Blocking decision
`isBlockingActiveNow = focusEndsAt > trustedNow || (masterEnabled && (alwaysOn || anyEnabledScheduleActiveNow()))`.
The VPN calls `blockedDomains()`; the accessibility service calls `decideForPackage(pkg)`.
Strict mode and a strict focus session lock the master switch, the Quick Settings tile, early
focus exit, and in-app device-admin disable; enabling strict mode requires a PIN.

## Schedules
`Schedule` = name + `daysMask` (bit0=Mon…bit6=Sun) + start/end minute-of-day. Windows wrap
past midnight when `end <= start`. `ScheduleEvaluator` is pure and unit-testable.

## Data model migrations
Room is at `version = 2` with `exportSchema = true`; generated schemas live in `app/schemas`.
`MIGRATION_1_2` preserves existing blocklists while adding pause/limit fields and access
grants. Destructive fallback is limited to downgrades in development; real upgrades migrate.

## Roadmap detail (parity + beyond)

### Parity with AppBlock
- **Full schedule editor**: multiple windows/day, per-day times, assign specific items to a
  profile (currently schedules gate the whole list).
- **Conditions engine**: extend `BlockEngine` with pluggable `Condition`s — time (done),
  Wi-Fi SSID, location geofence, app launch-count, daily usage-limit.
- **Strict mode enforcement** (implemented): while protection is on, lock the in-app master
  switch, the Quick Settings tile, early focus exit, and in-app device-admin disable; require
  a PIN to enable strict mode. Android's own device-admin deactivation remains the documented
  emergency exit.
- **PIN lock** (implemented): gates settings, blocklist, schedules, app/feed management, and
  disable paths; includes a compounding clock-tamper-resistant lockout.
- **Adult-content and ad/tracker blocking** (implemented): offline curated host sets, optional
  Porn Guard/SafeSearch/browser fallback, and a manual validated adult-list refresh.
- **Notification blocking**: `NotificationListenerService` to suppress notifications from
  blocked apps during active windows.
- **Usage stats**: expand `StatsScreen` with per-day history, charts, and "unlock attempts".
- **Quick block**: a Quick Settings tile + a home-screen "start focus session" (Pomodoro).

### Beyond AppBlock
- Encrypted local backup + optional **E2E-encrypted sync** across your own devices (no server
  can read the blocklist).
- Community-shared, signed blocklists (opt-in), like uBlock filter lists.
- On-device "why did I open this?" journaling to build awareness, fully local.

## Testing priorities
1. `ScheduleEvaluator` — pure logic, cover midnight-wrap and day boundaries.
2. `DnsPacket` — parse/build round-trips against captured DNS packets.
3. `BlockingPolicy` / `StrictPolicy` / `AccessController` — decision tables across schedules,
   focus, grants, limits, and strict-mode combinations.
4. `DomainMatcher` / `PinLockoutPolicy` / `AdultListFetcher` parsing — suffix boundaries,
   malformed hosts, clock/boot transitions, and hostile response shapes.
5. `SocialContentDetector` — adult community/badge/search signals, Arabic and full-width
   text, recovery subreddits, educational searches, and the node budget.
6. Instrumented: VPN establish + sinkhole a known domain, network changes, revocation, and
   process death on an emulator or device.

## Known limitations
- DNS filtering covers UDP/IPv4. Apps using DNS-over-HTTPS/TLS (e.g. Chrome's Secure DNS)
  can bypass a DNS sinkhole; known DoH hosts can be blocked optionally, with the browser URL
  fallback covering only visible browser text.
- The bundled ad/tracker list does not remove first-party sponsored content, and updates are
  manual.
- Social media protection sees accessibility text and view identifiers, not pixels. Unlabelled
  photos/videos, private messages used as a bypass, and app-version changes to view ids can
  escape detection; users should also enable each platform's built-in sensitive-content
  filters. It covers native apps, not social sites inside a browser.
- Strict mode enforces the in-app disable paths, tile, focus cancellation, and in-app
  device-admin disable. Android still allows deactivating device admin from system Settings;
  that is the deliberate emergency exit, not a bypass inside Sawn.
