"""Extracts the release notes for a version from docs/CHANGELOG.md.

This is the single source of truth for the `changelog` field of the update
manifest: the release workflow calls it, and it is testable locally before any
release is published.

Usage:
    python scripts/extract_changelog.py --version 2.2.0
    python scripts/extract_changelog.py --version 2.2.0 --out notes.txt

The changelog is written in Arabic and the app is Arabic-first, so the notes
are published verbatim rather than translated.
"""
import argparse
import pathlib
import sys

# A release dialog is not a changelog page: keep the first few notes that fit
# the dialog, and never cut a sentence mid-line.
MAX_NOTES = 12
MAX_CHARS = 1200


def changelog_for(name, path=pathlib.Path("docs/CHANGELOG.md")):
    """Return the release notes for `name`, or an empty string if not found."""
    if not path.exists():
        return ""
    lines = path.read_text(encoding="utf-8").splitlines()
    start = None
    for index, line in enumerate(lines):
        if not line.startswith("## "):
            continue
        heading = line[3:].strip()
        # The heading starts with the version, then a separator: "## 2.2.0 — …".
        # Matching the exact token avoids "2.2" catching the "2.2.0" section.
        token = heading.split(" ", 1)[0].split("—", 1)[0].strip()
        if token == name:
            start = index + 1
            break
    if start is None:
        return ""
    body = []
    for line in lines[start:]:
        if line.startswith("## "):
            break
        body.append(line)
    notes = []
    for line in body:
        text = line.strip()
        if not text:
            continue
        if text.startswith("###"):
            text = text.lstrip("#").strip()
        elif text.startswith("-"):
            text = text.lstrip("-").strip()
        else:
            continue
        text = text.replace("**", "").replace("`", "")
        notes.append(text)
    notes = notes[:MAX_NOTES]
    summary = "\n".join(notes)
    if len(summary) > MAX_CHARS:
        summary = summary[:MAX_CHARS].rsplit("\n", 1)[0] + "\n…"
    return summary


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, help="versionName, e.g. 2.2.0")
    parser.add_argument("--out", help="write to this file instead of stdout")
    parser.add_argument("--file", default="docs/CHANGELOG.md", help="changelog to read")
    args = parser.parse_args(argv)

    notes = changelog_for(args.version, pathlib.Path(args.file))
    if args.out:
        pathlib.Path(args.out).write_text(notes + ("\n" if notes else ""), encoding="utf-8")
    else:
        sys.stdout.write(notes)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
