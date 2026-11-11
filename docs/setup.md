# Setup & Build

Sawn is a standard Gradle/Android project. Requirements:

| Item | Version |
|------|---------|
| JDK | 17 or newer (this machine uses 21) |
| Gradle | 8.13 (via the wrapper — don't install it separately) |
| Android Gradle Plugin | 8.13.2 |
| Android SDK | platform **android-36**, build-tools **36.0.0** |
| Device | Android 8.0 (API 26) or newer |

`compileSdk` and `targetSdk` are 36. Play requires target 36 or higher for new and updated
apps as of 31 Aug 2026, so do not lower these to speed up a build.

## Paths on this machine

- SDK: `C:/Users/pc/tools/android-sdk` (recorded in `local.properties` as `sdk.dir`)
- JDK: `C:/Users/pc/tools/jdk-21.0.12.1+1` (exported as `JAVA_HOME`)

`local.properties` is machine-local and never committed. Copy `local.properties.example`
and point `sdk.dir` at your own SDK if you build elsewhere.

## Building

From Git Bash in the project root:

```bash
./gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests (scheduling, policy, DNS, PIN lockout)
./gradlew lintDebug            # Android lint
```

Add `--offline` once every dependency is cached if you want to skip network checks.

## Release signing

Release signing is read from the environment or `local.properties`; all four `SAWN_*`
values must be present together, or none of them (an unsigned release is then produced):

```properties
SAWN_STORE_FILE=sawn-release.keystore
SAWN_STORE_PASSWORD=...
SAWN_KEY_ALIAS=...
SAWN_KEY_PASSWORD=...
```

Release builds are minified with `proguard-android-optimize.txt` + `proguard-rules.pro`, and
signed with v1 + v2 + v3 so sideloaded copies verify on older tooling and the key can be
rotated for Play later.

**The keystore is the update identity of the app.** Keep a backup off-machine; losing it
means no future update can be installed over an existing install.

## Running on a phone

1. *Settings → About phone → Build number* 7× to unlock Developer options, then enable **USB debugging**.
2. Plug in over USB and authorize the computer.

```bash
adb devices                # confirm the phone shows up
./gradlew installDebug
adb shell monkey -p com.sawn.app 1
```

Sawn only behaves correctly on a real device: the DNS filter, accessibility detection and
usage stats are all things an emulator exercises poorly.

### First-run permissions

The in-app **الإعداد** screen drives these and re-reads their state every time you return
from system settings:

- **Accessibility** — app blocking and in-app content detection (required).
- **Restricted settings** — Android 13+ hides Accessibility from sideloaded installs until
  you allow it from *App info → ⋮ → Allow restricted settings*. The setup screen shows the
  card for this when it detects a sideload.
- **Notifications** — the ongoing "protection active" notice.
- **Usage access** — screen time and daily limits.
- **Always-on VPN** — makes Android restart the filter after a reboot. Leave
  *Block connections without VPN* off.
- **Battery optimization → Unrestricted** — so the system doesn't stop the filter.
- **Private DNS** — set to *Off*; a strict Private DNS profile bypasses local filtering.
- **VPN consent** — appears the first time you turn protection on.

## Troubleshooting

- **`Unable to locate a Java Runtime`** → `JAVA_HOME` doesn't point at a JDK 17+.
- **`SDK location not found`** → `local.properties` is missing or wrong.
- **`Failed to find target with hash string 'android-36'`** → install it:
  `sdkmanager "platforms;android-36" "build-tools;36.0.0"`
- **First build downloads a lot** → expected: the wrapper fetches Gradle 8.13 and AGP 8.13.2
  into `~/.gradle`, then builds go offline-able.
