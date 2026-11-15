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
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private WorkspaceGuard workspaceGuard;

    private GlobTool tool;

    @BeforeEach
    void setUp() {
        tool = new GlobTool(workspaceGuard);

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
    void name_isGlob() {
        assertThat(tool.name()).isEqualTo("Glob");
    }

    @Test
    void description_mentionsKeyPoints() {
        assertThat(tool.description())
                .contains("Finds files by name pattern")
                .contains("most recently modified first")
                .contains("at most 100 results");
    }

    @Test
    void inputType_returnsInputRecord() {
        assertThat(tool.inputType()).isEqualTo(GlobTool.Input.class);
    }

    // ---------- Валидация входа ----------

    @Test
    void call_withNullInput_returnsError() {
        var result = tool.call(null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("File pattern empty");
    }

    @Test
    void call_withEmptyPattern_returnsError() {
        var result = tool.call(new GlobTool.Input("", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("File pattern empty");
    }

    @Test
    void call_withBlankPattern_returnsError() {
        var result = tool.call(new GlobTool.Input("   ", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("File pattern empty");
    }

    @Test
    void call_withNullPattern_returnsError() {
        var result = tool.call(new GlobTool.Input(null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("File pattern empty");
    }

    // ---------- Ошибки resolve / walk ----------

    @Test
    void call_whenResolveFails_returnsParseError() {
        when(workspaceGuard.resolve("bad"))
                .thenThrow(new WorkspaceViolationException("outside workspace"));

        var result = tool.call(new GlobTool.Input("*.java", "bad"));

        assertThat(result.error()).isTrue();
        assertThat(result.content())
                .contains("Path parse error")
                .contains("outside workspace");
    }

    @Test
    void call_whenRootDoesNotExist_returnsWalkError() {
        when(workspaceGuard.resolve("phantom")).thenReturn(tempDir.resolve("phantom"));

        var result = tool.call(new GlobTool.Input("*.java", "phantom"));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("Error walk path");
    }

    // ---------- Успех / нет совпадений ----------

    @Test
    void call_whenNoMatches_returnsNotFoundMessage() {
        var result = tool.call(new GlobTool.Input("*.xyz", null));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("Files not found by pattern: *.xyz");
    }

    @Test
    void call_matchesFilesAtRoot() throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "");
        Files.writeString(tempDir.resolve("B.txt"), "");

        var result = tool.call(new GlobTool.Input("*.java", null));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("A.java\n");
    }

    @Test
    void call_matchesNestedFilesWithDoubleStar() throws IOException {
        Files.createDirectories(tempDir.resolve("src/main/java"));
        Files.writeString(tempDir.resolve("src/main/java/Main.java"), "");
        Files.writeString(tempDir.resolve("Root.java"), "");

        var result = tool.call(new GlobTool.Input("**/*.java", null));

        assertThat(result.content())
                .contains("src/main/java/Main.java")
                .contains("Root.java");
    }

    @Test
    void call_withPath_searchesOnlyInThatPath() throws IOException {
        var sub = Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("A.java"), "");
        Files.writeString(tempDir.resolve("B.java"), "");

        var result = tool.call(new GlobTool.Input("*.java", "sub"));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("sub/A.java\n");
    }

    @Test
    void call_sortsByModifiedTimeDescending() throws IOException {
        var older = tempDir.resolve("older.txt");
        var newer = tempDir.resolve("newer.txt");
        var middle = tempDir.resolve("middle.txt");
        Files.writeString(older, "");
        Files.writeString(newer, "");
        Files.writeString(middle, "");
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000_000_000L));
        Files.setLastModifiedTime(middle, FileTime.fromMillis(2_000_000_000L));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(3_000_000_000L));

        var result = tool.call(new GlobTool.Input("*.txt", null));

        assertThat(result.content()).isEqualTo("newer.txt\nmiddle.txt\nolder.txt\n");
    }

    @Test
    void call_limitsToMaxResultsAndAppendsHint() throws IOException {
        for (int i = 0; i < GlobTool.MAX_RESULTS + 5; i++) {
            Files.writeString(tempDir.resolve(String.format("f%03d.txt", i)), "");
        }

        var result = tool.call(new GlobTool.Input("*.txt", null));

        assertThat(result.content())
                .contains("Showed first 100 files, specify pattern for less output");
        // 100 путей + 1 строка-подсказка
        assertThat(result.content().split("\n")).hasSize(GlobTool.MAX_RESULTS + 1);
    }

    @Test
    void call_doesNotAppendHintWhenUnderLimit() throws IOException {
        for (int i = 0; i < 3; i++) {
            Files.writeString(tempDir.resolve("f" + i + ".txt"), "");
        }

        var result = tool.call(new GlobTool.Input("*.txt", null));

        assertThat(result.content())
                .doesNotContain("Showed first")
                .contains("f0.txt")
                .contains("f1.txt")
                .contains("f2.txt");
    }

    // ---------- Зависит от реализации IgnoredFiles ----------

    /**
     * Проверяет, что файлы в «игнорируемых» каталогах отсекаются.
     * Предполагается, что {@code IgnoredFiles} исключает {@code .git}.
     * Если у вас другой набор — замените {@code .git} на нужный путь.
     */
    @Test
    void call_skipsIgnoredFiles() throws IOException {
        var git = Files.createDirectories(tempDir.resolve(".git"));
        Files.writeString(git.resolve("config.txt"), "");
        Files.writeString(tempDir.resolve("visible.txt"), "");

        var result = tool.call(new GlobTool.Input("**/*.txt", null));

        assertThat(result.content())
                .contains("visible.txt")
                .doesNotContain(".git/config.txt");
    }

    // ---------- matcherFor() ----------

    @Test
    void matcherFor_plainPattern_matchesOnlyRootLevel() {
        var m = GlobTool.matcherFor("*.java");

        assertThat(m.matches(Path.of("Main.java"))).isTrue();
        assertThat(m.matches(Path.of("src/Main.java"))).isFalse();
        assertThat(m.matches(Path.of("Main.txt"))).isFalse();
    }

    @Test
    void matcherFor_doubleStarPrefix_matchesBothRootAndNested() {
        var m = GlobTool.matcherFor("**/*.java");

        assertThat(m.matches(Path.of("Root.java"))).isTrue();
        assertThat(m.matches(Path.of("a/b/C.java"))).isTrue();
        assertThat(m.matches(Path.of("Main.txt"))).isFalse();
    }

    @Test
    void matcherFor_doubleStarSuffix_matchesFilesAtAnyDepth() {
        var m = GlobTool.matcherFor("**/Tool*.java");

        assertThat(m.matches(Path.of("ToolA.java"))).isTrue();
        assertThat(m.matches(Path.of("a/b/ToolB.java"))).isTrue();
        assertThat(m.matches(Path.of("a/b/NotTool.java"))).isFalse();
    }
}