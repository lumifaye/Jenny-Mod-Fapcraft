#!/usr/bin/env python3
"""Turns a failed Gradle build log into GitHub Actions error annotations.

Usage (CI only):  python3 tools/ci_report_failure.py build.log

Annotations show up on the run's summary page and on the pull request, so the
cause of a failed build can be read without opening the full log. Compiler
errors are attached to their file and line. Gradle's own "What went wrong"
section is reported as one more annotation.
"""
import os
import re
import sys

MAX_COMPILER_ERRORS = 8
MAX_MESSAGE_CHARS = 3500
# Paths in the log are absolute; annotations want them relative to the repository.
WORKSPACE = os.environ.get("GITHUB_WORKSPACE", "")


def escape(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text):
    return escape(text).replace(":", "%3A").replace(",", "%2C")


def main():
    with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
        lines = handle.read().splitlines()

    reported = 0
    for index, line in enumerate(lines):
        match = re.match(r"^(/.+\.java):(\d+): error: (.*)$", line)
        if not match:
            continue
        if reported == MAX_COMPILER_ERRORS:
            break
        path, number, message = match.groups()
        # javac prints the source line and a caret under the error.
        context = "\n".join(lines[index + 1:index + 3])
        if WORKSPACE and path.startswith(WORKSPACE):
            path = os.path.relpath(path, WORKSPACE)
        print("::error file=%s,line=%s,title=Compiler error::%s" % (escape_property(path), number, escape(message + "\n" + context)))
        reported += 1

    total = sum(1 for line in lines if re.match(r"^/.+\.java:\d+: error: ", line))
    if total > reported:
        print("::error title=More compiler errors::%d compiler errors in total; only the first %d are shown." % (total, reported))

    try:
        start = max(i for i, line in enumerate(lines) if line.startswith("* What went wrong:"))
    except ValueError:
        tail = "\n".join(lines[-40:])
        print("::error title=Gradle build failed::%s" % escape(tail[-MAX_MESSAGE_CHARS:]))
        return
    end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("* Try:")), len(lines))
    summary = "\n".join(lines[start + 1:end]).strip()
    print("::error title=Gradle build failed::%s" % escape(summary[:MAX_MESSAGE_CHARS]))


if __name__ == "__main__":
    main()
