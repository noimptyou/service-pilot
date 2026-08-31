package com.servicepilot.agent.tool;

import com.servicepilot.agent.HandoffToolResult;
import com.servicepilot.agent.HumanHandoffRequested;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HandoffTools {

    public static final String SESSION_ID_CONTEXT_KEY = "sessionId";

    private final ApplicationEventPublisher eventPublisher;

    @Tool(name = "request_human_handoff",
            description = "当客户明确要求人工客服，或问题无法可靠解决且确实需要人工处理时，提交转人工申请")
    public HandoffToolResult requestHumanHandoff(
            @ToolParam(description = "需要转人工的具体原因") String reason,
            ToolContext toolContext) {
        Long sessionId = readSessionId(toolContext);
        String normalizedReason = reason == null || reason.isBlank()
                ? "客户需要人工客服协助"
                : reason.trim();
        eventPublisher.publishEvent(new HumanHandoffRequested(sessionId, normalizedReason));
        return new HandoffToolResult(true, "已提交转人工申请，请等待人工客服接入");
    }

    private Long readSessionId(ToolContext toolContext) {
        Object value = toolContext.getContext().get(SESSION_ID_CONTEXT_KEY);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                // Fall through to a clear configuration error.
            }
        }
        throw new IllegalStateException("转人工工具缺少会话编号");
    }
}
