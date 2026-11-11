"""Validates the Python blocks embedded in .github/workflows/release-apk.yml.

GitHub Actions will not tell us a heredoc is broken until a release runs, and a
release run is exactly the moment we least want a surprise. This extracts every
`python3 - <<'PY' ... PY` block, de-indents it the way YAML does, and compiles
it.
"""
import pathlib
import re
import sys

WORKFLOW = pathlib.Path(".github/workflows/release-apk.yml")


def main():
    text = WORKFLOW.read_text(encoding="utf-8")
    blocks = re.findall(r"python3 - <<'PY'\n(.*?)\n\s*PY\n", text, re.DOTALL)
    if not blocks:
        print("No heredoc blocks found — did the workflow change?")
        return 1
    failures = 0
    for index, block in enumerate(blocks, start=1):
        lines = block.split("\n")
        indent = min(
            (len(line) - len(line.lstrip()) for line in lines if line.strip()),
            default=0,
        )
        code = "\n".join(line[indent:] if line.strip() else "" for line in lines)
        try:
            compile(code, "workflow-block-%d" % index, "exec")
            print("block %d: OK (%d lines)" % (index, len(lines)))
        except SyntaxError as error:
            failures += 1
            print("block %d: SYNTAX ERROR %s" % (index, error))
            for number, line in enumerate(code.split("\n"), start=1):
                print("   %4d | %s" % (number, line))
    return failures


if __name__ == "__main__":
    raise SystemExit(main())
