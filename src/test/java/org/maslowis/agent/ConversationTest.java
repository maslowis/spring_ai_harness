package org.maslowis.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


class ConversationTest {

    public static final String SYSTEM_PROMPT = "You are a helpful assistant.";

    @Test
    void testConversationWithSystemPrompt() {
        Conversation conversation = new Conversation(SYSTEM_PROMPT);
        List<Message> promptMessages = conversation.toPromptMessages();
        assertNotNull(promptMessages);
        assertEquals(1, promptMessages.size());
        assertEquals(SYSTEM_PROMPT, promptMessages.getFirst().getText());
    }

    @Test
    void extracted() {
        Message[] expectedMessages = new Message[]{
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage("Hello"),
                new AssistantMessage("Hi there!")
        };
        Conversation conversation = new Conversation(SYSTEM_PROMPT);
        conversation.addUserMessage("Hello");
        conversation.addAssistantMessage(new AssistantMessage("Hi there!"));

        Message[] actualMessages = conversation.toPromptMessages().toArray(new Message[0]);

        assertEquals(expectedMessages.length, actualMessages.length);
        assertArrayEquals(expectedMessages, actualMessages, "Порядок и типы сообщений не совпадают");
    }

    @Test
    void testMessageOrderInPromptMessages() {
        Conversation conversation = new Conversation(SYSTEM_PROMPT);

        conversation.addUserMessage("Help me understand Java");
        conversation.addAssistantMessage(new AssistantMessage("Java is a powerful object-oriented language."));

        conversation.addUserMessage("What is its memory management?");
        conversation.addAssistantMessage(new AssistantMessage("Java uses automatic garbage collection."));

        var promptMessages = conversation.toPromptMessages();

        assertEquals(5, promptMessages.size());

        int idx = 0;

        Message sysMsg = promptMessages.get(idx);
        assertInstanceOf(SystemMessage.class, sysMsg);
        assertEquals(SYSTEM_PROMPT, sysMsg.getText());

        var firstUser = promptMessages.get(++idx);
        assertInstanceOf(UserMessage.class, firstUser);
        assertEquals("Help me understand Java", firstUser.getText());

        var firstAssistant = promptMessages.get(++idx);
        assertInstanceOf(AssistantMessage.class, firstAssistant);
        assertEquals("Java is a powerful object-oriented language.", firstAssistant.getText());

        var secondUser = promptMessages.get(++idx);
        assertInstanceOf(UserMessage.class, secondUser);
        assertEquals("What is its memory management?", secondUser.getText());

        var secondAssistant = promptMessages.get(++idx);
        assertInstanceOf(AssistantMessage.class, secondAssistant);
        assertEquals("Java uses automatic garbage collection.", secondAssistant.getText());
    }
}