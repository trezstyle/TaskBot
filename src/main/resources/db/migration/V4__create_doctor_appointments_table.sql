CREATE TABLE IF NOT EXISTS doctor_appointments (
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    doctor_name       VARCHAR(255) NOT NULL,
    clinic_name       VARCHAR(255),
    address           VARCHAR(500),
    appointment_date  DATE NOT NULL,
    appointment_time  TIME NOT NULL,
    notes             TEXT,
    reminder_sent     BOOLEAN DEFAULT FALSE,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_doctor_appointments_user_id ON doctor_appointments(user_id);
CREATE INDEX idx_doctor_appointments_date ON doctor_appointments(appointment_date);
