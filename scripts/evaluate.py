"""Evaluates the agent on a fixed list of alerts, each investigated on its own fresh copy of a monitored database,
so no run sees another run's escalations or links. The findings and finished-run audit entries are merged into one
database and scored with F12 (`tracewise metrics`).

Usage: python3 -I scripts/evaluate.py <monitored.db> <alerts.txt> <out-dir> <model> [parallel]
Run from the repository root (the CLI reads .env there); build first with ./mvnw -q package -DskipTests,
or set TRACEWISE_JAR to a saved build. Running again into the same out-dir repeats only the alerts whose
run left no finding (a failed model call, for example).
"""
import hashlib
import json
import os
import shutil
import sqlite3
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

base, alerts_file, out, model = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]), sys.argv[4]
parallel = int(sys.argv[5]) if len(sys.argv) > 5 else 4
alerts = [a.strip() for a in alerts_file.read_text().split() if a.strip()]
out.mkdir(parents=True, exist_ok=True)
# TRACEWISE_JAR lets a saved build keep running while the tree is rebuilt
jar = Path(os.environ.get("TRACEWISE_JAR", "target/tracewise.jar")).resolve()

# a resumed run must come from the same build (the prompt is compiled into it) and model, or versions would mix
run = {"model": model, "jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest()}
run_file = out / "run.json"
if run_file.exists() and json.loads(run_file.read_text()) != run:
    sys.exit(f"{out} holds a run of another build or model ({run_file}); use a new out-dir")
run_file.write_text(json.dumps(run))


def has_finding(alert):
    db = out / f"{alert}.db"
    if not db.exists():
        return False
    with sqlite3.connect(db) as s:
        return s.execute("SELECT COUNT(*) FROM findings").fetchone()[0] > 0


def investigate(alert):
    db = out / f"{alert}.db"
    if has_finding(alert):  # resume: a re-run only repeats alerts whose run left no finding
        return alert, 0
    shutil.copy(base, db)
    log = subprocess.run(["java", "-jar", str(jar), "--db", str(db), "--model", model, "investigate", alert],
                         capture_output=True, text=True)
    (out / f"{alert}.log").write_text(log.stdout + log.stderr)
    return alert, log.returncode


with ThreadPoolExecutor(parallel) as pool:
    for alert, code in pool.map(investigate, alerts):
        print(alert, f"exit {code}" if code != 0 else "ok" if has_finding(alert) else "no finding", flush=True)

merged = out / "merged.db"
shutil.copy(base, merged)
m = sqlite3.connect(merged)
runs = findings = 0
for alert in alerts:
    s = sqlite3.connect(out / f"{alert}.db")
    for row in s.execute("SELECT id, opened_at, status FROM cases"):
        m.execute("INSERT INTO cases VALUES (?, ?, ?)", row)
    m.executemany("INSERT INTO case_alerts VALUES (?, ?)", s.execute("SELECT case_id, alert_id FROM case_alerts"))
    for (case_id, body) in s.execute("SELECT case_id, body FROM findings ORDER BY seq"):
        m.execute("INSERT INTO findings (case_id, body) VALUES (?, ?)", (case_id, body))
        findings += 1
    for row in s.execute("SELECT ts, actor_name, actor_type, event_type, target, details, outcome FROM audit_log "
                         "WHERE event_type = 'INVESTIGATION_FINISHED'"):
        m.execute("INSERT INTO audit_log (ts, actor_name, actor_type, event_type, target, details, outcome) "
                  "VALUES (?, ?, ?, ?, ?, ?, ?)", row)
        runs += 1
    s.close()
m.commit()
# self-check: one finished run per alert (a resumed alert keeps only its last run), no more findings than runs
assert runs == len(alerts), f"{runs} finished runs for {len(alerts)} alerts"
assert findings <= runs
print(f"merged {findings} findings from {runs} runs into {merged}")
