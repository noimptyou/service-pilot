package com.servicepilot.conversation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ChatReplyResponse {

    private MessageResponse customerMessage;

    private MessageResponse aiMessage;
}
