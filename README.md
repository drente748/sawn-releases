# Sawn 2.2.0

**صَوْن (Sawn) — a local, account-free, tracking-free Android blocker.** Blocks websites (via an
on-device DNS filter), apps, adult content, ads/trackers, and the endless scroll (Reels/Shorts
only), with schedules, focus sessions, strict mode, a time lock, and a streak rebuilt from a
single source of truth. Arabic-first with full English; Latin digits everywhere.

> **Distribution:** side-loaded APK, later a Google Play listing. Install guides:
> [`docs/install-ar.md`](docs/install-ar.md) · [`docs/install-en.md`](docs/install-en.md).

---

## How blocking works

Sawn uses **two independent layers**, so there is no single point of bypass:

| Layer | Mechanism | Blocks |
|-------|-----------|--------|
| **Website filtering** | A local, on-device `VpnService` that intercepts DNS and blackholes queries for blocked domains. Non-blocked queries are forwarded to the resolver your network already uses. | Websites in **every** browser and app |
| **App blocking** | An `AccessibilityService` detects when a blocked app comes to the foreground and shows a full-screen block screen. | Native apps |

The VPN is **local-only**: DNS is filtered on-device, allowed lookups go to your network's
resolver (with a fallback only when Android exposes none), and no query is logged. Other
traffic is not proxied. Sawn excludes itself so it never filters its own traffic.
Full architecture and privacy model: [`docs/spec.md`](docs/spec.md).

### Time lock

A lock written from the home screen freezes protection: it cannot be turned off, its core
switches (adult filter, SafeSearch, DoH blocking) are re-asserted, uninstall protection cannot
be disabled, and **"wipe all data" is refused**. It can only be **extended**, never shortened or
cleared, and it ends by itself when its timer expires. A live lock is restored after a reboot
(and on any cold process start) by `BootRestorePolicy`.

## Project structure

```text
warden/
├── app/                     # Compose UI, services (VPN / Accessibility / DeviceAdmin /
│                            #   boot / QS tile / workers), ViewModels, Hilt
├── core/
│   ├── domain/              # Pure Kotlin (no Android): policies, schedules, backup
│   │                        #   codec, feature detectors — fully unit-tested
│   └── data/                # Room (streak source of truth) + DataStore repos,
│                            #   exported schemas in core/data/schemas/
├── docs/                    # All documentation (table below)
├── gradle/                  # Version catalog (libs.versions.toml) + wrapper
├── .github/workflows/       # Release APK CI
└── release/                 # Built APKs (git-ignored)
```

## Modules

- `:core:domain` — pure Kotlin, no Android: day/streak policy, schedules, lock policy, backup
  codec, feature detectors. Fully unit-tested.
- `:core:data` — Room (`protected_days` is the streak source of truth) + DataStore repositories
  behind domain interfaces.
- `:app` — Compose UI, services (VPN / Accessibility / DeviceAdmin / QS tile / boot / workers),
  ViewModels, Hilt.

## Feature status (2.2.0)

- [x] Single-page home: protection hero, rich streak card, daily summary, focus, urge shield,
      time lock, tools
- [x] "Challenge & focus" section: 30-day challenge, deep focus (whitelist enforcement with a
      PIN-gated exit window), breathing exercises (4-7-8, grounding, delay timer)
- [x] Time lock with 30-minute → 6-month presets plus a custom duration
- [x] Streak accuracy: recorded by the filter itself at midnight rollover, future-dated rows
      ignored, total protected days derived from the same log
- [x] Boot/update restore: `BootRestorePolicy` + `BootReceiver` + `KeepAliveWorker` +
      `GuardHealthWorker`
- [x] Encrypted-free, PIN-gated management sections; local profile; AR/EN with RTL
- [x] Optional in-app update check against a free GitHub Pages manifest, with SHA-256 and
      package/version verification before the Android installer opens
- [x] Automatic updates (on by default): periodic manifest check, background download and
      verification, and a "ready to install" notification that opens the system installer on
      the already-verified file. Android always requires the user's own tap to install — no
      app can install silently.
