package org.maslowis.agent.event;

import org.maslowis.agent.console.Printer;
import org.maslowis.agent.tool.LLMToolBase;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

import static org.maslowis.agent.console.Printer.*;

@Component
public class AgentEventListener {

    @EventListener(AgentEvent.Request.class)
    public void onAgentRequest(AgentEvent.Request event) {
        printFirstLine("REQUEST:", event.userMessage(), AnsiColor.BLUE);
    }

    @EventListener(AgentEvent.CallChatModel.class)
    public void onCallChatModel(AgentEvent.CallChatModel event) {
        printFirstLine("CALL MODEL:", "", AnsiColor.BLUE);
        var sysPrompt = event.prompt()
                             .stream()
                             .filter(m -> m.getMessageType() == MessageType.SYSTEM)
                             .map(m -> "%s".formatted(m.getText()))
                             .collect(Collectors.joining("\n"));
        var chatHistory = event.prompt()
                               .stream()
                               .filter(m -> m.getMessageType() != MessageType.SYSTEM)
                               .map(Printer::extractText)
                               .toList();
        var tokens = estimate(sysPrompt + String.join("\n", chatHistory));

        printSecondLine("TURN: %d".formatted(event.turn()),
                        "TOKEN: ~%d\nSYSTEM_PROMPT:\n%sCHAT_HISTORY:\n%s".formatted(tokens, sysPrompt, chatHistory.stream()
                                                                                                                .map(LLMToolBase::preview)
                                                                                                                .collect(Collectors.joining("\n"))),
                        AnsiColor.WHITE);
    }

    @EventListener(AgentEvent.Answer.class)
    public void onAgentAnswer(AgentEvent.Answer event) {
        printFirstLine("LLM:", event.assistantMessage(), AnsiColor.MAGENTA);
    }

    @EventListener(AgentEvent.RoundSuccess.class)
    public void onAgentRoundEnd(AgentEvent.RoundSuccess event) {
        printFirstLine("ROUND: finished", "%d turns in %d ms\n".formatted(event.turns(), event.duration().toMillis()), AnsiColor.GREEN);
    }

    @EventListener(AgentEvent.RoundLimitReached.class)
    public void onAgentRoundFailed(AgentEvent.RoundLimitReached event) {
        printFirstLine("ROUND: failed",
                       "reached max tuns %d in %d ms".formatted(event.maxTurns(), event.duration().toMillis()),
                       AnsiColor.RED);

    }

    @EventListener(AgentEvent.ToolCallStat.class)
    public void onAgentToolCallStat(AgentEvent.ToolCallStat event) {
        printSecondLine("TOOL: %s".formatted(event.toolName()), "call_id: %s\ninput: %s".formatted(event.callId(), event.input()), AnsiColor.CYAN);
    }

    @EventListener(AgentEvent.ToolCallEnd.class)
    public void onAgentToolCallEnd(AgentEvent.ToolCallEnd event) {
        printSecondLine("TOOL: %s".formatted(event.toolName()),
                        "call_id: %s\nout: %s\nin %d ms".formatted(event.callId(), event.preview(), event.duration().toMillis()),
                        event.error() ? AnsiColor.RED : AnsiColor.YELLOW);

    }
}
