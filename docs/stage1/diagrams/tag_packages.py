#!/usr/bin/env python3
"""Tags every package in src/model.iuml with the union of the tags of the classes inside it.

PlantUML's `remove * / restore $tag` hides a package unless the package itself carries the tag,
which would hide its classes too. Run this after editing model.iuml, before rendering.
"""
import pathlib
import re

path = pathlib.Path(__file__).parent / "src" / "model.iuml"
lines = path.read_text().splitlines()
out, i = [], 0
pkg_re = re.compile(r'^(package\s+"[^"]+")(\s+\$\w+)*\s*\{\s*$')
while i < len(lines):
    m = pkg_re.match(lines[i])
    if not m:
        out.append(lines[i]); i += 1; continue
    depth, j, tags = 1, i + 1, []
    while depth:
        line = lines[j]
        depth += line.count("{") - line.count("}")
        for t in re.findall(r"\$\w+", line):
            if t not in tags:
                tags.append(t)
        j += 1
    out.append(f'{m.group(1)} {" ".join(tags)} {{'.replace("  ", " "))
    out.extend(lines[i + 1:j])
    i = j
path.write_text("\n".join(out) + "\n")
print("tagged packages")