- [x] Plan B fully wired: night guard (PIN unlock), trigger analyzer with an hourly chart,
      accountability partner, encrypted `.sawnbak` backup (AES-256-GCM, real export/import),
      parent mode with a separate parent PIN that locks management
- [ ] Year heatmap, biometric unlock

  The biometric helper (`BiometricHelper`) is in place but not wired to any
  screen yet — every path is still PIN-gated.
- [x] Home-screen widget (2×2): protected-day streak + protection state, refreshed
  whenever protection state changes

## Build

```bash
./gradlew :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

Target: `minSdk 26`, `targetSdk 36`, `versionName 2.2.0`, `versionCode 31`.

Release signing reads `SAWN_STORE_FILE`, `SAWN_STORE_PASSWORD`, `SAWN_KEY_ALIAS`,
`SAWN_KEY_PASSWORD` from the environment or `local.properties` (all or none). Keep
`sawn-release.keystore` and those secrets safe: losing them blocks all future updates of
already-installed copies. The keystore and `local.properties` are git-ignored on purpose.

### Release

```bash
# 1. bake the public manifest URL into the build (the URL is compiled in, not fetched)
export SAWN_UPDATE_MANIFEST_URL="https://drente748.github.io/warden/updates/manifest.json"
./gradlew :app:assembleRelease

# 2. gate the artifact
powershell -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -ExpectedVersion 2.2.0

# 3. one-time hosting setup (Actions secrets + GitHub Pages), then tag and push
powershell -ExecutionPolicy Bypass -File scripts/setup-update-hosting.ps1 -Version 2.2.0
git tag v2.2.0 && git push origin v2.2.0
```

An APK built without `SAWN_UPDATE_MANIFEST_URL` can never check for updates later —
the address is compiled in. Details: [`docs/update-hosting.md`](docs/update-hosting.md).

## Documentation

| Document | Contents |
|---|---|
| [`docs/CONTRIBUTING.md`](docs/CONTRIBUTING.md) | Development workflow, conventions, release checklist |
| [`docs/spec.md`](docs/spec.md) | Architecture, data model, privacy model |
| [`docs/privacy.md`](docs/privacy.md) | What is stored, what leaves the device |
| [`docs/play-compliance.md`](docs/play-compliance.md) | Play policy mapping |
| [`docs/SECURITY.md`](docs/SECURITY.md) | Threat model, known limits |
| [`docs/setup.md`](docs/setup.md) | Local toolchain setup |
| [`docs/device-testing.md`](docs/device-testing.md) | On-device test plan |
| [`docs/feature-organization.html`](docs/feature-organization.html) | Design exploration: how the features could be grouped and ordered (open in a browser) |
| [`docs/update-hosting.md`](docs/update-hosting.md) | Free update hosting (GitHub Pages/Releases) |
| [`scripts/setup-update-hosting.ps1`](scripts/setup-update-hosting.ps1) | One-time setup of Actions secrets + GitHub Pages (publishes nothing) |
| [`scripts/verify-release.ps1`](scripts/verify-release.ps1) | Release gate: signing certificate, version, and manifest URL inside the APK |
| [`scripts/audit_release.py`](scripts/audit_release.py) | Pre-release audit: secrets, string parity, XML, doc links, version ladder |
| [`scripts/extract_changelog.py`](scripts/extract_changelog.py) | Pulls release notes out of `docs/CHANGELOG.md` for the update manifest |
| [`docs/CHANGELOG.md`](docs/CHANGELOG.md) | Version history |
| [`docs/install-ar.md`](docs/install-ar.md) · [`docs/install-en.md`](docs/install-en.md) | Install guides |
| [`docs/site-activation-ar.md`](docs/site-activation-ar.md) · [`docs/site-activation-en.md`](docs/site-activation-en.md) | Per-OEM activation |

Built APKs are kept out of git in `release/` (ignored).

## License

Code: see repository license. Cairo font: SIL Open Font License 1.1 (`app/licenses/Cairo-OFL.txt`).
