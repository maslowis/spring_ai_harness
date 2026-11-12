package org.maslowis.agent.tool.guard;

import org.maslowis.agent.AgentProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

@Component
public class WorkspaceGuard {

    private final Path root;

    public WorkspaceGuard(AgentProperties agentProperties) {
        this.root = Path.of(agentProperties.workspaceRoot()).toAbsolutePath().normalize();
    }

    public Path root() {
        return root;
    }

    public String relativize(Path path) {
        return root.relativize(path).toString().replace("\\", "/");
    }

    public Path resolve(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new WorkspaceViolationException("Path is empty");
        }

        Path candidate = Path.of(rawPath);
        Path resolved = (candidate.isAbsolute() ? candidate : root.resolve(candidate)).normalize();

        if (!resolved.startsWith(root))
            throw new WorkspaceViolationException("Path %s outside workspace %s".formatted(resolved, root));

        try {
            Path realPath = resolved.toRealPath();
            if (!realPath.startsWith(root.toRealPath())) {
                throw new WorkspaceViolationException("Path %s outside workspace %s by symlink".formatted(rawPath, root));
            }
        } catch (NoSuchFileException pathNotCreatedYet) {
            // Пути ещё нет: модель ошиблась именем или файл только предстоит создать.
            // Симлинка у несуществующего пути быть не может — проверять нечего.
        } catch (IOException cannotVerify) {
            throw new WorkspaceViolationException("Path %s check failed: %s".formatted(rawPath, cannotVerify.getCause()));
        }

        return resolved;
    }

}
