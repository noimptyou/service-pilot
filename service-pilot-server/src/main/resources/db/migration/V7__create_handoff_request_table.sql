CREATE TABLE handoff_request (
    id BIGSERIAL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    assigned_agent VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP WITH TIME ZONE,
    resolved_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_handoff_request_session
        FOREIGN KEY (session_id)
        REFERENCES customer_session(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_handoff_request_session_id
    ON handoff_request(session_id);

CREATE UNIQUE INDEX uq_handoff_request_active_session
    ON handoff_request(session_id)
    WHERE status IN ('PENDING', 'ACCEPTED');
