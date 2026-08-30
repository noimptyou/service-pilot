package com.servicepilot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.servicepilot.agent.AgentConversationMessage;
import com.servicepilot.agent.CustomerSupportAgent;
import com.servicepilot.conversation.domain.ChatMessage;
import com.servicepilot.conversation.domain.CustomerSession;
import com.servicepilot.conversation.domain.SenderType;
import com.servicepilot.conversation.domain.SessionStatus;
import com.servicepilot.conversation.dto.CreateSessionRequest;
import com.servicepilot.conversation.dto.SessionResponse;
import com.servicepilot.conversation.mapper.ChatMessageMapper;
import com.servicepilot.conversation.mapper.CustomerSessionMapper;
import com.servicepilot.conversation.service.ConversationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ServicePilotApplicationTests {

	@Autowired
	private CustomerSessionMapper customerSessionMapper;

	@Autowired
	private ChatMessageMapper chatMessageMapper;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CustomerSupportAgent customerSupportAgent;

	@Test
	void contextLoads() {
		assertThat(customerSessionMapper).isNotNull();
		assertThat(chatMessageMapper).isNotNull();
	}

	@Test
	void mybatisPlusPersistsConversation() {
		CustomerSession session = new CustomerSession();
		session.setCustomerName("Test customer");
		session.setStatus(SessionStatus.WAITING);

		assertThat(customerSessionMapper.insert(session)).isEqualTo(1);
		assertThat(session.getId()).isNotNull();

		ChatMessage message = new ChatMessage();
		message.setSessionId(session.getId());
		message.setSenderType(SenderType.CUSTOMER);
		message.setContent("Where is my order?");

		assertThat(chatMessageMapper.insert(message)).isEqualTo(1);
		assertThat(message.getId()).isNotNull();
		assertThat(customerSessionMapper.selectById(session.getId()).getStatus())
				.isEqualTo(SessionStatus.WAITING);
		assertThat(chatMessageMapper.selectById(message.getId()).getContent())
				.isEqualTo("Where is my order?");
	}

	@Test
	void createsCustomerSession() {
		CreateSessionRequest request = new CreateSessionRequest();
		request.setCustomerName("  Zhang San  ");

		SessionResponse response = conversationService.createSession(request);

		assertThat(response.getId()).isNotNull();
		assertThat(response.getCustomerName()).isEqualTo("Zhang San");
		assertThat(response.getStatus()).isEqualTo(SessionStatus.WAITING);
		assertThat(response.getCreatedAt()).isNotNull();
		assertThat(customerSessionMapper.selectById(response.getId()).getCustomerName())
				.isEqualTo("Zhang San");
	}

	@Test
	void createsCustomerSessionThroughHttpApi() throws Exception {
		mockMvc.perform(post("/api/conversations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"customerName":"Li Si"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.customerName").value("Li Si"))
				.andExpect(jsonPath("$.status").value("WAITING"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void rejectsBlankCustomerName() throws Exception {
		mockMvc.perform(post("/api/conversations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"customerName":""}
							"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void sendsCustomerMessageThroughHttpApi() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Wang Wu");
		SessionResponse session = conversationService.createSession(createRequest);

		mockMvc.perform(post("/api/conversations/{sessionId}/messages", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Where is my order?"}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.sessionId").value(session.getId()))
				.andExpect(jsonPath("$.senderType").value("CUSTOMER"))
				.andExpect(jsonPath("$.content").value("Where is my order?"))
				.andExpect(jsonPath("$.createdAt").isNotEmpty());
	}

	@Test
	void rejectsBlankMessage() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Blank Message Tester");
		SessionResponse session = conversationService.createSession(createRequest);

		mockMvc.perform(post("/api/conversations/{sessionId}/messages", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":""}
							"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void returnsNotFoundWhenSendingMessageToMissingSession() throws Exception {
		mockMvc.perform(post("/api/conversations/{sessionId}/messages", Long.MAX_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Hello"}
							"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void rejectsMessageForClosedSession() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Closed Session Tester");
		SessionResponse response = conversationService.createSession(createRequest);
		CustomerSession session = customerSessionMapper.selectById(response.getId());
		session.setStatus(SessionStatus.CLOSED);
		customerSessionMapper.updateById(session);

		mockMvc.perform(post("/api/conversations/{sessionId}/messages", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Hello"}
							"""))
				.andExpect(status().isConflict());
	}

	@Test
	void getsConversationMessagesInOrder() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("History Tester");
		SessionResponse session = conversationService.createSession(createRequest);

		mockMvc.perform(post("/api/conversations/{sessionId}/messages", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"First message"}
							"""))
				.andExpect(status().isCreated());
		mockMvc.perform(post("/api/conversations/{sessionId}/messages", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Second message"}
							"""))
				.andExpect(status().isCreated());

		mockMvc.perform(get("/api/conversations/{sessionId}/messages", session.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].content").value("First message"))
				.andExpect(jsonPath("$[1].content").value("Second message"));
	}

	@Test
	void returnsEmptyMessageListForNewSession() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Empty History Tester");
		SessionResponse session = conversationService.createSession(createRequest);

		mockMvc.perform(get("/api/conversations/{sessionId}/messages", session.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void returnsNotFoundWhenGettingMessagesForMissingSession() throws Exception {
		mockMvc.perform(get("/api/conversations/{sessionId}/messages", Long.MAX_VALUE))
				.andExpect(status().isNotFound());
	}

	@Test
	void closesSessionAndRejectsFurtherMessages() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Close Session Tester");
		SessionResponse session = conversationService.createSession(createRequest);

		mockMvc.perform(patch("/api/conversations/{sessionId}/close", session.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(session.getId()))
				.andExpect(jsonPath("$.status").value("CLOSED"));

		assertThat(customerSessionMapper.selectById(session.getId()).getStatus())
				.isEqualTo(SessionStatus.CLOSED);

		mockMvc.perform(post("/api/conversations/{sessionId}/messages", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Can I still send?"}
							"""))
				.andExpect(status().isConflict());
	}

	@Test
	void closingSessionIsIdempotent() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Repeated Close Tester");
		SessionResponse session = conversationService.createSession(createRequest);

		mockMvc.perform(patch("/api/conversations/{sessionId}/close", session.getId()))
				.andExpect(status().isOk());
		mockMvc.perform(patch("/api/conversations/{sessionId}/close", session.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));
	}

	@Test
	void returnsNotFoundWhenClosingMissingSession() throws Exception {
		mockMvc.perform(patch("/api/conversations/{sessionId}/close", Long.MAX_VALUE))
				.andExpect(status().isNotFound());
	}

	@Test
	void chatsWithAiAndPersistsBothMessages() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("AI Chat Tester");
		SessionResponse session = conversationService.createSession(createRequest);
		when(customerSupportAgent.reply(anyList())).thenAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			return "Yes, eligible products can be returned.";
		});

		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Do you support returns?"}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.customerMessage.senderType").value("CUSTOMER"))
				.andExpect(jsonPath("$.customerMessage.content").value("Do you support returns?"))
				.andExpect(jsonPath("$.aiMessage.senderType").value("AI"))
				.andExpect(jsonPath("$.aiMessage.content")
						.value("Yes, eligible products can be returned."));

		List<ChatMessage> messages = chatMessageMapper.selectList(
				Wrappers.<ChatMessage>lambdaQuery()
						.eq(ChatMessage::getSessionId, session.getId())
						.orderByAsc(ChatMessage::getId)
		);
		assertThat(messages).extracting(ChatMessage::getSenderType)
				.containsExactly(SenderType.CUSTOMER, SenderType.AI);
	}

	@Test
	@SuppressWarnings("unchecked")
	void sendsRecentConversationHistoryToAi() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Multi-turn Chat Tester");
		SessionResponse session = conversationService.createSession(createRequest);
		when(customerSupportAgent.reply(anyList()))
				.thenReturn("Your order number is A100.", "I remember that your order number is A100.");

		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"My order number is A100."}
							"""))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"What is my order number?"}
							"""))
				.andExpect(status().isOk());

		ArgumentCaptor<List<AgentConversationMessage>> contextCaptor =
				ArgumentCaptor.forClass(List.class);
		verify(customerSupportAgent, times(2)).reply(contextCaptor.capture());

		List<AgentConversationMessage> secondContext = contextCaptor.getAllValues().get(1);
		assertThat(secondContext)
				.extracting(AgentConversationMessage::role, AgentConversationMessage::content)
				.containsExactly(
						tuple(AgentConversationMessage.Role.USER, "My order number is A100."),
						tuple(AgentConversationMessage.Role.ASSISTANT, "Your order number is A100."),
						tuple(AgentConversationMessage.Role.USER, "What is my order number?")
				);
	}

	@Test
	void keepsCustomerMessageWhenAiCallFails() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("AI Failure Tester");
		SessionResponse session = conversationService.createSession(createRequest);
		when(customerSupportAgent.reply(anyList()))
				.thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI客服暂时无法回复"));

		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Please keep this message."}
							"""))
				.andExpect(status().isBadGateway());

		List<ChatMessage> messages = chatMessageMapper.selectList(
				Wrappers.<ChatMessage>lambdaQuery()
						.eq(ChatMessage::getSessionId, session.getId())
						.orderByAsc(ChatMessage::getId)
		);
		assertThat(messages).extracting(ChatMessage::getSenderType, ChatMessage::getContent)
				.containsExactly(tuple(SenderType.CUSTOMER, "Please keep this message."));
	}

	@Test
	void rejectsAiChatForClosedSessionWithoutCallingModel() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Closed AI Chat Tester");
		SessionResponse session = conversationService.createSession(createRequest);
		conversationService.closeSession(session.getId());

		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Can I still chat?"}
							"""))
				.andExpect(status().isConflict());

		verifyNoInteractions(customerSupportAgent);
	}

	@Test
	void rejectsBlankAiChatMessage() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Blank AI Chat Tester");
		SessionResponse session = conversationService.createSession(createRequest);

		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":""}
							"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(customerSupportAgent);
	}

	@Test
	void returnsNotFoundWhenAiChatUsesMissingSession() throws Exception {
		mockMvc.perform(post("/api/conversations/{sessionId}/chat", Long.MAX_VALUE)
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Hello"}
							"""))
				.andExpect(status().isNotFound());

		verifyNoInteractions(customerSupportAgent);
	}

	@Test
	void modularStructureIsValid() {
		ApplicationModules.of(ServicePilotApplication.class).verify();
	}

}
