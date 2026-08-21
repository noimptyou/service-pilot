ALTER TABLE event_publication
    ALTER COLUMN listener_id TYPE TEXT,
    ALTER COLUMN event_type TYPE TEXT,
    ALTER COLUMN serialized_event TYPE TEXT,
    ALTER COLUMN status TYPE TEXT,
    ALTER COLUMN completion_attempts DROP NOT NULL;

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING HASH (serialized_event);

CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);
