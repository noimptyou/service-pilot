CREATE TABLE customer_session (
                                  id BIGSERIAL PRIMARY KEY,
                                  customer_name VARCHAR(100) NOT NULL,
                                  status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
                                  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_message (
                              id BIGSERIAL PRIMARY KEY,
                              session_id BIGINT NOT NULL,
                              sender_type VARCHAR(30) NOT NULL,
                              content TEXT NOT NULL,
                              created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_chat_message_session
                                  FOREIGN KEY (session_id)
                                      REFERENCES customer_session(id)
                                      ON DELETE CASCADE
);

CREATE INDEX idx_chat_message_session_id
    ON chat_message(session_id);