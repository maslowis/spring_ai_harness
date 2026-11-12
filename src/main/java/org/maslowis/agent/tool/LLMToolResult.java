package org.maslowis.agent.tool;

public record LLMToolResult(String content, boolean error) {

    public static LLMToolResult success(String output) {
        return new LLMToolResult(output, false);
    }

    public static LLMToolResult error(String output) {
        return new LLMToolResult(output, true);
    }
}
