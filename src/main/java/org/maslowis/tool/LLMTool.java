package org.maslowis.tool;

import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

public interface LLMTool<I> {
    String name();

    String description();

    Class<I> inputType();

    LLMToolResult call(I input);

    default boolean isReadOnly() {
        return true;
    }

    default String printInput(I input) {
        return "";
    }

    default String inputSchema() {
        return JsonSchemaGenerator.generateForType(inputType());
    }

}
