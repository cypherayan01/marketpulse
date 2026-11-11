-- Latest output of each job as JSON, so the dashboard isn't empty after a restart.
CREATE TABLE report_snapshot (
    kind      VARCHAR(16)      PRIMARY KEY,
    payload   VARCHAR(1000000) NOT NULL,
    saved_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
