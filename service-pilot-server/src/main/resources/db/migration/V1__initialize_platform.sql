CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE platform_setting (
    setting_key VARCHAR(100) PRIMARY KEY,
    setting_value TEXT NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO platform_setting (setting_key, setting_value, description)
VALUES ('platform.initialized', 'true', 'Marks the initial ServicePilot database migration');

