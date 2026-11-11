# Sawn site activation guide (to be published on sawnapp.example.com later)

> This guide ships to your website later: same steps as the in-app wizard, written for the web page (screenshots later).

## Installation requirements (Android 8.0+)

1. **Download the APK** from the official download page (signed `.apk`).
2. **Verify file integrity**:
   ```
   certutil -hashfile Sawn-2.2.0.apk SHA256   # Windows
   sha256sum Sawn-2.2.0.apk                    # Linux/macOS
   ```
   Compare against the published SHA-256 fingerprint in the update manifest: `https://drente748.github.io/warden/updates/manifest.json`.
3. **Allow install from this source** (once):
   - Open the `.apk` → "needs permission to install apps from unknown sources" → **Settings** → toggle on → back → **Install**.
4. **Play Protect**: "This app may be unsafe" → **More details** → **Install anyway**.
   - (Play Protect warns on any off-store install — not proof of harm.)
5. **The app opens the setup wizard** automatically.

## Wizard steps (inside the app)

1. **Allow restricted settings (Android 13+, sideload)**:
   - **App info** → **⋮** menu → **Allow restricted settings**.
   - Return to the wizard — "Done ✓" celebrates.
2. **Accessibility** (required) — app blocking + Reels/Shorts. Button → `Settings.ACTION_ACCESSIBILITY_SETTINGS` + periodic check.
3. **Sawn local network (VPN)** (required) — DNS-only, never inspects visits. `VpnService.prepare` + "sees nothing".
4. **Notifications** (POST_NOTIFICATIONS) — so "protection running" reaches you.
5. **Battery exemption** (recommended) — direct request → single system dialog; if denied: OEM links + "you can skip".
6. **Optional**: Always-on VPN · Strict Private DNS (loophole explained) · Device Admin (prevent uninstall) · Notification access (mute blocked-app notifications).

## Notes

- Every step is "skippable" except (2)(3) — protection won't start without them (clear message).
- Play Protect won't disappear — guide the user, don't hide it.
- No mandatory internet; lists are local; list updates are manual and optional.
- 2.0 starts clean — no migration from Warden 1.x.