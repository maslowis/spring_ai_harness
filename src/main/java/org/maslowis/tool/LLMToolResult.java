package org.maslowis.tool;

public record LLMToolResult(String content, boolean error) {

    public static LLMToolResult success(String output) {
        return new LLMToolResult(output, false);
    }

    public static LLMToolResult error(String output) {
        return new LLMToolResult(output, true);
    }

    public static String preview(String content, int maxLength) {
        String firstLine = content.lines().findFirst().orElse("");
        return firstLine.length() <= maxLength ? firstLine : firstLine.substring(0, maxLength) + "...";
    }
}
