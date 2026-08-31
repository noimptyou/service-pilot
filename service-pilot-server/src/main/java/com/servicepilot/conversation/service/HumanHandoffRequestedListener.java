package com.servicepilot.conversation.service;

import com.servicepilot.agent.HumanHandoffRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class HumanHandoffRequestedListener {

    private final HandoffService handoffService;

    @EventListener
    void onHumanHandoffRequested(HumanHandoffRequested event) {
        handoffService.request(event.sessionId(), event.reason());
    }
}
