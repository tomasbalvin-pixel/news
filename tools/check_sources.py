#!/usr/bin/env python3
"""Fast checks for mistakes that otherwise surface as a failed build.

Both of these have cost a full CI cycle: a scripted edit re-applying an import,
and an apostrophe in a string resource, which aapt reports from deep inside
resource merging as a corrupt file.
"""

import glob
import re
import sys

BACKSLASH = chr(92)


def duplicate_imports():
    problems = []
    for path in glob.glob("app/src/**/*.kt", recursive=True):
        seen, reported = set(), set()
        for number, line in enumerate(open(path, encoding="utf-8"), 1):
            if not line.startswith("import "):
                continue
            statement = line.rstrip()
            if statement in seen and statement not in reported:
                problems.append(f"{path}:{number}: duplicate {statement}")
                reported.add(statement)
            seen.add(statement)
    return problems


def unescaped_apostrophes():
    problems = []
    for path in glob.glob("app/src/main/res/values*/strings.xml"):
        for number, line in enumerate(open(path, encoding="utf-8"), 1):
            body = line.replace(BACKSLASH + "'", "")
            for text in re.findall(r"<string[^>]*>(.*?)</string>", body):
                if "'" in text:
                    problems.append(
                        f"{path}:{number}: unescaped apostrophe: {line.strip()}"
                    )
    return problems


def main():
    problems = duplicate_imports() + unescaped_apostrophes()
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
