package org.maslowis.agent.event;

import org.springframework.ai.chat.messages.Message;

import java.time.Duration;
import java.util.List;

public sealed interface AgentEvent {

    record Request(String userMessage) implements AgentEvent {
    }

    record Answer(String assistantMessage) implements AgentEvent {
    }

    record RoundSuccess(int turns, Duration duration) implements AgentEvent {
    }

    record RoundLimitReached(int maxTurns, Duration duration) implements AgentEvent {
    }

    record ToolCallStat(String toolName, String callId, String input) {
    }

    record ToolCallEnd(String toolName, String callId, String preview, boolean error, Duration duration) {
    }

    record CallChatModel(int turn, List<Message> prompt) implements AgentEvent {
    }
}
