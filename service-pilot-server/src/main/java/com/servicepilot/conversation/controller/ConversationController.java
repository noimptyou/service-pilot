package com.servicepilot.conversation.controller;

import com.servicepilot.conversation.dto.CreateSessionRequest;
import com.servicepilot.conversation.dto.MessageResponse;
import com.servicepilot.conversation.dto.SendMessageRequest;
import com.servicepilot.conversation.dto.SessionResponse;
import com.servicepilot.conversation.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse createSession(@Valid @RequestBody CreateSessionRequest request) {
        return conversationService.createSession(request);
    }

    @PostMapping("/{sessionId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable Long sessionId,
            @Valid @RequestBody SendMessageRequest request) {
        return conversationService.sendMessage(sessionId, request);
    }

    @GetMapping("/{sessionId}/messages")
    public List<MessageResponse> getMessages(@PathVariable Long sessionId) {
        return conversationService.getMessages(sessionId);
    }
}
