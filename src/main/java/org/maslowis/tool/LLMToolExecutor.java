package org.maslowis.tool;

import org.maslowis.agent.event.AgentEvent;
import org.springframework.ai.util.JsonHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class LLMToolExecutor {

    private final ApplicationEventPublisher eventPublisher;
    private final Map<String, LLMTool<?>> tools = new LinkedHashMap<>();
    private final JsonHelper json = new JsonHelper();

    @Autowired
    public LLMToolExecutor(List<LLMTool<?>> tools, ApplicationEventPublisher eventPublisher) {
        for (LLMTool<?> tool : tools) {
            var existed = this.tools.put(tool.name(), tool);
            if (existed != null) {
                throw new IllegalStateException("Tool %s has a duplicates: %s - %s"
                                                        .formatted(tool.name(), existed.getClass(), tool.getClass()));
            }
        }
        this.eventPublisher = eventPublisher;
    }

    public List<LLMTool<?>> tools() {
        return List.copyOf(tools.values());
    }

    public LLMToolResult execute(String toolName, String id, String toolInput) {
        var tool = tools.get(toolName);

        if (tool == null) {
            return LLMToolResult.error("Unknown tool %s. Available tools: %s "
                                               .formatted(toolName, tools.keySet()));
        }
        eventPublisher.publishEvent(new AgentEvent.ToolCallStat(toolName, id, normalizeInput(tool, toolInput)));
        Instant startTime = Instant.now();
        var result = invoke(tool, toolInput);
        eventPublisher.publishEvent(new AgentEvent.ToolCallEnd(toolName,
                                                               id,
                                                               LLMToolResult.preview(result.content(), 200),
                                                               result.error(),
                                                               Duration.between(startTime, Instant.now())));
        return result;
    }

    private <I> String normalizeInput(LLMTool<I> tool, String toolInput) {
        try {
            var normalized = json.fromJson(toolInput, tool.inputType());
            return normalized == null ? "" : tool.printInput(normalized);
        } catch (Exception e) {
            return "Invalid arguments %s for tool %s: %s".formatted(toolInput, tool.name(), e.getMessage());
        }
    }

    private <I> LLMToolResult invoke(LLMTool<I> tool, String args) {
        I input;
        try {
            input = json.fromJson(args, tool.inputType());
        } catch (Exception e) {
            return LLMToolResult.error("Unable to parse arguments for tool %s: %s".formatted(tool.name(), e.getMessage()));
        }

        try {
            return tool.call(input);
        } catch (Exception e) {
            return LLMToolResult.error("Error calling tool %s: %s %s".formatted(tool.name(), e.getClass().getSimpleName(), e.getMessage()));
        }
    }
}
