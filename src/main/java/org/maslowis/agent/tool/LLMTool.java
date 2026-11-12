package org.maslowis.agent.tool;

public interface LLMTool<I> {
    String name();

    String description();

    Class<I> inputType();

    LLMToolResult call(I input);

    boolean isReadOnly();

    String printInput(I input);

    String inputSchema();
}
