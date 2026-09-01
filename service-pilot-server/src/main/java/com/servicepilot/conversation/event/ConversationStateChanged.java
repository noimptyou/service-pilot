package com.servicepilot.conversation.event;

public record ConversationStateChanged(
        Long sessionId,
        ConversationStateChangeType type
) {
}
