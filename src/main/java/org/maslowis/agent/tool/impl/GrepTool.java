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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class GrepTool extends LLMToolBase<GrepTool.Input> {
    private static final int MAX_MATCHES = 100;

    private final WorkspaceGuard workspaceGuard;

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return """
               Searches inside files with a regular expression. Use this when you know
               what the code says but not which file says it.
               
               Usage:
               - Output modes: FILES_WITH_MATCHES lists paths (default), CONTENT shows matching
                 lines with line numbers, COUNT shows how many matches per file
               - Narrow the search with glob, for example **/*.java
               - Prefer FILES_WITH_MATCHES first, then read the interesting file with Read
               - Returns at most %d matches
               """.formatted(MAX_MATCHES);
    }

    @Override
    public Class<Input> inputType() {
        return Input.class;
    }

    @Override
    public LLMToolResult call(Input input) {
        if (input == null || input.pattern == null || input.pattern.isBlank()) {
            return LLMToolResult.error("Search pattern empty");
        }

        Pattern regex;
        try {
            regex = Pattern.compile(input.pattern, Boolean.TRUE == input.ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
        } catch (Exception e) {
            return LLMToolResult.error("Compile pattern error: %s".formatted(e.getMessage()));
        }

        Path root;
        try {
            root = input.path == null ? workspaceGuard.root() : workspaceGuard.resolve(input.path);
        } catch (Exception e) {
            return LLMToolResult.error("Path parse error: %s".formatted(e.getMessage()));
        }

        if (!Files.isDirectory(root)) {
            return LLMToolResult.error("Directory not found: %s".formatted(input.path));
        }
        PathMatcher pathMatcher = input.glob == null ? null : GlobTool.matcherFor(input.glob);

        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                        .filter(file -> IgnoredFiles.notContains(root, file))
                        .filter(file -> pathMatcher == null || pathMatcher.matches(root.relativize(file)))
                        .toList();
        } catch (IOException e) {
            return LLMToolResult.error("Error walk path: %s".formatted(e.getMessage()));
        }

        OutputMode mode = input.outputMode == null ? OutputMode.FILES_WITH_MATCHES : input.outputMode;

        return switch (mode) {
            case FILES_WITH_MATCHES -> renderFiles(files, regex, input.pattern);
            case COUNT -> renderCounts(files, regex, input.pattern);
            case CONTENT -> renderContent(files, regex, input.pattern);
        };
    }

    private LLMToolResult renderContent(List<Path> files, Pattern regex, String pattern) {
        AtomicInteger count = new AtomicInteger(0);
        var matches = files.parallelStream()
                           .map(file -> {
                               var matchesLine = new StringBuilder();
                               var lines = lines(file);
                               for (int i = 0; i < lines.size(); i++) {
                                   if (count.get() >= MAX_MATCHES) {
                                       break;
                                   }
                                   var line = lines.get(i);
                                   if (regex.matcher(line).find()) {
                                       var strip = preview(line.strip());
                                       matchesLine.append(i + 1).append("\t").append(strip).append("\n");
                                       count.incrementAndGet();
                                   }
                               }
                               if (!matchesLine.isEmpty()) {
                                   return "%s\n%s".formatted(workspaceGuard.relativize(file), matchesLine.toString());
                               } else {
                                   return null;
                               }
                           })
                           .filter(Objects::nonNull)
                           .toList();

        if (matches.isEmpty()) {
            return nothingFound(pattern);
        }

        return LLMToolResult.success(String.join("\n", matches));
    }

    private LLMToolResult renderCounts(List<Path> files, Pattern regex, String pattern) {
        LongAccumulator total = new LongAccumulator(Long::sum, 0L);
        List<String> matches = files.parallelStream()
                                    .map(file -> {
                                        var count = lines(file).parallelStream()
                                                               .filter(line -> regex.matcher(line).find()).count();
                                        if (count > 0L) {
                                            total.accumulate(count);
                                            return "%s: %d".formatted(workspaceGuard.relativize(file), count);
                                        } else {
                                            return null;
                                        }
                                    })
                                    .filter(Objects::nonNull)
                                    .limit(MAX_MATCHES)
                                    .toList();
        if (matches.isEmpty()) {
            return nothingFound(pattern);
        }
        return LLMToolResult.success(String.join(System.lineSeparator(), matches) +
                                     "\n\nFound %d matches in %d files".formatted(total.get(), matches.size()));
    }

    private LLMToolResult renderFiles(List<Path> files, Pattern regex, String pattern) {
        List<String> found = files.parallelStream()
                                  .filter(file -> lines(file).parallelStream()
                                                             .anyMatch(line -> regex.matcher(line).find()))
                                  .map(workspaceGuard::relativize)
                                  .limit(MAX_MATCHES)
                                  .toList();
        if (found.isEmpty()) {
            return nothingFound(pattern);
        }

        return LLMToolResult.success(String.join("\n", found));
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException cannotRead) {
            return List.of();
        }
    }

    private static LLMToolResult nothingFound(String pattern) {
        return LLMToolResult.success("No matches found by: %s".formatted(pattern));
    }

    public record Input(@ToolParam(description = "Regular expression to search for")
                        String pattern,

                        @ToolParam(required = false, description = "Directory to search in, defaults to project root")
                        String path,

                        @ToolParam(required = false, description = "Only search files matching this glob, e.g. **/*.java")
                        String glob,

                        @ToolParam(required = false, description = "FILES_WITH_MATCHES lists paths (default), " +
                                                                   "CONTENT shows matching lines, " +
                                                                   "COUNT shows match counts")
                        OutputMode outputMode,

                        @ToolParam(required = false, description = "Case insensitive search")
                        Boolean ignoreCase) {

    }

    public enum OutputMode {
        FILES_WITH_MATCHES,
        CONTENT,
        COUNT
    }
}
