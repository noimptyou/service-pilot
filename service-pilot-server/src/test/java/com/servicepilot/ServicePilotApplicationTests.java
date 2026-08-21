package com.servicepilot;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
	void modularStructureIsValid() {
		ApplicationModules.of(ServicePilotApplication.class).verify();
	}

}
