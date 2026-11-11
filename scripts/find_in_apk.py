"""Searches an APK's dex files for a literal string.

The dex entries inside an APK are deflated, so the text is not visible in the
raw file - it has to be decompressed first.
"""
import pathlib
import sys
import zipfile


def main():
    apk = pathlib.Path(sys.argv[1])
    needle = sys.argv[2].encode("utf-8")
    with zipfile.ZipFile(apk) as archive:
        dexes = [n for n in archive.namelist() if n.startswith("classes") and n.endswith(".dex")]
        print("dex entries: %s" % dexes)
        for name in dexes:
            data = archive.read(name)
            index = data.find(needle)
            print("  %-18s %8d bytes  found=%s" % (name, len(data), index))
        # Also check the whole archive for any dex-like payload.
        for name in archive.namelist():
            if name.endswith(".dex"):
                continue
            data = archive.read(name)
            if needle in data:
                print("  also found in %s" % name)


if __name__ == "__main__":
    main()
