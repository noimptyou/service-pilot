package com.servicepilot;

import com.servicepilot.conversation.domain.ChatMessage;
import com.servicepilot.conversation.domain.CustomerSession;
import com.servicepilot.conversation.domain.SenderType;
import com.servicepilot.conversation.domain.SessionStatus;
import com.servicepilot.conversation.mapper.ChatMessageMapper;
import com.servicepilot.conversation.mapper.CustomerSessionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ServicePilotApplicationTests {

	@Autowired
	private CustomerSessionMapper customerSessionMapper;

	@Autowired
	private ChatMessageMapper chatMessageMapper;

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
	void modularStructureIsValid() {
		ApplicationModules.of(ServicePilotApplication.class).verify();
	}

}
