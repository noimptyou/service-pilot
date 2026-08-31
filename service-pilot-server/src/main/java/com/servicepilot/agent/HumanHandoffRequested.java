package com.servicepilot.agent;

public record HumanHandoffRequested(Long sessionId, String reason) {
}
