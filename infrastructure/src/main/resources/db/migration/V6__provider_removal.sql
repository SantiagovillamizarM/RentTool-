ALTER TABLE users ADD COLUMN removed_at DATETIME(6);
CREATE INDEX idx_users_role_removed ON users(role, removed_at);
