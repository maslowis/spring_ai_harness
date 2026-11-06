package org.maslowis.agent.event;

import java.time.Duration;

public sealed interface AgentEvent {

    record Request(String userMessage) implements AgentEvent {
    }

    record Answer(String assistantMessage) implements AgentEvent {
    }

    record Round(Duration duration) implements AgentEvent {
    }
}
