package org.maslowis.agent.tool.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.maslowis.agent.tool.LLMToolContext;
import org.maslowis.agent.tool.guard.WorkspaceGuard;
import org.maslowis.agent.tool.guard.WorkspaceViolationException;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReadToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private WorkspaceGuard workspaceGuard;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private LLMToolContext context;

    private ReadTool tool;

    @BeforeEach
    void setUp() {
        tool = new ReadTool(workspaceGuard, context);
    }

    // ---------- Метаданные ----------

    @Test
    void name_isRead() {
        assertThat(tool.name()).isEqualTo("Read");
    }

    @Test
    void description_mentionsKeyUsagePoints() {
        assertThat(tool.description())
                .contains("Reads a text file")
                .contains("cat -n")
                .contains("2000");
    }

    @Test
    void inputType_returnsInputRecord() {
        assertThat(tool.inputType()).isEqualTo(ReadTool.Input.class);
    }

    // ---------- Ошибки ----------

    @Test
    void call_whenWorkspaceGuardRejectsPath_returnsError() {
        when(workspaceGuard.resolve(anyString()))
                .thenThrow(new WorkspaceViolationException("outside workspace"));

        var result = tool.call(new ReadTool.Input("../etc/passwd", null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content())
                .contains("Path resolve error")
                .contains("outside workspace");
    }

    @Test
    void call_whenFileDoesNotExist_returnsError() {
        var missing = tempDir.resolve("nope.txt");
        when(workspaceGuard.resolve("nope.txt")).thenReturn(missing);

        var result = tool.call(new ReadTool.Input("nope.txt", null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content())
                .contains("File not found")
                .contains("nope.txt");
    }

    @Test
    void call_whenPathIsDirectory_returnsError() throws IOException {
        var dir = tempDir.resolve("folder");
        Files.createDirectory(dir);
        when(workspaceGuard.resolve("folder")).thenReturn(dir);

        var result = tool.call(new ReadTool.Input("folder", null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("It is not a text file, it is a folder.");
    }

    @Test
    void call_whenFileIsNotText_returnsError() throws IOException {
        var file = tempDir.resolve("binary.bin");
        Files.write(file, new byte[]{(byte) 0xFF, (byte) 0xFE, (byte) 0xFD});
        when(workspaceGuard.resolve("binary.bin")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("binary.bin", null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("Not a text file");
    }

    // ---------- Пустой файл ----------

    @Test
    void call_whenFileIsEmpty_returnsEmptyMessage() throws IOException {
        var file = tempDir.resolve("empty.txt");
        Files.createFile(file);
        when(workspaceGuard.resolve("empty.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("empty.txt", null, null));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("File is empty");
    }

    // ---------- Успешное чтение ----------

    @Test
    void call_readsAllLinesWithLineNumbers() throws IOException {
        var file = tempDir.resolve("greetings.txt");
        Files.writeString(file, "hello\nworld\nfoo\n");
        when(workspaceGuard.resolve("greetings.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("greetings.txt", null, null));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("1\thello\n2\tworld\n3\tfoo\n");
    }

    @Test
    void call_appliesOffsetAndLimit() throws IOException {
        var file = tempDir.resolve("five.txt");
        Files.writeString(file, "a\nb\nc\nd\ne\n");
        when(workspaceGuard.resolve("five.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("five.txt", 2, 2));

        assertThat(result.error()).isFalse();
        assertThat(result.content())
                .startsWith("2\tb\n3\tc\n")
                .contains("Lines 2 to 3 are shown. Total lines 5.");
    }

    @Test
    void call_readsFromOffsetToEnd() throws IOException {
        var file = tempDir.resolve("five.txt");
        Files.writeString(file, "a\nb\nc\nd\ne\n");
        when(workspaceGuard.resolve("five.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("five.txt", 4, null));

        assertThat(result.content()).isEqualTo("4\td\n5\te\n");
    }

    @Test
    void call_clampsNegativeOffsetToFirstLine() throws IOException {
        var file = tempDir.resolve("three.txt");
        Files.writeString(file, "a\nb\nc\n");
        when(workspaceGuard.resolve("three.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("three.txt", -5, 2));

        assertThat(result.content()).startsWith("1\ta\n2\tb\n");
    }

    @Test
    void call_usesDefaultLimitOfTwoThousandLines() throws IOException {
        var sb = new StringBuilder();
        for (int i = 1; i <= 2005; i++) {
            sb.append("line").append(i).append('\n');
        }
        var file = tempDir.resolve("big.txt");
        Files.writeString(file, sb.toString());
        when(workspaceGuard.resolve("big.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("big.txt", null, null));

        assertThat(result.content())
                .contains("1\tline1")
                .contains("2000\tline2000")
                .doesNotContain("2001\tline2001")
                .contains("Lines 1 to 2000 are shown. Total lines 2005.");
    }

    // ---------- Обрезка и подсказки ----------

    @Test
    void call_truncatesLinesLongerThanMaxLength() throws IOException {
        var longLine = "x".repeat(2500);
        var file = tempDir.resolve("long.txt");
        Files.writeString(file, longLine);
        when(workspaceGuard.resolve("long.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("long.txt", null, null));

        var expected = "1\t" + "x".repeat(2000) + "... (line is cut off)\n";
        assertThat(result.content()).isEqualTo(expected);
    }

    @Test
    void call_doesNotTruncateLineAtExactlyMaxLength() throws IOException {
        var line = "x".repeat(2000);
        var file = tempDir.resolve("edge.txt");
        Files.writeString(file, line);
        when(workspaceGuard.resolve("edge.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("edge.txt", null, null));

        assertThat(result.content()).isEqualTo("1\t" + line + "\n");
    }

    @Test
    void call_appendsContinuationHintWhenFileHasMoreLines() throws IOException {
        var sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            sb.append("line").append(i).append('\n');
        }
        var file = tempDir.resolve("ten.txt");
        Files.writeString(file, sb.toString());
        when(workspaceGuard.resolve("ten.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("ten.txt", 1, 3));

        assertThat(result.content())
                .startsWith("1\tline0\n2\tline1\n3\tline2\n")
                .endsWith("... Lines 1 to 3 are shown. Total lines 10. Use offset to continue reading.");
    }

    @Test
    void call_doesNotAppendContinuationHintWhenAllLinesShown() throws IOException {
        var file = tempDir.resolve("two.txt");
        Files.writeString(file, "a\nb\n");
        when(workspaceGuard.resolve("two.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("two.txt", null, null));

        assertThat(result.content())
                .isEqualTo("1\ta\n2\tb\n")
                .doesNotContain("Use offset to continue reading");
    }

    @Test
    void call_handlesOffsetBeyondFile() throws IOException {
        var file = tempDir.resolve("short.txt");
        Files.writeString(file, "a\nb\n");
        when(workspaceGuard.resolve("short.txt")).thenReturn(file);

        var result = tool.call(new ReadTool.Input("short.txt", 10, 5));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEmpty();
    }

    // ---------- Побочный эффект ----------

    @Test
    void call_onSuccess_savesFileState() throws IOException {
        var file = tempDir.resolve("state.txt");
        Files.writeString(file, "data\n");
        when(workspaceGuard.resolve("state.txt")).thenReturn(file);

        tool.call(new ReadTool.Input("state.txt", null, null));

        verify(context.fileStateCache()).save(file);
    }
}