package com.servicepilot;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.servicepilot.agent.AgentConversationMessage;
import com.servicepilot.agent.AgentRequestContext;
import com.servicepilot.agent.CustomerSupportAgent;
import com.servicepilot.agent.tool.HandoffTools;
import com.servicepilot.conversation.domain.ChatMessage;
import com.servicepilot.conversation.domain.CustomerSession;
import com.servicepilot.conversation.domain.HandoffRequest;
import com.servicepilot.conversation.domain.HandoffStatus;
import com.servicepilot.conversation.domain.SenderType;
import com.servicepilot.conversation.domain.SessionStatus;
import com.servicepilot.conversation.dto.CreateSessionRequest;
import com.servicepilot.conversation.dto.SendMessageRequest;
import com.servicepilot.conversation.dto.SessionResponse;
import com.servicepilot.conversation.mapper.ChatMessageMapper;
import com.servicepilot.conversation.mapper.CustomerSessionMapper;
import com.servicepilot.conversation.mapper.HandoffRequestMapper;
import com.servicepilot.conversation.service.ConversationService;
import com.servicepilot.knowledge.KnowledgeReference;
import com.servicepilot.knowledge.domain.KnowledgeDocument;
import com.servicepilot.knowledge.domain.KnowledgeDocumentStatus;
import com.servicepilot.knowledge.mapper.KnowledgeDocumentMapper;
import com.servicepilot.order.OrderLookupResult;
import com.servicepilot.order.domain.CustomerOrder;
import com.servicepilot.order.domain.OrderStatus;
import com.servicepilot.order.mapper.CustomerOrderMapper;
import com.servicepilot.order.service.OrderService;
import com.servicepilot.order.tool.OrderTools;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
		"service-pilot.security.admin.username=test-admin",
		"service-pilot.security.admin.password=test-password"
})
@AutoConfigureMockMvc
class ServicePilotApplicationTests {

	@Autowired
	private CustomerSessionMapper customerSessionMapper;

	@Autowired
	private ChatMessageMapper chatMessageMapper;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private HandoffRequestMapper handoffRequestMapper;

	@Autowired
	private HandoffTools handoffTools;

	@Autowired
	private KnowledgeDocumentMapper knowledgeDocumentMapper;

	@Autowired
	private CustomerOrderMapper customerOrderMapper;

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderTools orderTools;

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private CustomerSupportAgent customerSupportAgent;

	@MockitoBean
	private VectorStore vectorStore;

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
	void subscribesToConversationEvents() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("SSE customer");
		SessionResponse session = conversationService.createSession(createRequest);

		MvcResult result = mockMvc.perform(get("/api/conversations/{sessionId}/events", session.getId())
					.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andReturn();

		assertThat(result.getResponse().getContentType())
				.startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
		SendMessageRequest messageRequest = new SendMessageRequest();
		messageRequest.setContent("Realtime message");
		conversationService.sendMessage(session.getId(), messageRequest);
		assertThat(result.getResponse().getContentAsString())
				.contains("event:message-created")
				.contains("\"sessionId\":" + session.getId());
		Objects.requireNonNull(
				result.getRequest().getAsyncContext(),
				"SSE request should have an async context"
		).complete();
	}

