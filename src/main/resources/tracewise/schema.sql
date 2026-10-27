CREATE TABLE IF NOT EXISTS transactions (
    id TEXT PRIMARY KEY,
    ts INTEGER NOT NULL,
    sender TEXT NOT NULL,
    receiver TEXT NOT NULL,
    amount_cad TEXT NOT NULL,
    original_amount TEXT NOT NULL,
    currency TEXT NOT NULL,
    channel TEXT NOT NULL,
    counterparty_country TEXT,
    laundering_label INTEGER
);
CREATE INDEX IF NOT EXISTS transactions_sender ON transactions (sender, ts);
CREATE INDEX IF NOT EXISTS transactions_receiver ON transactions (receiver, ts);
CREATE TABLE IF NOT EXISTS accounts (
    id TEXT PRIMARY KEY,
    watchlisted INTEGER NOT NULL DEFAULT 0
);
CREATE TABLE IF NOT EXISTS audit_log (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    ts INTEGER NOT NULL,
    actor_name TEXT NOT NULL,
    actor_type TEXT NOT NULL,
    event_type TEXT NOT NULL,
    target TEXT,
    details TEXT,
    outcome TEXT
);
CREATE TRIGGER IF NOT EXISTS audit_log_no_update BEFORE UPDATE ON audit_log
BEGIN
    SELECT RAISE(ABORT, 'audit log is append-only');
END;
CREATE TRIGGER IF NOT EXISTS audit_log_no_delete BEFORE DELETE ON audit_log
BEGIN
    SELECT RAISE(ABORT, 'audit log is append-only');
END;
CREATE TABLE IF NOT EXISTS alerts (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    rule_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    total_cad TEXT NOT NULL,
    risk_score INTEGER NOT NULL,
    priority INTEGER NOT NULL,
    status TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS alert_transactions (
    alert_seq INTEGER NOT NULL REFERENCES alerts (seq),
    transaction_id TEXT NOT NULL REFERENCES transactions (id),
    PRIMARY KEY (alert_seq, transaction_id)
);
CREATE INDEX IF NOT EXISTS alert_transactions_tx ON alert_transactions (transaction_id);
CREATE TABLE IF NOT EXISTS threshold_reports (
    id TEXT PRIMARY KEY,
    rule_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    total_cad TEXT NOT NULL,
    window_start INTEGER NOT NULL,
    window_end INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS threshold_report_transactions (
    report_id TEXT NOT NULL REFERENCES threshold_reports (id),
    transaction_id TEXT NOT NULL REFERENCES transactions (id),
    PRIMARY KEY (report_id, transaction_id)
);
CREATE TABLE IF NOT EXISTS rule_configs (
    rule_id TEXT PRIMARY KEY,
    enabled INTEGER NOT NULL,
    parameters TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS cases (
    id TEXT PRIMARY KEY,
    opened_at INTEGER NOT NULL,
    status TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS case_alerts (
    case_id TEXT NOT NULL REFERENCES cases (id),
    alert_id TEXT NOT NULL,
    PRIMARY KEY (case_id, alert_id)
);
CREATE TABLE IF NOT EXISTS findings (
    seq INTEGER PRIMARY KEY AUTOINCREMENT,
    case_id TEXT NOT NULL REFERENCES cases (id),
    body TEXT NOT NULL
);
CREATE TABLE IF NOT EXISTS case_tasks (
    case_id TEXT NOT NULL REFERENCES cases (id),
    position INTEGER NOT NULL,
    description TEXT NOT NULL,
    done INTEGER NOT NULL,
    PRIMARY KEY (case_id, position)
);

CREATE TABLE IF NOT EXISTS case_notes (
    case_id TEXT NOT NULL REFERENCES cases (id),
    position INTEGER NOT NULL,
    text TEXT NOT NULL,
    author TEXT NOT NULL,
    created_at TEXT NOT NULL,
    PRIMARY KEY (case_id, position)
);
CREATE TABLE IF NOT EXISTS approvals (
    id TEXT PRIMARY KEY,
    command_type TEXT NOT NULL,
    command TEXT NOT NULL,
    justification TEXT NOT NULL,
    proposed_by TEXT NOT NULL,
    proposed_by_type TEXT NOT NULL,
    proposed_at INTEGER NOT NULL,
    status TEXT NOT NULL,
    reviewer TEXT,
    reviewer_type TEXT,
    decision_reason TEXT,
    decided_at INTEGER
);
CREATE TABLE IF NOT EXISTS str_reports (
    case_id TEXT PRIMARY KEY REFERENCES cases (id),
    subject_account TEXT NOT NULL,
    grounds TEXT NOT NULL,
    actions_taken TEXT NOT NULL,
    status TEXT NOT NULL,
    finalised_at TEXT
);
CREATE TABLE IF NOT EXISTS str_report_transactions (
    case_id TEXT NOT NULL REFERENCES str_reports (case_id),
    position INTEGER NOT NULL,
    transaction_id TEXT NOT NULL REFERENCES transactions (id),
    PRIMARY KEY (case_id, position)
);
