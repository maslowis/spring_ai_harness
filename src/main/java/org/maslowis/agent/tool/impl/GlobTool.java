package org.maslowis.agent.tool.impl;

import lombok.RequiredArgsConstructor;
import org.maslowis.agent.tool.IgnoredFiles;
import org.maslowis.agent.tool.LLMToolBase;
import org.maslowis.agent.tool.LLMToolResult;
import org.maslowis.agent.tool.guard.WorkspaceGuard;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class GlobTool extends LLMToolBase<GlobTool.Input> {

    public static final int MAX_RESULTS = 100;

    private final WorkspaceGuard workspaceGuard;

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String description() {
        return """
               Finds files by name pattern. Use this when you know what a file is called
               but not where it is.
               
               Usage:
               - Supports patterns like "**/*.java" or "**/Tool*.java"
               - Returns paths relative to the project root, most recently modified first
               - Returns at most %d results
               """.formatted(MAX_RESULTS);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public LLMToolResult call(Input input) {
        if (input == null || input.pattern == null || input.pattern.isBlank()) {
            return LLMToolResult.error("File pattern empty");
        }

        Path root;
        try {
            root = input.path == null ? workspaceGuard.root() : workspaceGuard.resolve(input.path);
        } catch (Exception e) {
            return LLMToolResult.error("Path parse error: %s".formatted(e.getMessage()));
        }

        PathMatcher matcher = matcherFor(input.pattern);

        List<Path> found;
        try (Stream<Path> files = Files.walk(root)) {
            found = files.filter(Files::isRegularFile)
                         .filter(file -> IgnoredFiles.notContains(root, file))
                         .filter(file -> matcher.matches(root.relativize(file)))
                         .sorted(Comparator.comparing(GlobTool::modifiedAt).reversed())
                         .limit(MAX_RESULTS + 1)
                         .toList();
        } catch (IOException e) {
            return LLMToolResult.error("Error walk path: %s".formatted(e.getMessage()));
        }

        if (found.isEmpty()) {
            return LLMToolResult.success("files not found by pattern: %s".formatted(input.pattern));
        }

        StringBuilder result = new StringBuilder();
        found.stream()
             .limit(MAX_RESULTS)
             .forEach(f -> result.append(workspaceGuard.relativize(f)).append("\n"));
        if (found.size() > MAX_RESULTS) {
            result.append("showed first %d files, specify pattern for less output".formatted(MAX_RESULTS));
        }

        return LLMToolResult.success(result.toString());
    }

    public record Input(
            @ToolParam(description = "Glob pattern, for example **/*.java or **/Tool*.java")
            String pattern,

            @ToolParam(required = false, description = "Directory to search in, defaults to project root")
            String path
    ) {
    }

    static PathMatcher matcherFor(String pattern) {
        PathMatcher asWritten = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        if (!pattern.startsWith("**/")) {
            return asWritten;
        }
        PathMatcher atRoot = FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(3));
        return path -> asWritten.matches(path) || atRoot.matches(path);
    }


    private static long modifiedAt(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
