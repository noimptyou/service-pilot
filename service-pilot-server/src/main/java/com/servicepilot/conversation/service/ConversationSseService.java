package com.servicepilot.conversation.service;

import com.servicepilot.conversation.event.ConversationMessageCreated;
import com.servicepilot.conversation.event.ConversationStateChanged;
import com.servicepilot.conversation.mapper.CustomerSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class ConversationSseService {

    private static final long CONNECTION_TIMEOUT =
            Duration.ofMinutes(30).toMillis();

    private final CustomerSessionMapper customerSessionMapper;

    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long sessionId) {
        if (customerSessionMapper.selectById(sessionId) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }

        SseEmitter emitter = new SseEmitter(CONNECTION_TIMEOUT);
        emitters.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArrayList<>())
                .add(emitter);
        emitter.onCompletion(() -> remove(sessionId, emitter));
        emitter.onTimeout(() -> remove(sessionId, emitter));
        emitter.onError(error -> remove(sessionId, emitter));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("sessionId", sessionId), MediaType.APPLICATION_JSON));
        } catch (IOException exception) {
            remove(sessionId, emitter);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "实时消息连接创建失败",
                    exception
            );
        }
        return emitter;
    }

    public void publishMessageCreated(ConversationMessageCreated event) {
        broadcast(
                event.sessionId(),
                SseEmitter.event()
                        .id(event.messageId().toString())
                        .name("message-created")
                        .data(event, MediaType.APPLICATION_JSON)
        );
    }

    public void publishStateChanged(ConversationStateChanged event) {
        broadcast(
                event.sessionId(),
                SseEmitter.event()
                        .name("conversation-state-changed")
                        .data(event, MediaType.APPLICATION_JSON)
        );
    }

    private void broadcast(Long sessionId, SseEmitter.SseEventBuilder event) {
        CopyOnWriteArrayList<SseEmitter> sessionEmitters = emitters.get(sessionId);
        if (sessionEmitters == null) {
            return;
        }

        sessionEmitters.forEach(emitter -> {
            try {
                emitter.send(event);
            } catch (IOException | IllegalStateException exception) {
                remove(sessionId, emitter);
                emitter.complete();
            }
        });
    }

    private void remove(Long sessionId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> sessionEmitters = emitters.get(sessionId);
        if (sessionEmitters == null) {
            return;
        }
        sessionEmitters.remove(emitter);
        if (sessionEmitters.isEmpty()) {
            emitters.remove(sessionId, sessionEmitters);
        }
    }
}
