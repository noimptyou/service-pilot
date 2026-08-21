package com.servicepilot.conversation.dto;

import com.servicepilot.conversation.domain.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class SessionResponse {

    private Long id;

    private String customerName;

    private SessionStatus status;

    private OffsetDateTime createdAt;
}
