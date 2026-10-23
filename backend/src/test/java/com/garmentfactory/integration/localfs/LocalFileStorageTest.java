package com.garmentfactory.integration.localfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path directory;

    @Test
    void storesReadsAndDeletesFilesUnderNestedKeys() {
        LocalFileStorage storage = new LocalFileStorage(directory);

        storage.put("products/abc/image-1.png", new byte[]{1, 2, 3});

        assertThat(storage.get("products/abc/image-1.png")).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(1, 2, 3));
        storage.delete("products/abc/image-1.png");
        assertThat(storage.get("products/abc/image-1.png")).isEmpty();
        storage.delete("products/abc/image-1.png");
    }

    @Test
    void aMissingFileIsEmptyNotAnError() {
        assertThat(new LocalFileStorage(directory).get("products/none.png")).isEmpty();
    }

    @Test
    void writingTheSameKeyAgainReplacesTheContent() {
        LocalFileStorage storage = new LocalFileStorage(directory);
        storage.put("a/b.png", new byte[]{1});

        storage.put("a/b.png", new byte[]{9, 9});

        assertThat(storage.get("a/b.png")).hasValueSatisfying(bytes -> assertThat(bytes).containsExactly(9, 9));
    }

    @Test
    void leavesNoTemporaryFilesBehind() throws IOException {
        LocalFileStorage storage = new LocalFileStorage(directory);

        storage.put("products/x/one.png", new byte[]{1});

        try (Stream<Path> files = Files.walk(directory)) {
            assertThat(files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()))
                    .containsExactly("one.png");
        }
    }

    @Test
    void rejectsKeysThatCouldEscapeTheStorageRoot() {
        LocalFileStorage storage = new LocalFileStorage(directory);

        for (String key : new String[]{"../evil.png", "a/../../evil.png", "/etc/passwd", "a//b.png", "a b.png", "", "..", "a\\b.png"}) {
            assertThatThrownBy(() -> storage.put(key, new byte[]{1})).as("put %s", key).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.get(key)).as("get %s", key).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> storage.delete(key)).as("delete %s", key).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> storage.get(null)).isInstanceOf(IllegalArgumentException.class);
    }
}