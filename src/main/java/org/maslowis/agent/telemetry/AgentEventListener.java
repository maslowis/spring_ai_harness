package org.maslowis.agent.telemetry;

import org.maslowis.agent.event.AgentEvent;
import org.maslowis.tool.LLMToolResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.boot.ansi.AnsiColor;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

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
                               .map(m -> {
                                   var result = "";
                                   if (m instanceof UserMessage) {
                                       result = "\t%s: %s".formatted(m.getMessageType(), m.getText());
                                   }
                                   if (m instanceof AssistantMessage) {
                                       var text = m.getText() == null || m.getText().isBlank() ? ((AssistantMessage) m).getToolCalls().stream()
                                                                                                                       .map(t -> t.name() + " " + t.arguments())
                                                                                                                       .collect(Collectors.joining("; "))
                                                                                               : m.getText();
                                       result = "\t%s: %s".formatted(m.getMessageType(), text);
                                   }
                                   if (m instanceof ToolResponseMessage) {
                                       var text = ((ToolResponseMessage) m).getResponses().stream()
                                                                           .map(t -> t.name() + " " + LLMToolResult.preview(t.responseData(), 200))
                                                                           .collect(Collectors.joining("; "));
                                       result = "\t%s: %s".formatted(m.getMessageType(), text);
                                   }
                                   return result;
                               })
                               .collect(Collectors.joining("\n"));
        printSecondLine("TURN: %d".formatted(event.turn()),
                        "sys_prompt: %s\nchat_context:\n%s".formatted(sysPrompt, chatHistory),
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

    private void printFirstLine(String key, String text, AnsiColor color) {
        IO.println(AnsiOutput.toString(color, key));
        text.lines().forEach(line -> IO.println(AnsiOutput.toString(color, ">\t" + line)));
    }

    private void printSecondLine(String key, String text, AnsiColor color) {
        IO.println(AnsiOutput.toString(color, "\t" + key));
        text.lines().forEach(line -> IO.println(AnsiOutput.toString(color, "\t>\t" + line)));
    }
}
