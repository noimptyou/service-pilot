package com.servicepilot.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ChatReplyResponse {

    private MessageResponse customerMessage;

    private MessageResponse aiMessage;

    private List<KnowledgeReferenceResponse> references;
}
