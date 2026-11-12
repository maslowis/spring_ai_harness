package org.maslowis.agent.tool;

import org.springframework.ai.util.JsonHelper;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

public abstract class LLMToolBase<I> implements LLMTool<I> {

    public static final int PREVIEW_MAX_LENGTH = 150;
    protected final JsonHelper json = new JsonHelper();

    public static String preview(String content) {
        String firstLine = content.lines().findFirst().orElse("");
        return firstLine.length() <= PREVIEW_MAX_LENGTH ? firstLine : firstLine.substring(0, PREVIEW_MAX_LENGTH) + "...";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String printInput(I input) {
        if (input == null) {
            return "";
        }
        return preview(json.toJson(input));
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(inputType());
    }
}
