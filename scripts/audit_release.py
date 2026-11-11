"""Pre-release audit for the Sawn repository.

Checks the things that are cheap to verify and expensive to get wrong:
  1. no secret file is tracked by git
  2. the release keystore and local.properties are git-ignored
  3. no password or token VALUE is committed in a tracked source file
  4. every default string has an Arabic translation, and vice versa
  5. every Android XML resource is well-formed
  6. every relative link in docs/ resolves to a real file
  7. no leftover debug output in release code
  8. the version ladder in build.gradle.kts is sane
"""
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ElementTree

ROOT = pathlib.Path(__file__).resolve().parent.parent
failures = []
notes = []

TEXT_SUFFIXES = (".kt", ".kts", ".xml", ".yml", ".yaml", ".md", ".properties", ".ps1", ".py")


def git(*args):
    result = subprocess.run(
        ["git", *args], cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace"
    )
    return result.returncode, result.stdout, result.stderr


def fail(section, message):
    failures.append("%s: %s" % (section, message))


def is_public_or_reference(value):
    """True when a literal is not a secret: a URL, a path, or a name reference."""
    if not value:
        return True
    lowered = value.lower()
    if lowered.startswith(("http://", "https://")):
        return True
    if value.startswith("$"):
        return True
    if "(" in value:
        # A lookup such as signingValue("SAWN_STORE_PASSWORD") or
        # providers.gradleProperty("SAWN_MINIFY_RELEASE") reads a value elsewhere.
        return True
    if value.endswith((".keystore", ".jks", ".p12", ".properties", "/", "\\")):
        return True
    if re.fullmatch(r"[A-Z][A-Z0-9_]{3,}", value):
        # The name of an environment variable, not its value.
        return True
    if value.startswith("$") or value.startswith("{") or value.startswith("<"):
        return True
    return False


# ---------------------------------------------------------------- 1 & 2
code, out, _ = git("ls-files")
tracked = out.splitlines()
for path in tracked:
    lowered = path.lower()
    if lowered.endswith((".keystore", ".jks", ".p12", ".p8")) or lowered.endswith(".env"):
        fail("secrets", "tracked file looks like a secret: %s" % path)
    if lowered == "local.properties":
        fail("secrets", "local.properties is tracked by git")

code, out, _ = git("check-ignore", "-v", "sawn-release.keystore", "local.properties")
if code != 0:
    fail("secrets", "sawn-release.keystore or local.properties is not git-ignored")
else:
    notes.append("git-ignore: keystore + local.properties are ignored")

