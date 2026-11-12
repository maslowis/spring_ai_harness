package org.maslowis.agent;

import lombok.RequiredArgsConstructor;
import org.maslowis.agent.event.AgentEvent;
import org.maslowis.agent.tool.LLMToolExecutor;
import org.maslowis.agent.tool.SpringToolCallbackAdapter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class Agent {

    public static final String SYS_PROMPT = """
                                            You are a coding agent that works in a local repository.
                                            You have tools for inspecting the project. Use them instead of guessing:
                                            if the user asks about a file, read it first, then answer from what you read.
                                            Never claim you have seen a file that you did not read with a tool.
                                            Answer concisely and logically!
                                            """;

    private final Conversation conversation = new Conversation(SYS_PROMPT);

    private final ChatModel chatModel;
    private final ApplicationEventPublisher eventPublisher;
    private final AgentProperties agentProperties;
    private final LLMToolExecutor toolExecutor;
    private final SpringToolCallbackAdapter toolCallbackAdapter;
    private final JsonHelper json = new JsonHelper();

    public String run(String userMessage) {
        eventPublisher.publishEvent(new AgentEvent.Request(userMessage));
        var startTime = Instant.now();
        conversation.addUserMessage(userMessage);
        var maxTurns = agentProperties.maxTurns();

        for (var turn = 1; turn <= maxTurns; turn++) {
            var prompt = conversation.toPromptMessages();
            eventPublisher.publishEvent(new AgentEvent.CallChatModel(turn, prompt));
            var assistantMessage = callChatModel(prompt);
            conversation.addAssistantMessage(assistantMessage);
            eventPublisher.publishEvent(new AgentEvent.Answer(assistantMessage.getText()));

            if (!assistantMessage.hasToolCalls()) {
                eventPublisher.publishEvent(new AgentEvent.RoundSuccess(turn, Duration.between(startTime, Instant.now())));
                return assistantMessage.getText();
            }

            var toolResponseMessage = callTools(assistantMessage.getToolCalls());
            conversation.addToolResponseMessage(toolResponseMessage);
        }
        eventPublisher.publishEvent(new AgentEvent.RoundLimitReached(maxTurns, Duration.between(startTime, Instant.now())));

        return "Agent cycle reached max turns = %d".formatted(maxTurns);
    }

    private ToolResponseMessage callTools(List<AssistantMessage.ToolCall> toolCalls) {
        var toolResponses = new ArrayList<ToolResponseMessage.ToolResponse>();

        for (AssistantMessage.ToolCall toolCall : toolCalls) {
            var toolResult = toolExecutor.execute(toolCall.name(), toolCall.id(), toolCall.arguments());
            toolResponses.add(new ToolResponseMessage.ToolResponse(toolCall.id(), toolCall.name(), json.toJson(toolResult)));
        }

        return ToolResponseMessage.builder().responses(toolResponses).build();
    }

    private AssistantMessage callChatModel(List<Message> promptMessages) {
        var prompt = Prompt.builder()
                           .messages(promptMessages)
                           .chatOptions(toolOptions())
                           .build();

        return Optional.ofNullable(chatModel.call(prompt).getResult())
                       .orElseThrow((() -> new RuntimeException("Chat model response is null")))
                       .getOutput();
    }

    private ChatOptions toolOptions() {
        return chatModel.getOptions()
                        .mutate()
                        .combineWith(ToolCallingChatOptions.builder().
                                                           toolCallbacks(toolCallbackAdapter.toolCallbacks()))
                        .build();
    }

}
