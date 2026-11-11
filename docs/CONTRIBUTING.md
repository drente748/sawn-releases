# CONTRIBUTING.md — Development workflow & conventions

Ways of working for Sawn (`com.sawn.app`). Read `spec.md` for architecture and the
privacy model before your first change.

## Module rules

| Module | Depends on | Rule |
|--------|-----------|------|
| `:core:domain` | nothing | Pure Kotlin. **No Android imports, ever.** All policy decisions live here and get unit tests. |
| `:core:data` | `domain` | Room + DataStore only behind domain interfaces. Never leak Room entities past this module. |
| `:app` | `domain`, `data` | Compose UI, services, Hilt wiring. No business logic in UI code. |

Dependency direction is one-way: `app → data → domain`. If you need a policy
decision in `app`, it belongs in `domain` first.

## Invariants (do not break these)

- **Streak source of truth** is the Room table `protected_days`. Derived values
  (total protected days, current streak) are computed from it, never stored twice.
- **Local-only**: nothing leaves the device. No accounts, no analytics, no network
  calls except the optional update check (`update-hosting.md`).
- **Time lock** can only be extended, never shortened or cleared. Its restore path
  (`BootRestorePolicy` → `BootReceiver` → workers) must survive reboots.
- **Arabic-first i18n**: every user-facing string goes in `values/strings.xml`
  **and** `values-ar/strings.xml`, with Latin digits in both. No hardcoded strings
  in composables.

## Common tasks

### Add a feature (the usual slice)

1. Policy/state machine in `core/domain` + unit test.
2. Persistence (if any) behind a domain interface, implemented in `core/data`.
3. UI + ViewModel in `app`, wired via Hilt (`di/AppModule.kt`).
4. Strings in both locales, then `device-testing.md` cases for the feature.

### Change the database

Increment the Room version and write a real migration. Destructive migration
fallback exists only as a launch-crash safety net — do not rely on it for schema
changes (2.1.1 history). Exported schemas in `core/data/schemas/` must be updated.

### Rename/move a doc

Filenames are kebab-case, except `CHANGELOG.md` and `SECURITY.md` which stay
uppercase (tool-recognized conventions). Update every reference — grep the repo,
comments in `.kt` files reference docs too.

## Verify before pushing

```bash
./gradlew :core:domain:test :core:data:testDebugUnitTest :app:testDebugUnitTest
./gradlew lintDebug
```

All three test tasks green is the merge bar. Anything touching blocking behaviour
also gets the on-device pass in `device-testing.md` — an emulator exercises the
DNS filter and accessibility poorly.

## Release checklist

1. Bump `versionCode` / `versionName` in `app/build.gradle.kts`. The `versionCode`
   must be **higher than every APK that was ever handed out** — the updater refuses
   an APK that is not strictly newer.
2. Update the version in the README title and the *Build* section.
3. Add a `CHANGELOG.md` entry (Arabic, matching the existing format).
4. Build with the public manifest URL baked in — the address is compiled in, so an
   APK built without it can never check for updates later:
   ```powershell
   $env:SAWN_UPDATE_MANIFEST_URL = "https://drente748.github.io/warden/updates/manifest.json"
   ./gradlew.bat :app:assembleRelease
   ```
5. Gate the artifact, then copy it to `release/Sawn-<ver>.apk`:
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/verify-release.ps1 -ExpectedVersion <ver>
   ```
6. Run the repository audit — no secrets tracked, Arabic/English string parity,
   valid XML, resolvable doc links, sane version ladder:
   ```powershell
   python scripts/audit_release.py
   ```
7. Tag the commit (`v<version>`), push, and let `.github/workflows/release-apk.yml`
   build the CI APK. The workflow refuses to publish if the tag and the APK's
   `versionName` disagree.
8. For update-channel changes see `update-hosting.md`.

Signing keys are documented in `setup.md`. **Never commit** `local.properties`,
the keystore password, or any `SAWN_*` secret.
