package org.maslowis.agent.tool.impl;

import lombok.RequiredArgsConstructor;
import org.maslowis.agent.ContentUtils;
import org.maslowis.agent.tool.LLMToolBase;
import org.maslowis.agent.tool.LLMToolContext;
import org.maslowis.agent.tool.LLMToolResult;
import org.maslowis.agent.tool.guard.WorkspaceGuard;
import org.maslowis.agent.tool.guard.WorkspaceViolationException;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class EditTool extends LLMToolBase<EditTool.Input> {

    private final WorkspaceGuard workspaceGuard;
    private final LLMToolContext context;

    @Override
    public String name() {
        return "Edit";
    }

    @Override
    public String description() {
        return """
               Replaces an exact piece of text in a file.
               
               Usage:
               - You MUST use Read on the file first. This tool fails if you did not
               - oldString must match the file exactly, whitespace included
               - The edit FAILS if oldString is not unique in the file. Either provide
                 a larger string with more surrounding context to make it unique,
                 or set replaceAll to change every instance
               - oldString and newString must differ
               """;
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public LLMToolResult call(Input input) {
        if (input == null || ContentUtils.isBlank(input.filePath)) {
            return LLMToolResult.error("File path is empty");
        }

        if (input.oldString == null || input.newString == null) {
            return LLMToolResult.error("oldString or newString must be not empty");
        }

        if (input.oldString.equals(input.newString)) {
            return LLMToolResult.error("oldString and newString must be not equals");
        }

        Path file;
        try {
            file = workspaceGuard.resolve(input.filePath);
        } catch (WorkspaceViolationException e) {
            return LLMToolResult.error("Path parse error: %s".formatted(e.getMessage()));
        }

        if (!Files.exists(file)) {
            return LLMToolResult.error("File not found: " + input.filePath);
        }
        if (Files.isDirectory(file)) {
            return LLMToolResult.error(input.filePath + " — it is folder, it is not file.");
        }

        var notEditable = context.fileStateCache().whyFileNotEditable(file);

        if (notEditable != null) {
            return LLMToolResult.error(notEditable);
        }
        String diskContent;
        try {
            diskContent = Files.readString(file);
        } catch (IOException e) {
            return LLMToolResult.error("File %s read error %s".formatted(input.filePath, e.getMessage()));
        }

        var occurrences = occurrences(diskContent, input.oldString);
        if (occurrences == 0) {
            return LLMToolResult.error("Text '%s' not found in file %s. Repeat read file and specify fragment text for replacement"
                                               .formatted(input.oldString, input.filePath));
        }

        var replaceAll = Boolean.TRUE.equals(input.replaceAll);
        if (occurrences > 1 && !replaceAll) {
            return LLMToolResult.error("Text %s occurs %d times in %s. Add neighboring lines, so the fragment becomes unique, or specify replaceAll"
                                               .formatted(input.oldString, occurrences, input.filePath));
        }

        var newContent = diskContent.replace(input.oldString, input.newString);

        try {
            Files.writeString(file, newContent);
        } catch (IOException e) {
            return LLMToolResult.error("File %s write fail: %s".formatted(input.filePath, e.getMessage()));
        }
        context.fileStateCache().save(file);

        return LLMToolResult.success("Replaced in %s, occurrences: %d".formatted(input.filePath, replaceAll ? occurrences : 1));
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }


    private static int occurrences(String haystack, String needle) {
        if (needle.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + needle.length())) {
            count++;
        }
        return count;
    }


    public record Input(@ToolParam(description = "Path to the file, relative to the project root")
                        String filePath,

                        @ToolParam(description = "Exact text to replace, whitespace included")
                        String oldString,

                        @ToolParam(description = "Text to put in its place")
                        String newString,

                        @ToolParam(required = false, description = "Replace every occurrence instead of failing on duplicates")
                        Boolean replaceAll) {
    }
}