	@Test
	void rejectsConversationEventSubscriptionForMissingSession() throws Exception {
		mockMvc.perform(get("/api/conversations/{sessionId}/events", Long.MAX_VALUE)
					.accept(MediaType.TEXT_EVENT_STREAM))
				.andExpect(status().isNotFound());
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
		when(customerSupportAgent.reply(anyList(), anyList(), any(AgentRequestContext.class))).thenAnswer(invocation -> {
			assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
			AgentRequestContext requestContext = invocation.getArgument(2, AgentRequestContext.class);
			assertThat(requestContext.sessionId()).isEqualTo(session.getId());
			assertThat(requestContext.customerName()).isEqualTo("AI Chat Tester");
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
		when(customerSupportAgent.reply(anyList(), anyList(), any(AgentRequestContext.class)))
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
		verify(customerSupportAgent, times(2))
				.reply(contextCaptor.capture(), anyList(), any(AgentRequestContext.class));

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
		when(customerSupportAgent.reply(anyList(), anyList(), any(AgentRequestContext.class)))
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
	@SuppressWarnings("unchecked")
	void retrievesKnowledgeAndReturnsReferencesWithAiReply() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("RAG Chat Tester");
		SessionResponse session = conversationService.createSession(createRequest);

		Document referenceDocument = Document.builder()
				.id("00000000-0000-0000-0000-000000000001")
				.text("Eligible products support seven-day no-reason returns.")
				.metadata("knowledge_document_id", "1")
				.metadata("document_title", "Seven-day return policy")
				.metadata("chunk_index", 0)
				.metadata("source_type", "knowledge_document")
				.score(0.91)
				.build();
		when(vectorStore.similaritySearch(any(SearchRequest.class)))
				.thenReturn(List.of(referenceDocument));
		when(customerSupportAgent.reply(anyList(), anyList(), any(AgentRequestContext.class)))
				.thenReturn("Eligible products support seven-day no-reason returns.");

		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Can I return a product within seven days?"}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.aiMessage.content")
						.value("Eligible products support seven-day no-reason returns."))
				.andExpect(jsonPath("$.references.length()").value(1))
				.andExpect(jsonPath("$.references[0].knowledgeDocumentId").value(1))
				.andExpect(jsonPath("$.references[0].documentTitle")
						.value("Seven-day return policy"))
				.andExpect(jsonPath("$.references[0].chunkIndex").value(0))
				.andExpect(jsonPath("$.references[0].score").value(0.91));

		ArgumentCaptor<SearchRequest> searchCaptor = ArgumentCaptor.forClass(SearchRequest.class);
		verify(vectorStore).similaritySearch(searchCaptor.capture());
		assertThat(searchCaptor.getValue().getQuery())
				.isEqualTo("Can I return a product within seven days?");
		assertThat(searchCaptor.getValue().getTopK()).isEqualTo(3);
		assertThat(searchCaptor.getValue().getSimilarityThreshold()).isEqualTo(0.70);

		ArgumentCaptor<List<KnowledgeReference>> referencesCaptor =
				ArgumentCaptor.forClass(List.class);
		verify(customerSupportAgent)
				.reply(anyList(), referencesCaptor.capture(), any(AgentRequestContext.class));
		assertThat(referencesCaptor.getValue())
				.extracting(
						KnowledgeReference::knowledgeDocumentId,
						KnowledgeReference::documentTitle,
						KnowledgeReference::content,
						KnowledgeReference::score
				)
				.containsExactly(tuple(
						1L,
						"Seven-day return policy",
						"Eligible products support seven-day no-reason returns.",
						0.91
				));
	}

