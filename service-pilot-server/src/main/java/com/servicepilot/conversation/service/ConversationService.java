package com.servicepilot.conversation.service;

import com.servicepilot.conversation.domain.CustomerSession;
import com.servicepilot.conversation.domain.SessionStatus;
import com.servicepilot.conversation.dto.CreateSessionRequest;
import com.servicepilot.conversation.dto.SessionResponse;
import com.servicepilot.conversation.mapper.CustomerSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final CustomerSessionMapper customerSessionMapper;

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
}
