package com.servicepilot.conversation.dto;

import com.servicepilot.conversation.domain.HandoffStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class HandoffResponse {

    private Long id;

    private Long sessionId;

    private HandoffStatus status;

    private String reason;

    private String assignedAgent;

    private OffsetDateTime createdAt;

    private OffsetDateTime acceptedAt;

    private OffsetDateTime resolvedAt;
}
