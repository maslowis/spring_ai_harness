package org.maslowis.tool;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class SpringToolCallbackAdapter {

    private final LLMToolExecutor toolExecutor;

    public List<ToolCallback> toolCallbacks() {
        return toolExecutor.tools().stream().map(SpringToolCallbackAdapter::toSpringToolCallback).toList();
    }

    private static ToolCallback toSpringToolCallback(LLMTool<?> tool) {
        var toolDefinition = ToolDefinition.builder()
                                           .name(tool.name())
                                           .description(tool.description())
                                           .inputSchema(tool.inputSchema())
                                           .build();

        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return toolDefinition;
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException("Tool call must be an instance of org.maslowis.tool.LLMTool");
            }
        };
    }
}
