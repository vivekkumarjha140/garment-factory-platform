package com.garmentfactory.integration.localfs;

import com.garmentfactory.shared.config.AppProperties;
import com.garmentfactory.storage.api.FileStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stores files in a directory (a Docker volume in production). Keys are validated and the resolved
 * path must stay inside the root, so a key can never reach outside it. Files are written to a
 * temporary name first and moved into place, so a reader never sees a half-written file.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorage.class);
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9/_.-]{0,254}$");

    private final Path root;

    @Autowired
    public LocalFileStorage(AppProperties properties) {
        this(Path.of(properties.storage().localRoot()));
    }

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create the storage directory " + this.root, ex);
        }
    }

    @Override
    public void put(String key, byte[] content) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store " + key, ex);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Path path = resolve(key);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read " + key, ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            log.warn("Could not delete stored file {}: {}", key, ex.getClass().getSimpleName());
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches() || key.contains("..") || key.contains("//")) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid storage key");
        }
        return path;
    }
}