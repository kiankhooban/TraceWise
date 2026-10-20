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
