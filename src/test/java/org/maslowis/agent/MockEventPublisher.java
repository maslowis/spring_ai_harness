package org.maslowis.agent;

import org.maslowis.agent.event.AgentEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

public class MockEventPublisher implements ApplicationEventPublisher {
    private final List<AgentEvent> events = new ArrayList<>();

    @Override
    public void publishEvent(Object event) {
        if (event instanceof AgentEvent) {
            events.add((AgentEvent) event);
        }
    }

    List<AgentEvent> events() {
        return List.copyOf(events);
    }
}
