CREATE TABLE IF NOT EXISTS reminders (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reminder_type   VARCHAR(20) NOT NULL,
    reference_id    BIGINT,
    message         VARCHAR(1000) NOT NULL,
    remind_at       TIMESTAMP NOT NULL,
    is_sent         BOOLEAN DEFAULT FALSE,
    cron_expression VARCHAR(100),
    is_recurring    BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reminders_user_id ON reminders(user_id);
CREATE INDEX idx_reminders_remind_at ON reminders(remind_at);
CREATE INDEX idx_reminders_is_sent ON reminders(is_sent);
