package com.servicepilot.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class CustomerSupportAgent {

    private static final String SYSTEM_PROMPT = """
            你是 ServicePilot 的智能客服。
            请使用中文，以礼貌、简洁、清晰的方式回答客户问题。
            不要编造订单状态、退款结果、平台规则或其他无法确认的信息。
            如果无法确定答案，请明确说明暂时无法确认，并建议客户联系人工客服。
            """;

    private final ObjectProvider<ChatClient.Builder> chatClientBuilderProvider;

    public CustomerSupportAgent(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        this.chatClientBuilderProvider = chatClientBuilderProvider;
    }

    public String reply(List<AgentConversationMessage> conversation) {
        ChatClient.Builder builder = chatClientBuilderProvider.getIfAvailable();
        if (builder == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI客服未启用");
        }

        try {
            List<Message> messages = conversation.stream()
                    .map(this::toSpringAiMessage)
                    .toList();

            String answer = builder.clone()
                    .defaultSystem(SYSTEM_PROMPT)
                    .build()
                    .prompt()
                    .messages(messages)
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI客服返回了空回复");
            }
            return answer.trim();
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI客服暂时无法回复", exception);
        }
    }

    private Message toSpringAiMessage(AgentConversationMessage message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }
}
