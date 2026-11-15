package org.maslowis.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStateCacheTest {

    @TempDir
    Path tempDir;

    private FileStateCache cache;

    @BeforeEach
    void setUp() {
        cache = new FileStateCache();
    }

    // ---------- save() ----------

    @Test
    void save_capturesContentAndModifiedTime() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "hello");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000L));

        cache.save(file);

        var state = cache.get(file);
        assertThat(state).isNotNull();
        assertThat(state.content()).isEqualTo("hello");
        assertThat(state.lastModifiedAt()).isEqualTo(1_000_000_000L);
    }

    @Test
    void save_capturesSnapshot_notLazyReference() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "first");

        cache.save(file);
        Files.writeString(file, "second");

        assertThat(cache.get(file).content()).isEqualTo("first");
    }

    @Test
    void save_overwritesPreviousState() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "first");
        cache.save(file);

        Files.writeString(file, "second");
        cache.save(file);

        assertThat(cache.get(file).content()).isEqualTo("second");
    }

    @Test
    void save_usesNormalizedKey() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "data");

        // Путь с избыточным "." — normalize() должен схлопнуть его до того же ключа.
        var weird = tempDir.resolve("./a.txt").resolve("..").resolve("a.txt");

        cache.save(weird);

        assertThat(cache.get(file)).isNotNull();
        assertThat(cache.get(file).content()).isEqualTo("data");
    }

    @Test
    void save_throwsRuntimeException_whenFileMissing() {
        var missing = tempDir.resolve("nope.txt");

        assertThatThrownBy(() -> cache.save(missing))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Fail read file")
                .hasMessageContaining("nope.txt");
    }

    // ---------- get() ----------

    @Test
    void get_returnsNullForUnknownFile() {
        assertThat(cache.get(tempDir.resolve("unknown.txt"))).isNull();
    }

    @Test
    void get_normalizesKey() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "x");
        cache.save(file);

        var weird = tempDir.resolve(".").resolve("a.txt");
        assertThat(cache.get(weird)).isNotNull();
    }

    // ---------- sameContentOnDisk() ----------

    @Test
    void sameContentOnDisk_trueWhenContentEquals() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "same");
        cache.save(file);
        var state = cache.get(file);

        assertThat(cache.sameContentOnDisk(file, state)).isTrue();
    }

    @Test
    void sameContentOnDisk_falseWhenContentDiffers() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "same");
        cache.save(file);
        var state = cache.get(file);

        Files.writeString(file, "different");

        assertThat(cache.sameContentOnDisk(file, state)).isFalse();
    }

    @Test
    void sameContentOnDisk_falseWhenFileDeleted() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "same");
        cache.save(file);
        var state = cache.get(file);

        Files.delete(file);

        assertThat(cache.sameContentOnDisk(file, state)).isFalse();
    }

    // ---------- whyFileNotEditable(): базовые случаи ----------

    @Test
    void whyFileNotEditable_returnsNullWhenFileDoesNotExist() {
        // Контракт: для несуществующего файла cache не блокирует (это задача вызывающего).
        assertThat(cache.whyFileNotEditable(tempDir.resolve("missing.txt"))).isNull();
    }

    @Test
    void whyFileNotEditable_returnsNotReadMessageWhenNotCached() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "x");

        var reason = cache.whyFileNotEditable(file);

        assertThat(reason)
                .isNotNull()
                .contains("was not read")
                .contains("Read tool");
    }

    @Test
    void whyFileNotEditable_returnsNullWhenFileUnchanged() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "x");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000L));
        cache.save(file);

        assertThat(cache.whyFileNotEditable(file)).isNull();
    }

    // ---------- whyFileNotEditable(): mtime и content ----------

    @Test
    void whyFileNotEditable_returnsNull_whenMtimeGrewButContentUnchanged() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "same-content");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000L));
        cache.save(file);

        // «Коснулись» файла: mtime вырос, но контент идентичен.
        Files.writeString(file, "same-content");
        Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000_000L));

        assertThat(cache.whyFileNotEditable(file)).isNull();
    }

    @Test
    void whyFileNotEditable_returnsChangedMessage_whenMtimeGrewAndContentDiffers() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "old");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000L));
        cache.save(file);

        Files.writeString(file, "new");
        Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000_000L));

        var reason = cache.whyFileNotEditable(file);

        assertThat(reason)
                .isNotNull()
                .contains("was changed")
                .contains("Read tool");
    }

    /**
     * Документирует неочевидный контракт: если mtime НЕ вырос, но контент другой —
     * редактирование не блокируется. Это осознанный выбор реализации
     * (защита от ложных срабатываний при «странном» поведении FS).
     */
    @Test
    void whyFileNotEditable_returnsNull_whenContentDiffersButMtimeNotGrew() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "old");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000L));
        cache.save(file);

        Files.writeString(file, "new");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000L)); // тот же mtime

        assertThat(cache.whyFileNotEditable(file)).isNull();
    }

    @Test
    void whyFileNotEditable_returnsNull_whenMtimeWentBackwards() throws IOException {
        var file = tempDir.resolve("a.txt");
        Files.writeString(file, "old");
        Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000_000L));
        cache.save(file);

        Files.writeString(file, "new");
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000_000L));

        assertThat(cache.whyFileNotEditable(file)).isNull();
    }

    // ---------- FileState record ----------

    @Test
    void fileState_isValueObject() {
        var a = new FileStateCache.FileState("content", 42L);
        var b = new FileStateCache.FileState("content", 42L);
        var c = new FileStateCache.FileState("content", 43L);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
    }
}