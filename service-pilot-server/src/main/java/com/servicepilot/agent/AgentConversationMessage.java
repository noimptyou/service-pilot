package com.servicepilot.agent;

public record AgentConversationMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT
    }
}