	@Test
	void keepsCustomerMessageWhenKnowledgeRetrievalFails() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("RAG Failure Tester");
		SessionResponse session = conversationService.createSession(createRequest);
		when(vectorStore.similaritySearch(any(SearchRequest.class)))
				.thenThrow(new RuntimeException("vector search unavailable"));

		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Please answer using the knowledge base."}
							"""))
				.andExpect(status().isBadGateway());

		verifyNoInteractions(customerSupportAgent);
		List<ChatMessage> messages = chatMessageMapper.selectList(
				Wrappers.<ChatMessage>lambdaQuery()
						.eq(ChatMessage::getSessionId, session.getId())
						.orderByAsc(ChatMessage::getId)
		);
		assertThat(messages).extracting(ChatMessage::getSenderType, ChatMessage::getContent)
				.containsExactly(tuple(
						SenderType.CUSTOMER,
						"Please answer using the knowledge base."
				));
	}

	@Test
	void orderServiceReturnsOnlyOrderOwnedByCurrentCustomer() {
		CustomerOrder order = saveOrder(
				"SP-ORDER-OWNER-1001",
				"Order Owner",
				"Wireless headphones",
				OrderStatus.SHIPPED,
				"SF10000001"
		);

		OrderLookupResult ownedResult = orderService.findForCustomer(
				"Order Owner",
				"sp-order-owner-1001"
		);
		OrderLookupResult otherCustomerResult = orderService.findForCustomer(
				"Another Customer",
				order.getOrderNumber()
		);

		assertThat(ownedResult.found()).isTrue();
		assertThat(ownedResult.orderNumber()).isEqualTo("SP-ORDER-OWNER-1001");
		assertThat(ownedResult.productName()).isEqualTo("Wireless headphones");
		assertThat(ownedResult.status()).isEqualTo("已发货");
		assertThat(ownedResult.trackingNumber()).isEqualTo("SF10000001");
		assertThat(otherCustomerResult.found()).isFalse();
		assertThat(otherCustomerResult.productName()).isNull();
		assertThat(otherCustomerResult.trackingNumber()).isNull();
	}

	@Test
	void orderToolUsesServerProvidedCustomerContext() {
		saveOrder(
				"SP-ORDER-TOOL-1002",
				"Tool Customer",
				"Mechanical keyboard",
				OrderStatus.DELIVERED,
				"YT10000002"
		);

		OrderLookupResult result = orderTools.queryOrder(
				"SP-ORDER-TOOL-1002",
				new ToolContext(Map.of(
						OrderTools.CUSTOMER_NAME_CONTEXT_KEY,
						"Tool Customer"
				))
		);

		assertThat(result.found()).isTrue();
		assertThat(result.status()).isEqualTo("已送达");
		assertThat(result.productName()).isEqualTo("Mechanical keyboard");
	}

	@Test
	void registersOrderToolWithSafeModelVisibleSchema() {
		saveOrder(
				"SP-ORDER-CALLBACK-1003",
				"Callback Customer",
				"Smart watch",
				OrderStatus.PROCESSING,
				null
		);

		ToolCallback[] callbacks = ToolCallbacks.from(orderTools);
		assertThat(callbacks).hasSize(1);
		ToolCallback callback = callbacks[0];
		assertThat(callback.getToolDefinition().name()).isEqualTo("query_order");
		assertThat(callback.getToolDefinition().inputSchema())
				.contains("orderNumber")
				.doesNotContain(OrderTools.CUSTOMER_NAME_CONTEXT_KEY)
				.doesNotContain("ToolContext");

		String toolResult = callback.call(
				"{\"orderNumber\":\"SP-ORDER-CALLBACK-1003\"}",
				new ToolContext(Map.of(
						OrderTools.CUSTOMER_NAME_CONTEXT_KEY,
						"Callback Customer"
				))
		);
		assertThat(toolResult)
				.contains("SP-ORDER-CALLBACK-1003")
				.contains("Smart watch")
				.contains("处理中");
	}

	@Test
	void orderToolRejectsMissingCustomerContext() {
		assertThatThrownBy(() -> orderTools.queryOrder(
				"SP-ORDER-MISSING-CONTEXT",
				new ToolContext(Map.of())
		))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("缺少当前客户身份，不能查询订单");
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
	@WithMockUser(roles = "ADMIN")
	@SuppressWarnings("unchecked")
	void createsKnowledgeDocumentAndWritesVectorChunks() throws Exception {
		mockMvc.perform(post("/api/knowledge/documents")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "title":"Seven-day return policy",
							  "content":"Eligible products may be returned within seven days. Returned products must remain complete with packaging and accessories. Customized products are not eligible for no-reason returns."
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.title").value("Seven-day return policy"))
				.andExpect(jsonPath("$.status").value("READY"))
				.andExpect(jsonPath("$.chunkCount").isNumber())
				.andExpect(jsonPath("$.createdAt").isNotEmpty());

		ArgumentCaptor<List<Document>> chunksCaptor = ArgumentCaptor.forClass(List.class);
		verify(vectorStore).add(chunksCaptor.capture());
		List<Document> chunks = chunksCaptor.getValue();
		assertThat(chunks).isNotEmpty();
		assertThat(chunks)
				.allSatisfy(chunk -> {
					assertThat(chunk.getText()).isNotBlank();
					assertThat(chunk.getMetadata())
							.containsEntry("document_title", "Seven-day return policy")
							.containsEntry("source_type", "knowledge_document");
				});

		KnowledgeDocument savedDocument = knowledgeDocumentMapper.selectOne(
				Wrappers.<KnowledgeDocument>lambdaQuery()
						.eq(KnowledgeDocument::getTitle, "Seven-day return policy")
		);
		assertThat(savedDocument.getStatus()).isEqualTo(KnowledgeDocumentStatus.READY);
		assertThat(savedDocument.getChunkCount()).isEqualTo(chunks.size());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void rejectsBlankKnowledgeContent() throws Exception {
		mockMvc.perform(post("/api/knowledge/documents")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Empty policy","content":""}
							"""))
				.andExpect(status().isBadRequest());

		verify(vectorStore, never()).add(anyList());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void marksKnowledgeDocumentFailedWhenVectorWriteFails() throws Exception {
		doThrow(new RuntimeException("embedding unavailable"))
				.when(vectorStore).add(anyList());

		mockMvc.perform(post("/api/knowledge/documents")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "title":"Failed vector policy",
							  "content":"This content is long enough to be split and sent to the vector store, but the simulated embedding service will fail."
							}
							"""))
				.andExpect(status().isBadGateway());

		KnowledgeDocument failedDocument = knowledgeDocumentMapper.selectOne(
				Wrappers.<KnowledgeDocument>lambdaQuery()
						.eq(KnowledgeDocument::getTitle, "Failed vector policy")
		);
		assertThat(failedDocument.getStatus()).isEqualTo(KnowledgeDocumentStatus.FAILED);
		assertThat(failedDocument.getChunkCount()).isZero();
	}

	@Test
	void rejectsAnonymousKnowledgeCreation() throws Exception {
		mockMvc.perform(post("/api/knowledge/documents")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Protected policy","content":"Protected knowledge content."}
							"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void allowsConfiguredAdministratorToCreateKnowledge() throws Exception {
		mockMvc.perform(post("/api/knowledge/documents")
					.with(httpBasic("test-admin", "test-password"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Administrator policy","content":"Only an authenticated administrator may add this knowledge."}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("READY"));
	}

	@Test
	void rejectsIncorrectAdministratorPassword() throws Exception {
		mockMvc.perform(post("/api/knowledge/documents")
					.with(httpBasic("test-admin", "wrong-password"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Rejected policy","content":"This request must not reach the controller."}
							"""))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "USER")
	void rejectsAuthenticatedUserWithoutAdminRole() throws Exception {
		mockMvc.perform(post("/api/knowledge/documents")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"title":"Forbidden policy","content":"A normal user must not add knowledge."}
							"""))
				.andExpect(status().isForbidden());
	}

	@Test
	void requestsHumanHandoffOnlyOnceAndStopsAiReplies() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Handoff Customer");
		SessionResponse session = conversationService.createSession(createRequest);

		for (int attempt = 0; attempt < 2; attempt++) {
			mockMvc.perform(post("/api/conversations/{sessionId}/handoff", session.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"reason":"  I need a human agent.  "}
								"""))
					.andExpect(status().isCreated())
					.andExpect(jsonPath("$.sessionId").value(session.getId()))
					.andExpect(jsonPath("$.status").value("PENDING"))
					.andExpect(jsonPath("$.reason").value("I need a human agent."));
		}

		assertThat(handoffRequestMapper.selectCount(
				Wrappers.<HandoffRequest>lambdaQuery()
						.eq(HandoffRequest::getSessionId, session.getId())
		)).isEqualTo(1);
		assertThat(customerSessionMapper.selectById(session.getId()).getStatus())
				.isEqualTo(SessionStatus.HUMAN_REQUESTED);

		mockMvc.perform(post("/api/conversations/{sessionId}/chat", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Can AI still answer?"}
							"""))
				.andExpect(status().isConflict());
		verifyNoInteractions(customerSupportAgent);
	}

	@Test
	void protectsAgentActionsAndAllowsAdministratorToHandleConversation() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Protected Handoff Customer");
		SessionResponse session = conversationService.createSession(createRequest);
		mockMvc.perform(post("/api/conversations/{sessionId}/handoff", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"reason":"My issue needs manual review."}
							"""))
				.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/conversations/{sessionId}/handoff/accept", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"agentName":"Agent Alice"}
							"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(patch("/api/conversations/{sessionId}/handoff/accept", session.getId())
					.with(httpBasic("test-admin", "test-password"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"agentName":"  Agent Alice  "}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ACCEPTED"))
				.andExpect(jsonPath("$.assignedAgent").value("Agent Alice"));
		assertThat(customerSessionMapper.selectById(session.getId()).getStatus())
				.isEqualTo(SessionStatus.HUMAN_ACTIVE);

		mockMvc.perform(post("/api/conversations/{sessionId}/agent-messages", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Hello from a fake agent."}
							"""))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/conversations/{sessionId}/agent-messages", session.getId())
					.with(httpBasic("test-admin", "test-password"))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"content":"Hello, I am handling your request."}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.senderType").value("AGENT"))
				.andExpect(jsonPath("$.content").value("Hello, I am handling your request."));
	}

	@Test
	void resolvesActiveHandoffWhenConversationCloses() throws Exception {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Closing Handoff Customer");
		SessionResponse session = conversationService.createSession(createRequest);
		mockMvc.perform(post("/api/conversations/{sessionId}/handoff", session.getId())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"reason":"Please transfer me."}
							"""))
				.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/conversations/{sessionId}/close", session.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CLOSED"));

		HandoffRequest handoff = handoffRequestMapper.selectOne(
				Wrappers.<HandoffRequest>lambdaQuery()
						.eq(HandoffRequest::getSessionId, session.getId())
		);
		assertThat(handoff.getStatus()).isEqualTo(HandoffStatus.RESOLVED);
		assertThat(handoff.getResolvedAt()).isNotNull();

		mockMvc.perform(get("/api/conversations/{sessionId}/handoff", session.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("RESOLVED"))
				.andExpect(jsonPath("$.resolvedAt").isNotEmpty());
	}

	@Test
	void handoffToolKeepsSessionIdOutsideModelVisibleSchemaAndCreatesRequest() {
		CreateSessionRequest createRequest = new CreateSessionRequest();
		createRequest.setCustomerName("Tool Handoff Customer");
		SessionResponse session = conversationService.createSession(createRequest);

		ToolCallback[] callbacks = ToolCallbacks.from(handoffTools);
		assertThat(callbacks).hasSize(1);
		ToolCallback callback = callbacks[0];
		assertThat(callback.getToolDefinition().name()).isEqualTo("request_human_handoff");
		assertThat(callback.getToolDefinition().inputSchema())
				.contains("reason")
				.doesNotContain(HandoffTools.SESSION_ID_CONTEXT_KEY)
				.doesNotContain("ToolContext");

		String toolResult = callback.call(
				"{\"reason\":\"The customer explicitly requested a human agent.\"}",
				new ToolContext(Map.of(HandoffTools.SESSION_ID_CONTEXT_KEY, session.getId()))
		);

		assertThat(toolResult).contains("已提交转人工申请");
		HandoffRequest handoff = handoffRequestMapper.selectOne(
				Wrappers.<HandoffRequest>lambdaQuery()
						.eq(HandoffRequest::getSessionId, session.getId())
		);
		assertThat(handoff.getStatus()).isEqualTo(HandoffStatus.PENDING);
		assertThat(handoff.getReason())
				.isEqualTo("The customer explicitly requested a human agent.");
	}

	@Test
	void modularStructureIsValid() {
		ApplicationModules.of(ServicePilotApplication.class).verify();
	}

	private CustomerOrder saveOrder(
			String orderNumber,
			String customerName,
			String productName,
			OrderStatus status,
			String trackingNumber
	) {
		CustomerOrder order = new CustomerOrder();
		order.setOrderNumber(orderNumber);
		order.setCustomerName(customerName);
		order.setProductName(productName);
		order.setStatus(status);
		order.setTrackingNumber(trackingNumber);
		assertThat(customerOrderMapper.insert(order)).isEqualTo(1);
		assertThat(order.getId()).isNotNull();
		return order;
	}

}
