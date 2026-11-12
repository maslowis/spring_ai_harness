package org.maslowis.agent.tool.impl;

import lombok.RequiredArgsConstructor;
import org.maslowis.agent.tool.LLMToolBase;
import org.maslowis.agent.tool.LLMToolResult;
import org.maslowis.agent.tool.guard.WorkspaceGuard;
import org.maslowis.agent.tool.guard.WorkspaceViolationException;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ReadTool extends LLMToolBase<ReadTool.Input> {

    public static final int DEFAULT_LIMIT = 2000;
    private static final int MAX_LINE_LENGTH = 2000;

    private final WorkspaceGuard workspaceGuard;

    @Override
    public String name() {
        return "read";
    }

    @Override
    public String description() {
        return """
               Reads a text file from the local filesystem. Use this instead of guessing file contents.
               
               Usage:
               - file_path is relative to the project root
               - Output is in `cat -n` format: line number, tab, then the line content
               - Reads up to %d lines; use offset and limit for larger files
               - Returns an error message if the file does not exist
               """.formatted(DEFAULT_LIMIT);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public LLMToolResult call(Input input) {
        Path pathToFile;
        try {
            pathToFile = workspaceGuard.resolve(input.filePath);
        } catch (WorkspaceViolationException e) {
            return LLMToolResult.error("Path resolve error: %s".formatted(e.getMessage()));
        }
        if (!Files.exists(pathToFile)) {
            return LLMToolResult.error("File not found: %s".formatted(input.filePath));
        }
        if (Files.isDirectory(pathToFile)) {
            return LLMToolResult.error("%s - It is not a text file, it is a folder.".formatted(input.filePath));
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(pathToFile);
        } catch (MalformedInputException e) {
            return LLMToolResult.error("Not a text file: %s".formatted(input.filePath));
        } catch (IOException e) {
            return LLMToolResult.error("Failed to read the file %s: %s".formatted(input.filePath, e.getMessage()));
        }

        if (lines.isEmpty()) {
            return LLMToolResult.success("File is empty");
        }

        var limit = input.limit != null ? Math.max(1, input.limit) : DEFAULT_LIMIT;
        var from = Math.max(1, input.offset != null ? input.offset : 0);
        var to = Math.min(lines.size(), from + limit - 1);

        var text = readAndNormalize(lines, from, to);
        return LLMToolResult.success(text);
    }

    private String readAndNormalize(List<String> lines, int from, int to) {
        StringBuilder output = new StringBuilder();
        for (int i = from; i <= to; i++) {
            var line = lines.get(i - 1);

            if (line.length() > MAX_LINE_LENGTH) {
                line = line.substring(0, MAX_LINE_LENGTH) + "... (line is cut off)";
            }
            output.append(i).append('\t').append(line).append("\n");
        }

        if (to < lines.size()) {
            output.append("... Lines %d to %d are shown. Total lines %d. Use offset to continue reading."
                                  .formatted(from, to, lines.size()));
        }

        return output.toString();
    }

    public record Input(@ToolParam(description = "Path to the file, relative to the project root")
                        String filePath,

                        @ToolParam(required = false, description = "1-based line number to start from")
                        Integer offset,

                        @ToolParam(required = false, description = "How many lines to read, default 2000")
                        Integer limit) {
    }
}
