package com.servicepilot.conversation.controller;

import com.servicepilot.conversation.service.ConversationSseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationEventController {

    private final ConversationSseService conversationSseService;

    @GetMapping(
            value = "/{sessionId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public SseEmitter subscribe(@PathVariable Long sessionId) {
        return conversationSseService.subscribe(sessionId);
    }
}
