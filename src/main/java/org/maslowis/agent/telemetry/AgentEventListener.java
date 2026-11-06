package org.maslowis.agent.telemetry;

import org.maslowis.agent.event.AgentEvent;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AgentEventListener {

    @EventListener(AgentEvent.Request.class)
    public void onAgentRequest(AgentEvent.Request event) {
        String userInput = "REQUEST: " + event.userMessage();
        IO.println(AnsiOutput.toString(AnsiColor.GREEN, userInput));
    }

    @EventListener(AgentEvent.Answer.class)
    public void onAgentAnswer(AgentEvent.Answer event) {
        String assistant = "LLM: " + event.assistantMessage();
        for (String line : assistant.split("\n")) {
            IO.println(AnsiOutput.toString(AnsiColor.YELLOW, line));
        }
    }

    @EventListener(AgentEvent.Round.class)
    public void onAgentRound(AgentEvent.Round event) {
        String duration = "ROUND: " + event.duration().toMillis() + " ms\n";
        IO.println(AnsiOutput.toString(AnsiColor.BLUE, duration));
    }
}
