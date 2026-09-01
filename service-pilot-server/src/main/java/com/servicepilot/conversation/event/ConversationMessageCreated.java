package com.servicepilot.conversation.event;

public record ConversationMessageCreated(
        Long sessionId,
        Long messageId
) {
}
