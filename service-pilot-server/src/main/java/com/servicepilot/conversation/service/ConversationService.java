package com.servicepilot.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.servicepilot.conversation.domain.ChatMessage;
import com.servicepilot.conversation.domain.CustomerSession;
import com.servicepilot.conversation.domain.SenderType;
import com.servicepilot.conversation.domain.SessionStatus;
import com.servicepilot.conversation.dto.CreateSessionRequest;
import com.servicepilot.conversation.dto.MessageResponse;
import com.servicepilot.conversation.dto.SendMessageRequest;
import com.servicepilot.conversation.dto.SessionResponse;
import com.servicepilot.conversation.mapper.ChatMessageMapper;
import com.servicepilot.conversation.mapper.CustomerSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final CustomerSessionMapper customerSessionMapper;

    private final ChatMessageMapper chatMessageMapper;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        CustomerSession session = new CustomerSession();
        session.setCustomerName(request.getCustomerName().trim());
        session.setStatus(SessionStatus.WAITING);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);

        customerSessionMapper.insert(session);

        return new SessionResponse(
                session.getId(),
                session.getCustomerName(),
                session.getStatus(),
                session.getCreatedAt()
        );
    }

    @Transactional
    public MessageResponse sendMessage(Long sessionId, SendMessageRequest request) {
        CustomerSession session = requireSession(sessionId);
        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会话已结束，不能继续发送消息");
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType(SenderType.CUSTOMER);
        message.setContent(request.getContent().trim());
        message.setCreatedAt(OffsetDateTime.now());

        chatMessageMapper.insert(message);

        return toMessageResponse(message);
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(Long sessionId) {
        requireSession(sessionId);

        return chatMessageMapper.selectList(
                        Wrappers.<ChatMessage>lambdaQuery()
                                .eq(ChatMessage::getSessionId, sessionId)
                                .orderByAsc(ChatMessage::getCreatedAt)
                                .orderByAsc(ChatMessage::getId)
                ).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    private CustomerSession requireSession(Long sessionId) {
        CustomerSession session = customerSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    private MessageResponse toMessageResponse(ChatMessage message) {
        return new MessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getSenderType(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
