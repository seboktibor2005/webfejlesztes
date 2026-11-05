ALTER TABLE maintenance_period ADD COLUMN loan_id BIGINT UNIQUE REFERENCES loan(id);
CREATE INDEX idx_loan_open_due ON loan(due_at, id) WHERE returned_at IS NULL;
