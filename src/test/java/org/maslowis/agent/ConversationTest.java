package org.maslowis.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import static org.junit.jupiter.api.Assertions.*;


class ConversationTest {

    @Test
    void testConversationWithSystemPromptAndMessageOrder() {
        String systemPrompt = "You are a helpful assistant.";
        Conversation conversation = new Conversation(systemPrompt);

        // Проверяем системный промпт
        assertNotNull(conversation.getSystemMessage());
        assertEquals("You are a helpful assistant.", conversation.getSystemMessage().getText());

        Message[] expectedMessages = new Message[] {
                new SystemMessage("You are a helpful assistant."),
                new UserMessage("Hello"),
                new AssistantMessage("Hi there!")
        };

        // Добавляем сообщения пользователя и ассистента
        conversation.addUserMessage("Hello");
        conversation.addAssistantMessage(new AssistantMessage("Hi there!"));

        Message[] actualMessages = conversation.toPromptMessages().toArray(new Message[0]);
        
        assertEquals(expectedMessages.length, actualMessages.length);
        
        // Проверяем порядок сообщений
        assertArrayEquals(expectedMessages, actualMessages, "Порядок и типы сообщений не совпадают");
    }

    @Test
    void testMessageOrderInPromptMessages() {
        String systemPrompt = "You are a coding assistant.";
        Conversation conversation = new Conversation(systemPrompt);

        // Добавляем сообщение пользователя
        conversation.addUserMessage("What is the capital of France?");
        // Добавляем ответ модели
        conversation.addAssistantMessage(new AssistantMessage("Paris"));

        // Проверяем формат сообщений для prompting
        var promptMessages = conversation.toPromptMessages();

        // 1. Сначала должен быть системный промпт
        assertTrue(promptMessages.get(0) instanceof SystemMessage);
        assertEquals("You are a coding assistant.", promptMessages.get(0).getText());

        // 2. Потом сообщение пользователя
        var userMsg = (UserMessage) promptMessages.get(1);
        assertEquals("What is the capital of France?", userMsg.getText());

        // 3. И ответ ассистента
        var assistantMsg = (AssistantMessage) promptMessages.get(2);
        assertEquals("Paris", assistantMsg.getText());
    }

    @Test
    void testComplexConversation() {
        String systemPrompt = "You are a helpful coding agent.";
        Conversation conversation = new Conversation(systemPrompt);

        // Добавляем несколько раундов диалога
        conversation.addUserMessage("Help me understand Java");
        conversation.addAssistantMessage(new AssistantMessage("Java is a powerful object-oriented language."));

        conversation.addUserMessage("What is its memory management?");
        conversation.addAssistantMessage(new AssistantMessage("Java uses automatic garbage collection."));

        // Проверяем полный порядок в promptMessages
        var promptMessages = conversation.toPromptMessages();

        assertEquals(5, promptMessages.size(), "Системный промпт + 2 раунда диалога (4 сообщения)");

        int idx = 0;
        
        // Системный промпт всегда первый
        assertTrue(promptMessages.get(idx) instanceof SystemMessage);

        // Первый раунд: пользователь
        var firstUser = (UserMessage) promptMessages.get(++idx);
        assertEquals("Help me understand Java", firstUser.getText());

        // Первый раунд: ассистент
        var firstAssistant = (AssistantMessage) promptMessages.get(++idx);
        assertTrue(firstAssistant.getText().contains("Java is a powerful object-oriented language"));

        // Второй раунд: пользователь
        var secondUser = (UserMessage) promptMessages.get(++idx);
        assertEquals("What is its memory management?", secondUser.getText());

        // Второй раунд: ассистент
        var secondAssistant = (AssistantMessage) promptMessages.get(++idx);
        assertTrue(secondAssistant.getText().contains("Java uses automatic garbage collection"));
    }
}