package com.servicepilot.conversation.service;

import com.servicepilot.conversation.event.ConversationMessageCreated;
import com.servicepilot.conversation.event.ConversationStateChanged;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class ConversationRealtimeEventListener {

    private final ConversationSseService conversationSseService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onMessageCreated(ConversationMessageCreated event) {
        conversationSseService.publishMessageCreated(event);
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onStateChanged(ConversationStateChanged event) {
        conversationSseService.publishStateChanged(event);
    }
}
