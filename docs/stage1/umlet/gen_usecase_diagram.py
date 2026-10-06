#!/usr/bin/env python3
"""Generates the TraceWise UMLet use-case diagram (usecase-diagram.uxf).

Layout: one column of use cases inside the system boundary, with include/extend arrows between
neighbours; primary actors on the left, secondary actors on the right.
"""
import pathlib
from xml.sax.saxutils import escape

HERE = pathlib.Path(__file__).parent
UC = {
    "UC03": "UC03 Configure\nMonitoring Rules", "UC17": "UC17 View Agent\nPerformance",
    "UC11": "UC11 Review Audit Trail", "UC12": "UC12 Explore\nMoney-Flow Network",
    "UC01": "UC01 Import Transactions", "UC02": "UC02 Run Monitoring\nand Review Alerts",
    "UC08": "UC08 Review Agent Proposals",
    "UC09": "UC09 Manage Case\n--\nextension points:\ncase closed", "UC10": "UC10 Reopen Case",
    "UC16": "UC16 Search\nKnowledge Base", "UC05": "UC05 Retrieve Indicators\nand Similar Cases",
    "UC07": "UC07 Triage Alert Queue", "UC04": "UC04 Investigate Alert",
    "UC06": "UC06 Verify Agent Claims", "UC13": "UC13 Ask the Assistant",
    "UC14": "UC14 Draft and Finalise\nSuspicious Transaction Report\n--\nextension points:\nreport finalised",
    "UC15": "UC15 Export Report",
}
ORDER = ["UC03", "UC17", "UC11", "UC12", "UC01", "UC02", "UC08", "UC09", "UC10", "UC16", "UC05",
         "UC07", "UC04", "UC06", "UC13", "UC14", "UC15"]
COL_X, UC_W, GAP, TOP = 420, 260, 26, 110
pos = {}
y = TOP
for u in ORDER:
    h = 100 if "extension points" in UC[u] else (60 if "\n" in UC[u] else 46)
    pos[u] = (COL_X, y, UC_W, h)
    y += h + GAP
BOTTOM = y

ACTORS = {  # name -> (label, x, y)
    "Supervisor": ("Compliance\nSupervisor", 90, 150),
    "Analyst": ("AML Analyst", 90, 760),
    "Auto": ("Automation\nClient", 1010, 800),
    "LLM": ("LLM Service\n(Anthropic\nClaude API)", 1010, 1180),
}
ACT_W, ACT_H = 80, 120
ASSOC = [("Supervisor", "UC03"), ("Supervisor", "UC17")] + \
        [("Analyst", u) for u in ["UC11", "UC12", "UC01", "UC02", "UC08", "UC09", "UC16", "UC07", "UC04", "UC13", "UC14"]] + \
        [("Auto", u) for u in ["UC07", "UC04", "UC13"]] + [("LLM", u) for u in ["UC04", "UC13", "UC14"]]
INCLUDE = [("UC16", "UC05"), ("UC07", "UC04"), ("UC04", "UC06"), ("UC13", "UC06"), ("UC04", "UC05")]
EXTEND = [("UC10", "UC09"), ("UC15", "UC14")]

els = []
def el(kind, x, y, w, h, text):
    els.append(f"<element><id>{kind}</id><coordinates><x>{int(x)}</x><y>{int(y)}</y><w>{int(w)}</w><h>{int(h)}</h></coordinates>"
               f"<panel_attributes>{escape(text)}</panel_attributes><additional_attributes/></element>")
def rel(pts, attrs):
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    ox, oy = int(min(xs)) - 10, int(min(ys)) - 10
    w, h = int(max(xs) - ox) + 20, int(max(ys) - oy) + 20
    rp = ";".join(f"{px - ox:.1f};{py - oy:.1f}" for px, py in zip(xs, ys))
    els.append(f"<element><id>Relation</id><coordinates><x>{ox}</x><y>{oy}</y><w>{w}</w><h>{h}</h></coordinates>"
               f"<panel_attributes>{escape(attrs)}</panel_attributes><additional_attributes>{rp}</additional_attributes></element>")

el("Text", 20, 10, 900, 40, "*TraceWise: Use-Case Diagram*\nfontsize=18")
el("UMLGeneric", COL_X - 80, TOP - 50, UC_W + 160, BOTTOM - TOP + 70, "TraceWise\nhalign=center\nvalign=top\nlayer=-1")
for u in ORDER:
    x, yy, w, h = pos[u]
    el("UMLUseCase", x, yy, w, h, UC[u] + ("\nvalign=top" if "extension points" in UC[u] else ""))
for a, (label, x, yy) in ACTORS.items():
    el("UMLActor", x, yy, ACT_W, ACT_H, label)

def left_mid(u):  x, yy, w, h = pos[u]; return (x, yy + h / 2)
def right_mid(u): x, yy, w, h = pos[u]; return (x + w, yy + h / 2)
def top_mid(u):   x, yy, w, h = pos[u]; return (x + w / 2, yy)
def bottom_mid(u): x, yy, w, h = pos[u]; return (x + w / 2, yy + h)
def actor_anchor(a, side):
    _, x, yy = ACTORS[a]
    return (x + ACT_W if side == "right" else x, yy + 40)

for a, u in ASSOC:
    if a in ("Auto", "LLM"):
        rel([right_mid(u), actor_anchor(a, "left")], "lt=-")
    else:
        rel([actor_anchor(a, "right"), left_mid(u)], "lt=-")
# Supervisor is a specialised Analyst: generalization arrow points at the Analyst (parent)
_, sx, sy = ACTORS["Supervisor"]; _, ax, ay = ACTORS["Analyst"]
rel([(ax + ACT_W / 2, ay), (sx + ACT_W / 2, sy + ACT_H)], "lt=<<-")

def vertical(a, b, label):
    """Arrow from use case a to use case b, attached to the facing ellipse edges."""
    ia, ib = ORDER.index(a), ORDER.index(b)
    if abs(ia - ib) == 1:
        start = bottom_mid(a) if ia < ib else top_mid(a)
        end = top_mid(b) if ia < ib else bottom_mid(b)
        rel([start, end], f"lt=.>\n{label}")
    else:  # route around the right side of the column
        s, e = right_mid(a), right_mid(b)
        rel([s, (COL_X + UC_W + 40, s[1]), (COL_X + UC_W + 40, e[1]), e], f"lt=.>\n{label}")

for a, b in INCLUDE:
    vertical(a, b, "<<include>>")
for a, b in EXTEND:
    vertical(a, b, "<<extend>>")

xml = ['<?xml version="1.0" encoding="UTF-8" standalone="no"?>', '<diagram program="umlet" version="15.1">',
       "<zoom_level>10</zoom_level>"] + els + ["</diagram>"]
(HERE / "usecase-diagram.uxf").write_text("\n".join(xml))
print("use cases:", len(ORDER), "associations:", len(ASSOC))
