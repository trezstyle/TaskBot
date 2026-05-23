-- Performance indexes for TaskBot

-- Reminder lookups: find due reminders by date + reminder_sent flag
CREATE INDEX IF NOT EXISTS idx_events_reminder_lookup ON events (reminder_sent, event_date, event_time);

-- User's events by date (upcoming/past lookups)
CREATE INDEX IF NOT EXISTS idx_events_user_date ON events (user_id, event_date);

-- User's events sorted by date+time (for list views)
CREATE INDEX IF NOT EXISTS idx_events_user_date_desc ON events (user_id, event_date DESC, event_time DESC);