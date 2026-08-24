package com.servicepilot.conversation.dto;

import com.servicepilot.conversation.domain.SenderType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class MessageResponse {

    private Long id;
    private Long sessionId;
    private SenderType senderType;
    private String content;
    private OffsetDateTime createdAt;
}
