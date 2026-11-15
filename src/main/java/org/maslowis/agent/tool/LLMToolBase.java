package org.maslowis.agent.tool;

import org.maslowis.agent.ContentUtils;
import org.springframework.ai.util.JsonHelper;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;

public abstract class LLMToolBase<I> implements LLMTool<I> {

    protected final JsonHelper json = new JsonHelper();

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public String printInput(I input) {
        if (input == null) {
            return "";
        }
        return ContentUtils.preview(json.toJson(input));
    }

    @Override
    public String inputSchema() {
        return JsonSchemaGenerator.generateForType(inputType());
    }
}
