#!/usr/bin/env python3
"""Checks the Stage 2 sequence diagrams against the Stage 2 class diagram: every participant is a class on the
class diagram (or an actor), and every call is a method of the receiving class there or of a class it extends or
implements. Run after editing either generator; it prints each problem and exits 1 if there is any."""
import re
import sys

sys.dont_write_bytecode = True
import gen_class_diagram as cd  # noqa: E402
import gen_sequence_diagrams as sd  # noqa: E402

parents = {}
for kind, a, b, *_ in cd.R:
    if kind in ("gen", "real"):
        parents.setdefault(a, set()).add(b)


def methods(cls, seen=()):
    names = {re.match(r"[/_]*[+\-#~]?(\w+)\(", m).group(1) for m in cd.C[cls][3] if "(" in m}
    for p in parents.get(cls, ()):
        if p not in seen:
            names |= methods(p, seen + (cls,))
    return names


problems = []
for name, text in sd.SD.items():
    kinds = {}
    for m in re.finditer(r"^obj=([^~]*)~(\w+)(.*)$", text, re.M):
        label, oid, rest = m.groups()
        if "ACTOR" in rest:
            continue
        cls = label.split(":")[-1].strip()
        if cls not in cd.C:
            problems.append(f"{name}: participant {label} is not on the class diagram")
        kinds[oid] = cls
    for line in text.splitlines():
        m = re.match(r"^(\w+)(?:->>>|->)(\w+) \+? ?: ([A-Za-z]\w*)\(", line)
        if not m:
            continue
        src, dst, method = m.groups()
        cls = kinds.get(dst)
        if cls in cd.C and method not in methods(cls):
            problems.append(f"{name}: {cls} has no {method}() on the class diagram ({line.strip()})")
print("\n".join(problems) or f"{len(sd.SD)} sequence diagrams match the class diagram")
sys.exit(1 if problems else 0)
