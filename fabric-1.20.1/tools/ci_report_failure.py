#!/usr/bin/env python3
"""Reports why a Gradle build failed as a GitHub Actions error annotation.

Usage (CI only):  python3 tools/ci_report_failure.py build.log

Annotations show up on the run's summary page and on the pull request, so the
cause of a failed build can be read without opening the full log. This prints
Gradle's "What went wrong" section, which for a compile failure contains the
compiler output. Per-line compiler annotations are not produced here:
actions/setup-java already adds those.
"""
import sys

MAX_MESSAGE_CHARS = 6000


def escape(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main():
    with open(sys.argv[1], encoding="utf-8", errors="replace") as handle:
        lines = handle.read().splitlines()
    starts = [i for i, line in enumerate(lines) if line.startswith("* What went wrong:")]
    if starts:
        start = starts[-1]
        end = next((i for i in range(start + 1, len(lines)) if lines[i].startswith("* Try:")), len(lines))
        message = "\n".join(lines[start + 1:end]).strip()[:MAX_MESSAGE_CHARS]
    else:
        # No Gradle failure section (e.g. the wrapper itself failed): show the end of the log.
        message = "\n".join(lines[-40:])[-MAX_MESSAGE_CHARS:]
    print("::error title=Gradle build failed::%s" % escape(message))


if __name__ == "__main__":
    main()
