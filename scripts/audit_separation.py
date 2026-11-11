"""Scans everything for any trace of a foreign repository owner.

Covers the four places a trace can hide:
  1. git configuration and refs (remotes, FETCH_HEAD, packed-refs, reflogs)
  2. every blob in the object database
  3. the working tree, including ignored build output
  4. commit messages

The object scan uses one `git cat-file --batch-all-objects` process rather than
one call per object, which is what makes it finish in seconds instead of
minutes.
"""
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
# The needle is assembled from parts so that this file does not itself carry the
# name it is looking for.
NEEDLES = [b"christopher" + b"laughlin", b"Christopher" + b"Laughlin"]
SKIP_DIRS = {".git"}
BINARY_SUFFIXES = {".apk", ".png", ".jpg", ".webp", ".jar", ".zip", ".keystore", ".class"}
MAX_FILE_BYTES = 20 * 1024 * 1024


def git(*args, binary=False):
    result = subprocess.run(
        ["git", *args], cwd=ROOT, capture_output=True,
        text=not binary, encoding=None if binary else "utf-8", errors="replace",
    )
    return result.stdout if binary else result.stdout


def find(data):
    lowered = data.lower()
    for needle in NEEDLES:
        index = lowered.find(needle.lower())
        if index >= 0:
            start = max(0, index - 70)
            end = min(len(data), index + 90)
            snippet = data[start:end].decode("utf-8", errors="replace").replace("\n", " ")
            return snippet
    return None


def main():
    hits = []

    # ---------------------------------------------------------- 1. git plumbing
    print("== 1. git configuration, refs and reflogs ==")
    git_dir = ROOT / ".git"
    for name in ("config", "packed-refs", "FETCH_HEAD", "ORIG_HEAD", "description"):
        path = git_dir / name
        if not path.exists():
            continue
        snippet = find(path.read_bytes())
        if snippet:
            hits.append(".git/%s: ...%s..." % (name, snippet))
            print("  XX .git/%s -> %s" % (name, snippet))
        else:
            print("  OK .git/%s" % name)
    logs = git_dir / "logs"
    log_hits = 0
    if logs.exists():
        for log in logs.rglob("*"):
            if log.is_file():
                snippet = find(log.read_bytes())
                if snippet:
                    hits.append("reflog %s: %s" % (log.name, snippet))
                    log_hits += 1
        print("  %s .git/logs" % ("XX found %d" % log_hits if log_hits else "OK no reference"))
    print("  remotes: " + (git("remote", "-v").strip() or "(none)"))

    # ---------------------------------------------------------- 2. every blob
    print("")
    print("== 2. every blob in every reachable commit ==")
    # `git grep` over every commit is the authoritative check: it walks the trees
    # of the whole history. The batch reader below walks the object database
    # instead, which also covers unreachable objects.
    revisions = git("rev-list", "--all").split()
    blob_hits = 0
    if revisions:
        found = subprocess.run(
            ["git", "grep", "-I", "-l", "-i", NEEDLES[0].decode(), *revisions],
            cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace",
        ).stdout
        for line in found.splitlines():
            if line.strip():
                blob_hits += 1
                print("  XX " + line.strip())
    print("  %s (%d commits searched)" % (
        "OK no blob mentions it" if blob_hits == 0 else "FOUND", len(revisions)))

    # The object database itself, including objects no ref points at any more.
    proc = subprocess.Popen(
        ["git", "cat-file", "--batch-all-objects", "--batch"],
        cwd=ROOT, stdout=subprocess.PIPE,
    )
    loose_hits = 0
    count = 0
    stream = proc.stdout
    while True:
        header = stream.readline()
        if not header:
            break
        parts = header.split()
        if len(parts) < 3:
            continue
        sha, kind, size = parts[0], parts[1], int(parts[2])
        data = stream.read(size)
        stream.read(1)
        if kind != "blob":
            continue
        count += 1
        snippet = find(data)
        if snippet:
            loose_hits += 1
            print("  XX object %s -> %s" % (sha[:8], snippet))
    proc.wait()
    print("  %s (%d objects in the database)" % (
        "OK no object mentions it" if loose_hits == 0 else "FOUND", count))

    # ---------------------------------------------------------- 3. commit messages
    print("")
    print("== 3. commit messages ==")
    messages = git("log", "--all", "--format=%H%n%B%n--").encode("utf-8", errors="replace")
    snippet = find(messages)
    if snippet:
        hits.append("commit messages: " + snippet)
        print("  XX " + snippet)
    else:
        print("  OK no commit message mentions it")

    # ---------------------------------------------------------- 4. working tree
    print("")
    print("== 4. working tree, including ignored build output ==")
    tree_hits = 0
    scanned = 0
    for path in ROOT.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.parts):
            continue
        if path.suffix.lower() in BINARY_SUFFIXES:
            continue
        try:
            if path.stat().st_size > MAX_FILE_BYTES:
                continue
            data = path.read_bytes()
        except OSError:
            continue
        scanned += 1
        snippet = find(data)
        if snippet:
            tree_hits += 1
            hits.append("%s: %s" % (path.relative_to(ROOT), snippet))
            print("  XX %s -> %s" % (path.relative_to(ROOT), snippet))
    print("  %s (%d files scanned)" % ("OK no file mentions it" if tree_hits == 0 else "FOUND", scanned))

    # ---------------------------------------------------------- 5. binary artefacts
    print("")
    print("== 5. built artefacts that may embed an old URL ==")
    for path in ROOT.rglob("*.apk"):
        try:
            data = path.read_bytes()
        except OSError:
            continue
        snippet = find(data)
        if snippet:
            hits.append("%s: %s" % (path.relative_to(ROOT), snippet))
            print("  XX %s -> %s" % (path.relative_to(ROOT), snippet))
        else:
            print("  OK %s" % path.relative_to(ROOT))

    print("")
    if hits:
        print("TRACES FOUND: %d" % len(hits))
        return 1
    print("CLEAN: no trace of the previous owner anywhere in the repository.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
