package org.maslowis.agent.tool.guard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.maslowis.agent.AgentProperties;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceGuardTest {

    @TempDir
    Path tempDir;      // workspace root

    @TempDir
    Path outsideDir;   // гарантированно вне workspace; JUnit чистит сам

    @Mock
    private AgentProperties agentProperties;

    private Path root;

    private WorkspaceGuard guard;

    @BeforeEach
    void setUp() {
        root = tempDir.toAbsolutePath().normalize();
        when(agentProperties.workspaceRoot()).thenReturn(root.toString());
        guard = new WorkspaceGuard(agentProperties);
    }

    // ---------- root() ----------

    @Test
    void root_isAbsoluteAndNormalized() {
        assertThat(guard.root())
                .isEqualTo(root)
                .isAbsolute()
                .isNormalized();
    }

    // ---------- relativize() ----------

    @Test
    void relativize_returnsRelativePathWithForwardSlashes() {
        var nested = root.resolve("src").resolve("main").resolve("File.java");

        assertThat(guard.relativize(nested)).isEqualTo("src/main/File.java");
    }

    @Test
    void relativize_ofRoot_returnsEmptyString() {
        assertThat(guard.relativize(root)).isEmpty();
    }

    // ---------- resolve(): отклонения ----------

    @Test
    void resolve_rejectsNull() {
        assertThatThrownBy(() -> guard.resolve(null))
                .isInstanceOf(WorkspaceViolationException.class)
                .hasMessageContaining("Path is empty");
    }

    @Test
    void resolve_rejectsEmptyString() {
        assertThatThrownBy(() -> guard.resolve(""))
                .isInstanceOf(WorkspaceViolationException.class)
                .hasMessageContaining("Path is empty");
    }

    @Test
    void resolve_rejectsBlankString() {
        assertThatThrownBy(() -> guard.resolve("   "))
                .isInstanceOf(WorkspaceViolationException.class)
                .hasMessageContaining("Path is empty");
    }

    @Test
    void resolve_rejectsRelativePathEscapingViaDotDot() {
        assertThatThrownBy(() -> guard.resolve("../outside.txt"))
                .isInstanceOf(WorkspaceViolationException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    void resolve_rejectsDeepDotDotEscape() {
        assertThatThrownBy(() -> guard.resolve("a/b/../../../../etc/passwd"))
                .isInstanceOf(WorkspaceViolationException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    void resolve_rejectsAbsolutePathOutsideRoot() {
        var outside = outsideDir.resolve("outside-workspace.txt").toAbsolutePath().normalize();

        assertThatThrownBy(() -> guard.resolve(outside.toString()))
                .isInstanceOf(WorkspaceViolationException.class)
                .hasMessageContaining("outside workspace");
    }

    // ---------- resolve(): успешные кейсы ----------

    @Test
    void resolve_allowsRootItself() {
        assertThat(guard.resolve(root.toString())).isEqualTo(root);
    }

    @Test
    void resolve_allowsRelativePathInsideRoot() throws IOException {
        var file = root.resolve("notes.txt");
        Files.writeString(file, "hello");

        assertThat(guard.resolve("notes.txt")).isEqualTo(file);
    }

    @Test
    void resolve_allowsAbsolutePathInsideRoot() throws IOException {
        var file = root.resolve("data").resolve("a.txt");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hi");

        assertThat(guard.resolve(file.toString())).isEqualTo(file);
    }

    @Test
    void resolve_normalizesRedundantSegmentsInsideRoot() {
        var expected = root.resolve("dir").resolve("file.txt");

        assertThat(guard.resolve("./dir/./sub/../file.txt")).isEqualTo(expected);
    }

    @Test
    void resolve_allowsNotYetExistingPath() {
        // Файла нет — NoSuchFileException проглатывается, путь возвращается.
        var missing = root.resolve("will-be-created.txt");

        assertThat(guard.resolve("will-be-created.txt")).isEqualTo(missing);
    }

    // ---------- symlink ----------

    @Test
    void resolve_allowsSymlinkPointingInsideRoot() throws IOException {
        var target = root.resolve("real.txt");
        Files.writeString(target, "data");
        var link = root.resolve("link.txt");
        assumeSymlinkSupported(link, target);

        assertThat(guard.resolve("link.txt")).isEqualTo(link);
    }

    @Test
    void resolve_rejectsSymlinkPointingOutsideRoot() throws IOException {
        var outsideTarget = outsideDir.resolve("secret.txt");
        Files.writeString(outsideTarget, "secret");

        var link = root.resolve("evil-link.txt");
        assumeSymlinkSupported(link, outsideTarget);

        assertThatThrownBy(() -> guard.resolve("evil-link.txt"))
                .isInstanceOf(WorkspaceViolationException.class)
                .hasMessageContaining("by symlink");
    }

    // ---------- helpers ----------

    private void assumeSymlinkSupported(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Symlinks are not supported on this environment: " + e);
        }
    }
}