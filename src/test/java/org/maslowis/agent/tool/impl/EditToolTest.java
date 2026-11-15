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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditToolTest {

    @TempDir
    Path tempDir;

    @Mock
    private WorkspaceGuard workspaceGuard;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private LLMToolContext context;

    private EditTool tool;

    @BeforeEach
    void setUp() {
        tool = new EditTool(workspaceGuard, context);

        lenient().when(workspaceGuard.resolve(anyString())).thenAnswer(inv ->
                                                                               tempDir.resolve((String) inv.getArgument(0)).normalize());
        // По умолчанию файл редактируемый — save/whyFileNotEditable замоканы deep-stubs.
        lenient().when(context.fileStateCache().whyFileNotEditable(any())).thenReturn(null);
    }

    // ---------- Метаданные ----------

    @Test
    void name_isEdit() {
        assertThat(tool.name()).isEqualTo("Edit");
    }

    @Test
    void description_mentionsKeyRules() {
        assertThat(tool.description())
                .contains("Replaces an exact piece of text")
                .contains("use Read on the file first")
                .contains("replaceAll");
    }

    @Test
    void inputType_returnsInputRecord() {
        assertThat(tool.inputType()).isEqualTo(EditTool.Input.class);
    }

    @Test
    void isReadOnly_isFalse() {
        assertThat(tool.isReadOnly()).isFalse();
    }

    // ---------- Валидация входа ----------

    @Test
    void call_withNullInput_returnsError() {
        var result = tool.call(null);

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("File path is empty");
    }

    @Test
    void call_withBlankFilePath_returnsError() {
        var result = tool.call(new EditTool.Input("   ", "a", "b", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("File path is empty");
    }

    @Test
    void call_withNullOldString_returnsError() {
        var result = tool.call(new EditTool.Input("f.txt", null, "b", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("oldString or newString must be not empty");
    }

    @Test
    void call_withNullNewString_returnsError() {
        var result = tool.call(new EditTool.Input("f.txt", "a", null, null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("oldString or newString must be not empty");
    }

    @Test
    void call_whenOldEqualsNew_returnsError() {
        var result = tool.call(new EditTool.Input("f.txt", "same", "same", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("oldString and newString must be not equals");
    }

    // ---------- Ошибки resolve / ФС ----------

    @Test
    void call_whenResolveFails_returnsPathParseError() {
        when(workspaceGuard.resolve("bad"))
                .thenThrow(new WorkspaceViolationException("outside workspace"));

        var result = tool.call(new EditTool.Input("bad", "a", "b", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content())
                .contains("Path parse error")
                .contains("outside workspace");
    }

    @Test
    void call_whenFileDoesNotExist_returnsError() {
        var result = tool.call(new EditTool.Input("nope.txt", "a", "b", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("File not found: nope.txt");
    }

    @Test
    void call_whenPathIsDirectory_returnsError() throws IOException {
        Files.createDirectory(tempDir.resolve("folder"));

        var result = tool.call(new EditTool.Input("folder", "a", "b", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("it is folder, it is not file.");
    }

    // ---------- fileStateCache().whyFileNotEditable ----------

    @Test
    void call_whenFileIsNotEditable_returnsReasonAndDoesNotTouchDisk() throws IOException {
        var file = tempDir.resolve("locked.txt");
        Files.writeString(file, "hello");
        when(context.fileStateCache().whyFileNotEditable(file))
                .thenReturn("File was not read yet, call Read first");

        var result = tool.call(new EditTool.Input("locked.txt", "hello", "bye", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).isEqualTo("File was not read yet, call Read first");
        assertThat(Files.readString(file)).isEqualTo("hello");
        verify(context.fileStateCache(), never()).save(any());
    }

    // ---------- occurrences == 0 ----------

    @Test
    void call_whenOldStringNotFound_returnsErrorAndLeavesFileUntouched() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "hello world");

        var result = tool.call(new EditTool.Input("a.txt", "missing", "x", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content())
                .contains("Text 'missing' not found")
                .contains("a.txt");
        assertThat(Files.readString(file)).isEqualTo("hello world");
        verify(context.fileStateCache(), never()).save(any());
    }

    // ---------- occurrences > 1 без replaceAll ----------

    @Test
    void call_whenOldStringNotUniqueWithoutReplaceAll_returnsErrorAndLeavesFileUntouched() throws IOException {
        var file = tempDir.resolve("dup.txt");
        Files.writeString(file, "foo\nfoo\nfoo\n");

        var result = tool.call(new EditTool.Input("dup.txt", "foo", "bar", null));

        assertThat(result.error()).isTrue();
        assertThat(result.content())
                .contains("occurs 3 times")
                .contains("replaceAll");
        assertThat(Files.readString(file)).isEqualTo("foo\nfoo\nfoo\n");
        verify(context.fileStateCache(), never()).save(any());
    }

    @Test
    void call_whenReplaceAllIsFalseExplicitly_andMultipleOccurrences_returnsError() throws IOException {
        var file = tempDir.resolve("dup.txt");
        Files.writeString(file, "x x x");

        var result = tool.call(new EditTool.Input("dup.txt", "x", "y", false));

        assertThat(result.error()).isTrue();
        assertThat(result.content()).contains("occurs 3 times");
        assertThat(Files.readString(file)).isEqualTo("x x x");
    }

    // ---------- occurrences > 1 c replaceAll ----------

    @Test
    void call_withReplaceAllTrue_replacesEveryOccurrence() throws IOException {
        var file = tempDir.resolve("dup.txt");
        Files.writeString(file, "foo foo foo");

        var result = tool.call(new EditTool.Input("dup.txt", "foo", "bar", true));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("Replaced in dup.txt, occurrences: 3");
        assertThat(Files.readString(file)).isEqualTo("bar bar bar");
        verify(context.fileStateCache()).save(file);
    }

    // ---------- occurrences == 1 ----------

    @Test
    void call_whenUniqueMatch_replacesItAndSavesState() throws IOException {
        var file = tempDir.resolve("one.txt");
        Files.writeString(file, "hello world");

        var result = tool.call(new EditTool.Input("one.txt", "world", "there", null));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("Replaced in one.txt, occurrences: 1");
        assertThat(Files.readString(file)).isEqualTo("hello there");
        verify(context.fileStateCache()).save(file);
    }

    @Test
    void call_whenUniqueMatch_andReplaceAllTrue_stillReportsOneOccurrence() throws IOException {
        var file = tempDir.resolve("one.txt");
        Files.writeString(file, "abc");

        var result = tool.call(new EditTool.Input("one.txt", "b", "X", true));

        assertThat(result.error()).isFalse();
        assertThat(result.content()).isEqualTo("Replaced in one.txt, occurrences: 1");
        assertThat(Files.readString(file)).isEqualTo("aXc");
    }

    // ---------- Граничные случаи ----------

    @Test
    void call_withEmptyNewString_deletesFragment() throws IOException {
        var file = tempDir.resolve("del.txt");
        Files.writeString(file, "keepREMOVEkeep");

        var result = tool.call(new EditTool.Input("del.txt", "REMOVE", "", null));

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("keepkeep");
    }

    @Test
    void call_preservesWhitespaceAndNewlinesExactly() throws IOException {
        var file = tempDir.resolve("multi.txt");
        var original = "line1\n    indented\nline3\n";
        Files.writeString(file, original);

        var result = tool.call(new EditTool.Input("multi.txt", "    indented", "  changed", null));

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("line1\n  changed\nline3\n");
    }

    @Test
    void call_matchesAcrossMultipleLines() throws IOException {
        var file = tempDir.resolve("multi.txt");
        Files.writeString(file, "a\nb\nc\n");

        var result = tool.call(new EditTool.Input("multi.txt", "a\nb", "A\nB", null));

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("A\nB\nc\n");
    }
}