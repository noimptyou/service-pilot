package com.servicepilot.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.servicepilot.conversation.domain.CustomerSession;
import com.servicepilot.conversation.domain.HandoffRequest;
import com.servicepilot.conversation.domain.HandoffStatus;
import com.servicepilot.conversation.domain.SessionStatus;
import com.servicepilot.conversation.dto.HandoffResponse;
import com.servicepilot.conversation.event.ConversationStateChanged;
import com.servicepilot.conversation.event.ConversationStateChangeType;
import com.servicepilot.conversation.mapper.CustomerSessionMapper;
import com.servicepilot.conversation.mapper.HandoffRequestMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HandoffService {

    private final CustomerSessionMapper customerSessionMapper;

    private final HandoffRequestMapper handoffRequestMapper;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public HandoffResponse request(Long sessionId, String reason) {
        CustomerSession session = requireOpenSession(sessionId);
        HandoffRequest activeRequest = findActiveRequest(sessionId);
        if (activeRequest != null) {
            return toResponse(activeRequest);
        }

        HandoffRequest handoffRequest = new HandoffRequest();
        handoffRequest.setSessionId(sessionId);
        handoffRequest.setStatus(HandoffStatus.PENDING);
        handoffRequest.setReason(reason.trim());
        handoffRequest.setCreatedAt(OffsetDateTime.now());
        if (handoffRequestMapper.insert(handoffRequest) != 1) {
            throw new IllegalStateException("创建转人工申请失败");
        }

        session.setStatus(SessionStatus.HUMAN_REQUESTED);
        session.setUpdatedAt(OffsetDateTime.now());
        customerSessionMapper.updateById(session);
        eventPublisher.publishEvent(new ConversationStateChanged(
                sessionId,
                ConversationStateChangeType.HANDOFF_REQUESTED
        ));
        return toResponse(handoffRequest);
    }

    @Transactional(readOnly = true)
    public HandoffResponse getLatest(Long sessionId) {
        requireSession(sessionId);
        HandoffRequest request = handoffRequestMapper.selectOne(
                Wrappers.<HandoffRequest>lambdaQuery()
                        .eq(HandoffRequest::getSessionId, sessionId)
                        .orderByDesc(HandoffRequest::getCreatedAt)
                        .orderByDesc(HandoffRequest::getId)
                        .last("LIMIT 1")
        );
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该会话没有转人工申请");
        }
        return toResponse(request);
    }

    @Transactional
    public HandoffResponse accept(Long sessionId, String agentName) {
        CustomerSession session = requireOpenSession(sessionId);
        HandoffRequest request = handoffRequestMapper.selectOne(
                Wrappers.<HandoffRequest>lambdaQuery()
                        .eq(HandoffRequest::getSessionId, sessionId)
                        .eq(HandoffRequest::getStatus, HandoffStatus.PENDING)
                        .orderByDesc(HandoffRequest::getCreatedAt)
                        .orderByDesc(HandoffRequest::getId)
                        .last("LIMIT 1")
        );
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有等待接单的转人工申请");
        }

        OffsetDateTime acceptedAt = OffsetDateTime.now();
        String normalizedAgentName = agentName.trim();
        int updated = handoffRequestMapper.update(
                null,
                Wrappers.<HandoffRequest>lambdaUpdate()
                        .eq(HandoffRequest::getId, request.getId())
                        .eq(HandoffRequest::getStatus, HandoffStatus.PENDING)
                        .set(HandoffRequest::getStatus, HandoffStatus.ACCEPTED)
                        .set(HandoffRequest::getAssignedAgent, normalizedAgentName)
                        .set(HandoffRequest::getAcceptedAt, acceptedAt)
        );
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该转人工申请已被其他客服处理");
        }
        request.setStatus(HandoffStatus.ACCEPTED);
        request.setAssignedAgent(normalizedAgentName);
        request.setAcceptedAt(acceptedAt);

        session.setStatus(SessionStatus.HUMAN_ACTIVE);
        session.setUpdatedAt(OffsetDateTime.now());
        customerSessionMapper.updateById(session);
        eventPublisher.publishEvent(new ConversationStateChanged(
                sessionId,
                ConversationStateChangeType.HANDOFF_ACCEPTED
        ));
        return toResponse(request);
    }

    @Transactional
    public void resolveActive(Long sessionId) {
        List<HandoffRequest> activeRequests = handoffRequestMapper.selectList(
                Wrappers.<HandoffRequest>lambdaQuery()
                        .eq(HandoffRequest::getSessionId, sessionId)
                        .in(HandoffRequest::getStatus, HandoffStatus.PENDING, HandoffStatus.ACCEPTED)
        );
        OffsetDateTime resolvedAt = OffsetDateTime.now();
        activeRequests.forEach(request -> {
            request.setStatus(HandoffStatus.RESOLVED);
            request.setResolvedAt(resolvedAt);
            handoffRequestMapper.updateById(request);
        });
        if (!activeRequests.isEmpty()) {
            eventPublisher.publishEvent(new ConversationStateChanged(
                    sessionId,
                    ConversationStateChangeType.HANDOFF_RESOLVED
            ));
        }
    }

    private HandoffRequest findActiveRequest(Long sessionId) {
        return handoffRequestMapper.selectOne(
                Wrappers.<HandoffRequest>lambdaQuery()
                        .eq(HandoffRequest::getSessionId, sessionId)
                        .in(HandoffRequest::getStatus, HandoffStatus.PENDING, HandoffStatus.ACCEPTED)
                        .orderByDesc(HandoffRequest::getCreatedAt)
                        .orderByDesc(HandoffRequest::getId)
                        .last("LIMIT 1")
        );
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "会话已结束，不能转人工");
        }
        return session;
    }

    private HandoffResponse toResponse(HandoffRequest request) {
        return new HandoffResponse(
                request.getId(),
                request.getSessionId(),
                request.getStatus(),
                request.getReason(),
                request.getAssignedAgent(),
                request.getCreatedAt(),
                request.getAcceptedAt(),
                request.getResolvedAt()
        );
    }
}
