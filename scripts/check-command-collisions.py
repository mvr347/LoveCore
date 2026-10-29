#!/usr/bin/env python3
"""Report command names/aliases declared by more than one Love* plugin.yml.

Usage: check-command-collisions.py ROOT   (ROOT contains the plugin checkouts)
Exit status 1 if a collision is found.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path


def parse(path):
    text = path.read_text(encoding="utf8")
    plugin = (re.search(r"^name:\s*(\S+)", text, re.M) or [None, path.parent.name])[1].strip("'\"")
    block = re.search(r"^commands:\s*\n((?:[ \t].*\n?|\n)*)", text, re.M)
    names = {}
    if not block:
        return plugin, names
    current = None
    for line in block.group(1).splitlines():
        m = re.match(r"^  ([^\s#:][^:]*):\s*$", line)
        if m:
            current = m.group(1).strip("'\"").lower()
            names[current] = []
            continue
        m = re.match(r"^    aliases:\s*(.*)$", line)
        if m and current:
            raw = m.group(1).strip().strip("[]")
            names[current] += [a.strip().strip("'\"").lower() for a in raw.split(",") if a.strip()]
    return plugin, names


def main(root):
    seen = defaultdict(set)
    for yml in Path(root).rglob("src/main/resources/plugin.yml"):
        if "/target/" in str(yml) or "/mvr347/" in str(yml):
            continue
        plugin, commands = parse(yml)
        for name, aliases in commands.items():
            for label in [name, *aliases]:
                seen[label].add(plugin)
    bad = {k: sorted(v) for k, v in seen.items() if len(v) > 1}
    for label, plugins in sorted(bad.items()):
        print(f"/{label}: {', '.join(plugins)}")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "."))
