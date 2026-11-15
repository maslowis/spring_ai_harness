package org.maslowis.agent.tool.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.maslowis.agent.tool.guard.WorkspaceGuard;
import org.maslowis.agent.tool.guard.WorkspaceViolationException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GrepToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private WorkspaceGuard workspaceGuard;

    private GrepTool tool;

    @BeforeEach
    void setUp() {
        tool = new GrepTool(workspaceGuard);

        lenient().when(workspaceGuard.root()).thenReturn(tempDir.toAbsolutePath().normalize());
        lenient().when(workspaceGuard.resolve(anyString())).thenAnswer(inv ->
                                                                               tempDir.resolve((String) inv.getArgument(0)).normalize());
        lenient().when(workspaceGuard.relativize(any())).thenAnswer(inv -> {
            Path p = inv.getArgument(0);
            return tempDir.relativize(p).toString().replace("\\", "/");
        });
    }

    // ---------- Метаданные ----------

    @Test
    void name_isGrep() {
        assertThat(tool.name()).isEqualTo("Grep");
    }

    @Test
    void description_mentionsKeyPoints() {
        assertThat(tool.description())
                .contains("Searches inside files")
                .contains("FILES_WITH_MATCHES")
                .contains("CONTENT")
                .contains("COUNT")
                .contains("**/*.java")
                .contains("100");
    }

    @Test
    void inputType_returnsInputRecord() {
        assertThat(tool.inputType()).isEqualTo(GrepTool.Input.class);
    }

    // ---------- Валидация входа ----------

    @Test
    void call_withNullInput_returnsError() {
        var result = tool.call(null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("Search pattern empty");
    }

    @Test
    void call_withEmptyPattern_returnsError() {
        var result = tool.call(new GrepTool.Input("", null, null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("Search pattern empty");
    }

    @Test
    void call_withBlankPattern_returnsError() {
        var result = tool.call(new GrepTool.Input("   ", null, null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("Search pattern empty");
    }

    @Test
    void call_withNullPattern_returnsError() {
        var result = tool.call(new GrepTool.Input(null, null, null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("Search pattern empty");
    }

    // ---------- Ошибки компиляции / пути ----------

    @Test
    void call_withInvalidRegex_returnsCompileError() {
        var result = tool.call(new GrepTool.Input("[", null, null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("Compile pattern error");
    }

    @Test
    void call_whenResolveFails_returnsPathParseError() {
        when(workspaceGuard.resolve("bad"))
                .thenThrow(new WorkspaceViolationException("outside workspace"));

        var result = tool.call(new GrepTool.Input("x", "bad", null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content())
                .contains("Path parse error")
                .contains("outside workspace");
    }

    @Test
    void call_whenPathDoesNotExist_returnsDirectoryNotFound() {
        when(workspaceGuard.resolve("ghost")).thenReturn(tempDir.resolve("ghost"));

        var result = tool.call(new GrepTool.Input("x", "ghost", null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("Directory not found");
    }

    @Test
    void call_whenPathIsFile_returnsDirectoryNotFound() throws IOException {
        var file = tempDir.resolve("file.txt");
        Files.writeString(file, "hi");
        when(workspaceGuard.resolve("file.txt")).thenReturn(file);

        var result = tool.call(new GrepTool.Input("hi", "file.txt", null, null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("Directory not found");
    }

    // ---------- Пустой результат ----------

    @Test
    void call_whenNoMatches_filesMode_returnsNothingFound() {
        var result = tool.call(new GrepTool.Input("nope", null, null,
                                                  GrepTool.OutputMode.FILES_WITH_MATCHES, null));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("No matches found by: nope");
    }

    @Test
    void call_whenNoMatches_contentMode_returnsNothingFound() {
        var result = tool.call(new GrepTool.Input("nope", null, null,
                                                  GrepTool.OutputMode.CONTENT, null));

        assertThat(result.content()).isEqualTo("No matches found by: nope");
    }

    @Test
    void call_whenNoMatches_countMode_returnsNothingFound() {
        var result = tool.call(new GrepTool.Input("nope", null, null,
                                                  GrepTool.OutputMode.COUNT, null));

        assertThat(result.content()).isEqualTo("No matches found by: nope");
    }

    // ---------- FILES_WITH_MATCHES (default) ----------

    @Test
    void call_defaultMode_listsRelativePathsOfMatchingFiles() throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "hello world");
        Files.writeString(tempDir.resolve("B.java"), "nothing here");
        Files.writeString(tempDir.resolve("C.txt"), "hello again");

        var result = tool.call(new GrepTool.Input("hello", null, null, null, null));

        assertThat(result.error()).isFalse();
        assertThat(result.content().split("\n"))
                .containsExactlyInAnyOrder("A.java", "C.txt");
    }

    @Test
    void call_filesMode_searchesNestedFolders() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main"));
        Files.writeString(tempDir.resolve("src/main/App.java"), "target");
        Files.writeString(tempDir.resolve("Root.java"), "target");

        var result = tool.call(new GrepTool.Input("target", null, null,
                                                  GrepTool.OutputMode.FILES_WITH_MATCHES, null));

        assertThat(result.content().split("\n"))
                .containsExactlyInAnyOrder("src/main/App.java", "Root.java");
    }

    // ---------- CONTENT ----------

    @Test
    void call_contentMode_returnsLineNumbersAndContent() throws IOException {
        Files.writeString(tempDir.resolve("A.txt"), "one\nTWO\nthree\n");

        var result = tool.call(new GrepTool.Input("TWO", null, null,
                                                  GrepTool.OutputMode.CONTENT, null));

        assertThat(result.error()).isFalse();
        assertThat(result.content())
                .contains("A.txt")
                .contains("2\tTWO");
    }

    @Test
    void call_contentMode_skipsFilesWithoutMatches() throws IOException {
        Files.writeString(tempDir.resolve("hit.txt"), "needle");
        Files.writeString(tempDir.resolve("miss.txt"), "haystack");

        var result = tool.call(new GrepTool.Input("needle", null, null,
                                                  GrepTool.OutputMode.CONTENT, null));

        assertThat(result.content())
                .contains("hit.txt")
                .doesNotContain("miss.txt");
    }

    @Test
    void call_contentMode_usesRegexFind_returnsMultipleLinesPerFile() throws IOException {
        Files.writeString(tempDir.resolve("A.txt"), "foo\nbar\nfoo foo\n");

        var result = tool.call(new GrepTool.Input("foo", null, null,
                                                  GrepTool.OutputMode.CONTENT, null));

        assertThat(result.content())
                .contains("1\tfoo")
                .contains("3\tfoo foo");
    }

    // ---------- COUNT ----------

    @Test
    void call_countMode_returnsCountsAndSummary() throws IOException {
        Files.writeString(tempDir.resolve("A.txt"), "x\nx\nx\n");
        Files.writeString(tempDir.resolve("B.txt"), "x\n");
        Files.writeString(tempDir.resolve("C.txt"), "nothing");

        var result = tool.call(new GrepTool.Input("x", null, null,
                                                  GrepTool.OutputMode.COUNT, null));

        assertThat(result.error()).isFalse();
        assertThat(result.content())
                .contains("A.txt: 3")
                .contains("B.txt: 1")
                .doesNotContain("C.txt")
                .contains("Found 4 matches in 2 files");
    }

    // ---------- ignoreCase ----------

    @Test
    void call_ignoreCaseTrue_matchesRegardlessOfCase() throws IOException {
        Files.writeString(tempDir.resolve("A.txt"), "HELLO");

        var result = tool.call(new GrepTool.Input("hello", null, null, null, true));

        assertThat(result.content()).isEqualTo("A.txt");
    }

    @Test
    void call_ignoreCaseFalse_isCaseSensitive() throws IOException {
        Files.writeString(tempDir.resolve("A.txt"), "HELLO");

        var result = tool.call(new GrepTool.Input("hello", null, null, null, false));

        assertThat(result.content()).isEqualTo("No matches found by: hello");
    }

    @Test
    void call_ignoreCaseNull_defaultsToCaseSensitive() throws IOException {
        Files.writeString(tempDir.resolve("A.txt"), "HELLO");

        var result = tool.call(new GrepTool.Input("hello", null, null, null, null));

        assertThat(result.content()).isEqualTo("No matches found by: hello");
    }

    // ---------- glob ----------

    @Test
    void call_withGlob_filtersFilesByPattern() throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "target");
        Files.writeString(tempDir.resolve("A.txt"), "target");

        var result = tool.call(new GrepTool.Input("target", null, "**/*.java", null, null));

        assertThat(result.content()).isEqualTo("A.java");
    }

    @Test
    void call_withGlobNoMatches_returnsNothingFound() throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "target");

        var result = tool.call(new GrepTool.Input("target", null, "**/*.kt", null, null));

        assertThat(result.content()).isEqualTo("No matches found by: target");
    }

    // ---------- Лимит ----------

    @Test
    void call_filesMode_limitsToMaxMatches() throws IOException {
        for (int i = 0; i < 105; i++) {
            Files.writeString(tempDir.resolve("f" + i + ".txt"), "hit");
        }

        var result = tool.call(new GrepTool.Input("hit", null, null, null, null));

        assertThat(result.content().split("\n")).hasSize(100);
    }

    // ---------- path указывает в подпапку ----------

    @Test
    void call_withPath_searchesOnlyInThatSubdirectory() throws IOException {
        var sub = Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("Inside.txt"), "needle");
        Files.writeString(tempDir.resolve("Outside.txt"), "needle");

        var result = tool.call(new GrepTool.Input("needle", "sub", null, null, null));

        assertThat(result.content()).isEqualTo("sub/Inside.txt");
    }
}