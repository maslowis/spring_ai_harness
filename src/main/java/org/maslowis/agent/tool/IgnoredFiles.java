package org.maslowis.agent.tool;

import lombok.experimental.UtilityClass;

import java.nio.file.Path;
import java.util.Set;

@UtilityClass
public class IgnoredFiles {

    private static final Set<String> DIRECTORIES =
            Set.of(".git", "target", "build", "node_modules", ".idea", ".mvn", "ollama", ".vscode",
                   ".gradle", ".kotlin", ".DS_Store", "out", "bin", ".junie");

    public static boolean notContains(Path root, Path candidate) {
        Path relative = root.relativize(candidate);
        for (Path segment : relative) {
            if (DIRECTORIES.contains(segment.toString())) {
                return false;
            }
        }
        return true;
    }
}
