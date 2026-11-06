//package org.maslowis.agent;
//
//import org.junit.jupiter.api.Test;
//import org.springframework.ai.chat.model.ChatModel;
//import org.springframework.ai.chat.model.ChatResponse;
//import org.springframework.ai.chat.messages.AssistantMessage;
//import org.springframework.ai.chat.messages.Message;
//
//import java.util.Collections;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//class AgentTest {
//
//    private ChatModel chatModel;
//    private Conversation conversation;
//    private String sysPrompt = "You are a coding assistant.";
//
//    @BeforeEach
//    void setUp() {
//        this.chatModel = new MockChatModel();
//        this.conversation = new Conversation(sysPrompt);
//    }
//
//    @Test
//    void testRunWithSimpleMessage() {
//        // Создаем новый Agent с моком для каждого теста (для изоляции)
//        var agent = createAgent(this.chatModel, this.conversation);
//
//        String input = "Hello, I need help with Java";
//        String expectedOutput = "I can help you with Java. What specifically do you need?";
//
//        assertDoesNotThrow(() -> {
//            String result = agent.run(input);
//            assertNotNull(result);
//        });
//
//        // Проверяем, что ответ был получен
//        assertTrue(result.contains("Java"), "Ответ должен содержать 'Java'");
//    }
//
//    @Test
//    void testRunWithMultipleMessages() {
//        Conversation conversation = new Conversation(sysPrompt);
//
//        String input1 = "What is the capital of France?";
//        String output1 = "The capital of France is Paris.";
//
//        String input2 = "What language is Spring Boot written in?";
//        String output2 = "Spring Boot is written in Java.";
//
//        // Добавляем первое сообщение
//        conversation.addUserMessage(input1);
//        var chatModel = new MockChatModel(Collections.singletonList(new ChatResponse(new AssistantMessage(output1, "qwen3.5:4b", null))));
//        var agent1 = createAgent(chatModel, conversation);
//
//        assertDoesNotThrow(() -> {
//            String result = agent1.run(input1);
//            assertNotNull(result);
//        });
//
//        // Добавляем второе сообщение (в то же самое контекст)
//        conversation.addUserMessage(input2);
//        var chatModel2 = new MockChatModel(Collections.singletonList(new ChatResponse(new AssistantMessage(output2, "qwen3.5:4b", null))));
//        var agent2 = createAgent(chatModel2, conversation);
//
//        assertDoesNotThrow(() -> {
//            String result = agent2.run(input2);
//            assertNotNull(result);
//        });
//    }
//
//    @Test
//    void testRunReturnsNullThrowsException() {
//        ChatModel mockChatModel = new MockChatModel(Collections.singletonList(null)); // Возвращает null
//
//        var agent = createAgent(mockChatModel, conversation);
//
//        String input = "Test";
//
//        assertThrows(RuntimeException.class, () -> {
//            agent.run(input);
//        });
//    }
//
//    private static Agent createAgent(ChatModel chatModel, Conversation conversation) {
//        return new org.maslowis.agent.Agent(conversation, chatModel);
//    }
//
//    private static class MockChatModel implements ChatModel {
//        private final java.util.List<ChatResponse> responses = new java.util.ArrayList<>();
//
//        public MockChatModel(java.util.List<ChatResponse> responses) {
//            this.responses.addAll(responses);
//        }
//
//        @Override
//        public ChatResponse call() {
//            if (responses.isEmpty()) {
//                throw new IllegalStateException("No mock responses available");
//            }
//            return responses.remove(0);
//        }
//
//        @Override
//        public ChatResponse call(org.springframework.ai.chat.prompt.Prompt prompt) {
//            return call();
//        }
//
//        @Override
//        public <T> T callWithCallback() {
//            throw new UnsupportedOperationException("Not supported");
//        }
//
//        @Override
//        public <T> T callWithCallback(org.springframework.ai.chat.prompt.Prompt prompt) {
//            throw new UnsupportedOperationException("Not supported");
//        }
//
//        @Override
//        public void close() {
//            // no-op
//        }
//    }
//}