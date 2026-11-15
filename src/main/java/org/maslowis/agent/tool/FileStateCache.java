package org.maslowis.agent.tool;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FileStateCache {

    private final Map<Path, FileState> filesContext = new ConcurrentHashMap<>();

    public void save(@NonNull Path file) {
        try {
            var key = file.normalize();
            var val = new FileState(Files.readString(key), lastModifiedAt(key));
            filesContext.put(key, val);
        } catch (IOException e) {
            throw new RuntimeException("Fail read file %s".formatted(file));
        }
    }

    public FileState get(@NonNull Path file) {
        return filesContext.get(file.normalize());
    }

    public boolean sameContentOnDisk(@NonNull Path file, FileState cachedState) {
        try {
            return Files.readString(file).equals(cachedState.content);
        } catch (IOException e) {
            return false;
        }
    }

    public String whyFileNotEditable(@NonNull Path file) {
        if (!Files.exists(file)) {
            return null;
        }

        var cachedState = get(file);
        if (cachedState == null) {
            return "File %s was not read. First, read it using the Read tool.".formatted(file);
        }
        if (lastModifiedAt(file) > cachedState.lastModifiedAt && !sameContentOnDisk(file, cachedState)) {
            return "File %s was changed. Repeat read it using the Read tool.".formatted(file);
        }

        return null;
    }

    private static long lastModifiedAt(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    public record FileState(String content, long lastModifiedAt) {
    }
}
