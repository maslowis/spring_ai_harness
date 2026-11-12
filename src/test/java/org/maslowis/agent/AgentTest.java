package org.maslowis.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.maslowis.agent.event.AgentEvent;
import org.maslowis.agent.tool.LLMToolExecutor;
import org.maslowis.agent.tool.LLMToolResult;
import org.maslowis.agent.tool.SpringToolCallbackAdapter;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AgentProperties agentProperties;

    @Mock
    private LLMToolExecutor toolExecutor;

    @Mock
    private SpringToolCallbackAdapter toolCallbackAdapter;

    @Mock
    private Conversation conversation;

    @Mock
    private ChatOptions chatOptions;

    @Mock
    private ChatOptions.Builder<?> chatOptionsBuilder;

    private Agent agent;

    @BeforeEach
    void setUp() {
        agent = new Agent(
                chatModel,
                eventPublisher,
                agentProperties,
                toolExecutor,
                toolCallbackAdapter
        );

        // Conversation is created internally by Agent, so replace it with a mock.
        ReflectionTestUtils.setField(agent, "conversation", conversation);

        // Stub the chat-options chain that Agent.toolOptions() builds.
        when(chatModel.getOptions()).thenReturn(chatOptions);
        when(chatOptions.mutate()).thenAnswer(_ -> chatOptionsBuilder);
        when(chatOptionsBuilder.combineWith(any())).thenAnswer(_ ->  chatOptionsBuilder);
        when(chatOptionsBuilder.build()).thenReturn(chatOptions);

        when(toolCallbackAdapter.toolCallbacks()).thenReturn(List.of());
        when(conversation.toPromptMessages()).thenReturn(List.of());
    }

    @Test
    void runReturnsAssistantTextWhenNoToolCalls() {
        when(agentProperties.maxTurns()).thenReturn(3);

        AssistantMessage assistantMessage = assistantMessage("Hello", false, null);
        ChatResponse response = chatResponse(assistantMessage);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        String result = agent.run("Hi");

        assertEquals("Hello", result);
        verify(conversation).addUserMessage("Hi");
        verify(conversation).addAssistantMessage(assistantMessage);
        verify(conversation, never()).addToolResponseMessage(any(ToolResponseMessage.class));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(4)).publishEvent(eventCaptor.capture());

        List<Object> events = eventCaptor.getAllValues();
        assertInstanceOf(AgentEvent.Request.class, events.get(0));
        assertInstanceOf(AgentEvent.CallChatModel.class, events.get(1));
        assertInstanceOf(AgentEvent.Answer.class, events.get(2));
        assertInstanceOf(AgentEvent.RoundSuccess.class, events.get(3));
    }

    @Test
    void runExecutesToolsAndContinuesUntilFinalAnswer() {
        when(agentProperties.maxTurns()).thenReturn(3);

        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-1",
                "function",
                "readFile",
                "{\"path\":\"file.txt\"}"
        );

        AssistantMessage toolCallMessage = assistantMessage("", true, List.of(toolCall));
        AssistantMessage finalMessage = assistantMessage("Done", false, null);

        ChatResponse firstResponse = chatResponse(toolCallMessage);
        ChatResponse secondResponse = chatResponse(finalMessage);
        when(chatModel.call(any(Prompt.class))).thenReturn(firstResponse, secondResponse);

        LLMToolResult toolResult = mock(LLMToolResult.class);
        when(toolExecutor.execute("readFile", "call-1", "{\"path\":\"file.txt\"}"))
                .thenReturn(toolResult);

        String result = agent.run("Read file");

        assertEquals("Done", result);
        verify(toolExecutor).execute("readFile", "call-1", "{\"path\":\"file.txt\"}");
        verify(conversation).addAssistantMessage(toolCallMessage);
        verify(conversation).addAssistantMessage(finalMessage);

        ArgumentCaptor<ToolResponseMessage> toolResponseCaptor =
                ArgumentCaptor.forClass(ToolResponseMessage.class);
        verify(conversation).addToolResponseMessage(toolResponseCaptor.capture());

        ToolResponseMessage toolResponseMessage = toolResponseCaptor.getValue();
        assertEquals(1, toolResponseMessage.getResponses().size());
        assertEquals("call-1", toolResponseMessage.getResponses().getFirst().id());
        assertEquals("readFile", toolResponseMessage.getResponses().getFirst().name());

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(6)).publishEvent(eventCaptor.capture());

        List<Object> events = eventCaptor.getAllValues();
        assertInstanceOf(AgentEvent.Request.class, events.get(0));
        assertInstanceOf(AgentEvent.CallChatModel.class, events.get(1));
        assertInstanceOf(AgentEvent.Answer.class, events.get(2));
        assertInstanceOf(AgentEvent.CallChatModel.class, events.get(3));
        assertInstanceOf(AgentEvent.Answer.class, events.get(4));
        assertInstanceOf(AgentEvent.RoundSuccess.class, events.get(5));
    }

    @Test
    void runStopsAtMaxTurnsWhenModelKeepsCallingTools() {
        when(agentProperties.maxTurns()).thenReturn(2);

        AssistantMessage.ToolCall toolCall = new AssistantMessage.ToolCall(
                "call-1",
                "function",
                "readFile",
                "{}"
        );

        AssistantMessage toolCallMessage = assistantMessage("", true, List.of(toolCall));
        ChatResponse response = chatResponse(toolCallMessage);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        LLMToolResult toolResult = mock(LLMToolResult.class);
        when(toolExecutor.execute(anyString(), anyString(), anyString())).thenReturn(toolResult);

        String result = agent.run("Loop");

        assertEquals("Agent cycle reached max turns = 2", result);
        verify(chatModel, times(2)).call(any(Prompt.class));
        verify(toolExecutor, times(2)).execute(anyString(), anyString(), anyString());
        verify(conversation, times(2)).addToolResponseMessage(any(ToolResponseMessage.class));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(6)).publishEvent(eventCaptor.capture());

        List<Object> events = eventCaptor.getAllValues();
        assertInstanceOf(AgentEvent.Request.class, events.get(0));
        assertInstanceOf(AgentEvent.CallChatModel.class, events.get(1));
        assertInstanceOf(AgentEvent.Answer.class, events.get(2));
        assertInstanceOf(AgentEvent.CallChatModel.class, events.get(3));
        assertInstanceOf(AgentEvent.Answer.class, events.get(4));
        assertInstanceOf(AgentEvent.RoundLimitReached.class, events.get(5));
    }

    @Test
    void runThrowsWhenChatModelResultIsNull() {
        when(agentProperties.maxTurns()).thenReturn(3);

        ChatResponse response = mock(ChatResponse.class);
        when(response.getResult()).thenReturn(null);
        when(chatModel.call(any(Prompt.class))).thenReturn(response);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> agent.run("Hi")
                                                 );

        assertEquals("Chat model response is null", exception.getMessage());
        verify(conversation).addUserMessage("Hi");

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());

        List<Object> events = eventCaptor.getAllValues();
        assertInstanceOf(AgentEvent.Request.class, events.get(0));
        assertInstanceOf(AgentEvent.CallChatModel.class, events.get(1));
    }

    private static AssistantMessage assistantMessage(
            String text,
            boolean hasToolCalls,
            List<AssistantMessage.ToolCall> toolCalls
                                                    ) {
        AssistantMessage message = mock(AssistantMessage.class);
        when(message.getText()).thenReturn(text);
        when(message.hasToolCalls()).thenReturn(hasToolCalls);

        if (hasToolCalls) {
            when(message.getToolCalls()).thenReturn(toolCalls);
        }

        return message;
    }

    private static ChatResponse chatResponse(AssistantMessage assistantMessage) {
        ChatResponse response = mock(ChatResponse.class);
        Generation generation = mock(Generation.class);

        when(response.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);

        return response;
    }
}