CREATE TABLE IF NOT EXISTS event_publication (
    id UUID NOT NULL,
    completion_attempts INTEGER NOT NULL,
    completion_date TIMESTAMP(6) WITH TIME ZONE,
    event_type VARCHAR(255) NOT NULL,
    last_resubmission_date TIMESTAMP(6) WITH TIME ZONE,
    listener_id VARCHAR(255) NOT NULL,
    publication_date TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    serialized_event VARCHAR(255) NOT NULL,
    status VARCHAR(255),
    CONSTRAINT event_publication_pkey PRIMARY KEY (id),
    CONSTRAINT event_publication_status_check CHECK (
        status IN ('PUBLISHED', 'PROCESSING', 'COMPLETED', 'FAILED', 'RESUBMITTED')
    )
);
