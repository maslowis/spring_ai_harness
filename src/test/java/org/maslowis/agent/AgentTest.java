package org.maslowis.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTest {

    ApplicationEventPublisher eventPublisher = new MockEventPublisher();

    @Test
    void testSimpleAnswer() {
        Agent agent = new Agent(MockChatModel.replyingWith("I am Harness."), eventPublisher);
        assertThat(agent.run("Кто ты?")).isEqualTo("I am Harness.");
    }

    @Test
    void testOrderOfSystemPrompt() {
        MockChatModel model = MockChatModel.replyingWith("ok");
        Agent agent = new Agent(model, eventPublisher);

        agent.run("Привет");

        List<Message> sent = model.lastPrompt().getInstructions();
        assertThat(sent.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(sent.getFirst().getText()).contains("coding agent");
    }

    @Test
    void testHistoryOfConversation() {
        MockChatModel model = MockChatModel.replyingWith("Привет!", "Тебя зовут Саша.");
        Agent agent = new Agent(model, eventPublisher);

        agent.run("Меня зовут Саша");
        agent.run("Как меня зовут?");

        List<String> secondCall = model.lastPrompt().getInstructions().stream().map(Message::getText).toList();
        assertThat(secondCall).containsExactly(secondCall.getFirst(), "Меня зовут Саша", "Привет!", "Как меня зовут?");
    }
}