# Sawn — release artefacts

This repository holds only what a phone has to be able to fetch:

- `updates/manifest.json` — the update manifest, served over GitHub Pages.
- the published `app-release.apk` as a release asset.

The source code is private and lives in `drente748/sawn`. The split is not
cosmetic: GitHub Pages and release assets on a private repository cannot be
read without credentials, and the app downloads them anonymously.
