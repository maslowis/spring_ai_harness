package org.maslowis.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContentUtilsTest {

    // ---------- PREVIEW_MAX_LENGTH ----------

    @Test
    void previewMaxLength_is150() {
        assertThat(ContentUtils.PREVIEW_MAX_LENGTH).isEqualTo(150);
    }

    // ---------- preview(): пустые входы ----------

    @Test
    void preview_null_returnsEmpty() {
        assertThat(ContentUtils.preview(null)).isEmpty();
    }

    @Test
    void preview_emptyString_returnsEmpty() {
        assertThat(ContentUtils.preview("")).isEmpty();
    }

    @Test
    void preview_blankString_returnsEmpty() {
        assertThat(ContentUtils.preview("   ")).isEmpty();
    }

    @Test
    void preview_whitespaceOnlyWithNewlines_returnsEmpty() {
        assertThat(ContentUtils.preview("  \n\t\n")).isEmpty();
    }

    // ---------- preview(): короткие строки ----------

    @Test
    void preview_shortLine_returnsAsIs() {
        assertThat(ContentUtils.preview("hello world")).isEqualTo("hello world");
    }

    @Test
    void preview_exactlyMaxLength_noTruncation() {
        var s = "x".repeat(ContentUtils.PREVIEW_MAX_LENGTH);

        assertThat(ContentUtils.preview(s)).isEqualTo(s).hasSize(ContentUtils.PREVIEW_MAX_LENGTH);
    }

    @Test
    void preview_oneOverMaxLength_truncatesWithEllipsis() {
        var s = "x".repeat(ContentUtils.PREVIEW_MAX_LENGTH + 1);

        var result = ContentUtils.preview(s);

        assertThat(result)
                .hasSize(ContentUtils.PREVIEW_MAX_LENGTH + 3)
                .endsWith("...")
                .startsWith("x".repeat(ContentUtils.PREVIEW_MAX_LENGTH));
        assertThat(result.substring(0, ContentUtils.PREVIEW_MAX_LENGTH))
                .isEqualTo("x".repeat(ContentUtils.PREVIEW_MAX_LENGTH));
    }

    @Test
    void preview_farOverMaxLength_truncatesTo150() {
        var s = "a".repeat(1000);

        var result = ContentUtils.preview(s);

        assertThat(result).isEqualTo("a".repeat(ContentUtils.PREVIEW_MAX_LENGTH) + "...");
    }

    // ---------- preview(): многострочные ----------

    @Test
    void preview_multiline_returnsFirstLineOnly() {
        assertThat(ContentUtils.preview("first\nsecond\nthird")).isEqualTo("first");
    }

    @Test
    void preview_multiline_longFirstLine_truncatedOthersIgnored() {
        var s = "a".repeat(500) + "\nsecond\nthird";

        var result = ContentUtils.preview(s);

        assertThat(result)
                .isEqualTo("a".repeat(ContentUtils.PREVIEW_MAX_LENGTH) + "...")
                .doesNotContain("second");
    }

    @Test
    void preview_firstLineBlankButContentNotEmpty_returnsBlankFirstLine() {
        // isNotBlank(content) — true, значит firstLine = "   " (без trim), возвращается как есть.
        assertThat(ContentUtils.preview("   \nreal content")).isEqualTo("   ");
    }

    @Test
    void preview_crlfLineEndings_returnsFirstLineWithoutCarriageReturn() {
        // lines() нормализует \r\n → \n, поэтому \r в конце не должно остаться.
        assertThat(ContentUtils.preview("hello\r\nworld")).isEqualTo("hello");
    }

    @Test
    void preview_singleLineWithTrailingNewline_returnsContentWithoutNewline() {
        assertThat(ContentUtils.preview("only\n")).isEqualTo("only");
    }

    // ---------- isBlank ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", "\n", "\r\n", " \t\n "})
    void isBlank_trueForNullOrWhitespace(String input) {
        assertThat(ContentUtils.isBlank(input)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", "x y", "\u00A0x"})
    void isBlank_falseForContent(String input) {
        assertThat(ContentUtils.isBlank(input)).isFalse();
    }

    @Test
    void isBlank_nonBreakingSpaceAlone_isNotBlank() {
        // Character.isWhitespace('\u00A0') == false, поэтому String#isBlank даёт false.
        assertThat(ContentUtils.isBlank("\u00A0")).isFalse();
    }

    // ---------- isNotBlank ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "   ", "\t", "\n", "\r\n", " \t\n "})
    void isNotBlank_falseForNullOrWhitespace(String input) {
        assertThat(ContentUtils.isNotBlank(input)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", " a ", "x y"})
    void isNotBlank_trueForContent(String input) {
        assertThat(ContentUtils.isNotBlank(input)).isTrue();
    }

    // ---------- Инвариант ----------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "x", "\t\n", " a ", "\u00A0"})
    void isNotBlank_isInverseOfIsBlank(String input) {
        assertThat(ContentUtils.isNotBlank(input)).isEqualTo(!ContentUtils.isBlank(input));
    }
}