# ---------------------------------------------------------------- 3
# A leak is a hard-coded VALUE. In Kotlin the value must be a string literal;
# in a properties/env/yaml file a bare token is a value too.
ASSIGNMENT = re.compile(
    r"""(?ix)
    (?:^|[\s"'(\[{,])
    (?P<key>[A-Za-z_][A-Za-z0-9_.-]{2,})
    \s*[:=]\s*
    (?P<value>"[^"\n]{4,}" | '[^'\n]{4,}' | [A-Za-z0-9_./+\-]{8,})
    """
)
SECRET_KEY = re.compile(
    r"(?i)(password|passwd|secret|api[_-]?key|apikey|token|credential|keystore)"
)
BARE_VALUE_FILES = (".properties", ".env", ".yml", ".yaml", ".md")
for path in tracked:
    if not path.lower().endswith(TEXT_SUFFIXES):
        continue
    full = ROOT / path
    try:
        content = full.read_text(encoding="utf-8", errors="replace")
    except OSError:
        continue
    bare_counts = path.lower().endswith(BARE_VALUE_FILES)
    for number, line in enumerate(content.splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith(("#", "//", "*", "<!--")):
            continue
        for match in ASSIGNMENT.finditer(line):
            key = match.group("key")
            raw = match.group("value")
            quoted = raw[0] in "\"'"
            value = raw.strip("\"'")
            if not SECRET_KEY.search(key):
                continue
            if not quoted and not bare_counts:
                # A bare word in source code is a variable or a method call
                # (storePassword = sawnStorePassword), never a literal secret.
                continue
            if is_public_or_reference(value):
                continue
            fail(
                "secrets",
                "%s:%d assigns a literal to %r: %s" % (path, number, key, value[:24]),
            )

# ---------------------------------------------------------------- 4
def string_keys(path):
    tree = ElementTree.parse(path)
    return {
        element.get("name")
        for element in tree.getroot().findall("string")
        if element.get("name")
    }


default_strings = string_keys(ROOT / "app/src/main/res/values/strings.xml")
arabic_strings = string_keys(ROOT / "app/src/main/res/values-ar/strings.xml")
missing_ar = sorted(default_strings - arabic_strings)
extra_ar = sorted(arabic_strings - default_strings)
if missing_ar:
    fail("i18n", "%d default strings have no Arabic: %s" % (len(missing_ar), ", ".join(missing_ar[:8])))
if extra_ar:
    fail("i18n", "%d Arabic strings have no default: %s" % (len(extra_ar), ", ".join(extra_ar[:8])))
notes.append("i18n: %d default strings / %d Arabic — parity %s" % (
    len(default_strings), len(arabic_strings),
    "OK" if not missing_ar and not extra_ar else "BROKEN",
))

# ---------------------------------------------------------------- 5
xml_count = 0
for pattern in ("app/src/main/res/**/*.xml", "app/src/main/AndroidManifest.xml"):
    for xml_path in ROOT.glob(pattern):
        xml_count += 1
        try:
            ElementTree.parse(xml_path)
        except ElementTree.ParseError as error:
            fail("xml", "%s is malformed: %s" % (xml_path.relative_to(ROOT), error))
notes.append("xml: %d resource files parsed" % xml_count)

# ---------------------------------------------------------------- 6
LINK = re.compile(r"\[[^\]]*\]\((?!https?://|#|mailto:)([^)#]+)")
link_count = 0
for doc in list((ROOT / "docs").glob("*.md")) + [ROOT / "README.md"]:
    for target in LINK.findall(doc.read_text(encoding="utf-8", errors="replace")):
        link_count += 1
        resolved = (doc.parent / target).resolve()
        if not resolved.exists():
            fail("docs", "%s links to a missing file: %s" % (doc.name, target))
notes.append("docs: %d relative links resolved" % link_count)

# ---------------------------------------------------------------- 7
DEBUG_PATTERN = re.compile(r"\b(println|printStackTrace|System\.out\.print)\b")
debug_hits = []
for path in (ROOT / "app/src/main").rglob("*.kt"):
    content = path.read_text(encoding="utf-8", errors="replace")
    for number, line in enumerate(content.splitlines(), start=1):
        stripped = line.strip()
        if stripped.startswith(("//", "*", "/*")):
            continue
        if DEBUG_PATTERN.search(stripped):
            debug_hits.append("%s:%d" % (path.relative_to(ROOT), number))
if debug_hits:
    fail("debug", "debug output left in release code: %s" % ", ".join(debug_hits[:8]))
notes.append("debug: no stray print statements in app/src/main")

# ---------------------------------------------------------------- 8
gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
version_code = re.search(r"versionCode\s*=\s*(\d+)", gradle)
version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle)
if not version_code or not version_name:
    fail("version", "could not read versionCode/versionName from app/build.gradle.kts")
else:
    notes.append("version: versionName=%s versionCode=%s" % (version_name.group(1), version_code.group(1)))
    changelog = (ROOT / "docs/CHANGELOG.md").read_text(encoding="utf-8")
    head = changelog.split("---", 1)[0]
    if "versionCode %s" % version_code.group(1) not in head:
        fail("version", "CHANGELOG.md does not state versionCode %s for %s"
             % (version_code.group(1), version_name.group(1)))
    readme = (ROOT / "README.md").read_text(encoding="utf-8")
    if "versionCode %s" % version_code.group(1) not in readme:
        fail("version", "README.md does not state versionCode %s" % version_code.group(1))

# ---------------------------------------------------------------- report
print("== Sawn pre-release audit ==")
for note in notes:
    print("  .. %s" % note)
print("")
if failures:
    for failure in failures:
        print("  XX %s" % failure)
    print("")
    print("%d problem(s) found." % len(failures))
    sys.exit(1)
print("No blocking problems found.")
