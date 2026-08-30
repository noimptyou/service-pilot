package com.servicepilot.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.servicepilot.agent.CustomerSupportAgent;
import com.servicepilot.conversation.domain.ChatMessage;
import com.servicepilot.conversation.domain.CustomerSession;
import com.servicepilot.conversation.domain.SenderType;
import com.servicepilot.conversation.domain.SessionStatus;
import com.servicepilot.conversation.dto.ChatReplyResponse;
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

    private final CustomerSupportAgent customerSupportAgent;

    @Transactional
    public SessionResponse createSession(CreateSessionRequest request) {
        OffsetDateTime now = OffsetDateTime.now();

        CustomerSession session = new CustomerSession();
        session.setCustomerName(request.getCustomerName().trim());
        session.setStatus(SessionStatus.WAITING);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);

        customerSessionMapper.insert(session);

        return toSessionResponse(session);
    }

    @Transactional
    public MessageResponse sendMessage(Long sessionId, SendMessageRequest request) {
        requireOpenSession(sessionId);
        return toMessageResponse(saveMessage(sessionId, SenderType.CUSTOMER, request.getContent()));
    }

    @Transactional
    public ChatReplyResponse chat(Long sessionId, SendMessageRequest request) {
        requireOpenSession(sessionId);

        ChatMessage customerMessage = saveMessage(sessionId, SenderType.CUSTOMER, request.getContent());
        String answer = customerSupportAgent.reply(customerMessage.getContent());
        ChatMessage aiMessage = saveMessage(sessionId, SenderType.AI, answer);

        return new ChatReplyResponse(
                toMessageResponse(customerMessage),
                toMessageResponse(aiMessage)
        );
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

    @Transactional
    public SessionResponse closeSession(Long sessionId) {
        CustomerSession session = requireSession(sessionId);
        if (session.getStatus() != SessionStatus.CLOSED) {
            session.setStatus(SessionStatus.CLOSED);
            session.setUpdatedAt(OffsetDateTime.now());
            customerSessionMapper.updateById(session);
        }
        return toSessionResponse(session);
    }

    private CustomerSession requireSession(Long sessionId) {
        CustomerSession session = customerSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    private CustomerSession requireOpenSession(Long sessionId) {
        CustomerSession session = requireSession(sessionId);
        if (session.getStatus() == SessionStatus.CLOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会话已结束，不能继续发送消息");
        }
        return session;
    }

    private ChatMessage saveMessage(Long sessionId, SenderType senderType, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderType(senderType);
        message.setContent(content.trim());
        message.setCreatedAt(OffsetDateTime.now());
        chatMessageMapper.insert(message);
        return message;
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

    private SessionResponse toSessionResponse(CustomerSession session) {
        return new SessionResponse(
                session.getId(),
                session.getCustomerName(),
                session.getStatus(),
                session.getCreatedAt()
        );
    }
}
