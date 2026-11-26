/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.support.processor.idempotent;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The file store is rewritten when a key is removed and when the oldest keys are dropped: a rewrite that fails must
 * leave the file store unchanged, instead of truncated or half written (CAMEL-27430).
 */
class FileIdempotentRepositoryRewriteTest {

    @TempDir
    Path dir;

    private FileIdempotentRepository repository;

    @AfterEach
    void stop() {
        if (repository != null) {
            repository.stop();
        }
    }

    @Test
    void removeKeepsTheStoreWhenTheRewriteFails() throws Exception {
        Path store = dir.resolve("store.dat");
        AtomicBoolean diskFull = new AtomicBoolean();
        repository = new FailingRewriteRepository(store, diskFull);
        repository.start();
        repository.add("A");
        repository.add("B");
        repository.add("C");

        diskFull.set(true);
        assertThatThrownBy(() -> repository.remove("B")).hasMessageContaining("No space left on device");

        assertThat(Files.readString(store)).isEqualTo("A\nB\nC\n");
        assertThat(dir.resolve("store.dat.tmp")).doesNotExist();
        assertThat(repository.contains("B")).as("the key that could not be removed is still stored").isTrue();

        diskFull.set(false);
        repository.remove("B");
        assertThat(Files.readString(store)).isEqualTo("A\nC\n");
    }

    @Test
    void addSucceedsAndKeepsTheStoreWhenDroppingTheOldestKeysFails() throws Exception {
        Path store = dir.resolve("store.dat");
        AtomicBoolean diskFull = new AtomicBoolean();
        repository = new FailingRewriteRepository(store, diskFull);
        repository.setMaxFileStoreSize(6);
        repository.setDropOldestFileStore(1);
        repository.start();
        repository.add("A");
        repository.add("B");
        repository.add("C");
        assertThat(Files.readString(store)).isEqualTo("A\nB\nC\n");

        // the key is stored, so the add succeeds although the oldest keys cannot be dropped
        diskFull.set(true);
        assertThat(repository.add("D")).isTrue();
        assertThat(Files.readString(store)).isEqualTo("A\nB\nC\nD\n");
        assertThat(repository.add("D")).as("a duplicate").isFalse();

        // the next add drops the oldest key
        diskFull.set(false);
        assertThat(repository.add("E")).isTrue();
        assertThat(Files.readString(store)).isEqualTo("B\nC\nD\nE\n");
    }

    @Test
    void removeRewritesTheStoreInPlaceWhenNoTemporaryFileCanBeCreated() throws Exception {
        Path store = dir.resolve("store.dat");
        repository = new FileIdempotentRepository(store.toFile(), new HashMap<>());
        repository.start();
        repository.add("A");
        repository.add("B");
        repository.add("C");

        // the temporary file cannot be created (here: a directory that is not empty took its place)
        Path tmp = Files.createDirectory(dir.resolve("store.dat.tmp"));
        Files.writeString(tmp.resolve("other"), "other");

        assertThat(repository.remove("B")).isTrue();
        assertThat(Files.readString(store)).isEqualTo("A\nC\n");
        assertThat(tmp.resolve("other")).exists();
    }

    @Test
    void removeReplacesTheStore() throws Exception {
        Path store = dir.resolve("store.dat");
        repository = new FileIdempotentRepository(store.toFile(), new HashMap<>());
        repository.start();
        repository.add("A");
        repository.add("B");
        repository.add("C");

        assertThat(repository.remove("B")).isTrue();

        assertThat(Files.readString(store)).isEqualTo("A\nC\n");
        assertThat(dir.resolve("store.dat.tmp")).doesNotExist();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void removeKeepsTheSymlinkedStore() throws Exception {
        // a store which is a symbolic link to a file in another directory
        Path realStore = Files.createDirectory(dir.resolve("real")).resolve("store.dat");
        Files.writeString(realStore, "A\nB\nC\n");
        Path link = dir.resolve("store.dat");
        try {
            Files.createSymbolicLink(link, realStore);
        } catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "Symbolic links are not supported: " + e.getMessage());
        }
        repository = new FileIdempotentRepository(link.toFile(), new HashMap<>());
        repository.start();

        assertThat(repository.remove("B")).isTrue();

        assertThat(Files.isSymbolicLink(link)).isTrue();
        assertThat(Files.readSymbolicLink(link)).isEqualTo(realStore);
        assertThat(Files.readString(realStore)).isEqualTo("A\nC\n");
        assertThat(Path.of(realStore + ".tmp")).doesNotExist();
        assertThat(Files.exists(Path.of(link + ".tmp"), LinkOption.NOFOLLOW_LINKS)).isFalse();
    }

    @Test
    void removeKeepsThePermissionsOfTheStore() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX file permissions are not supported");

        // a store that only its owner can read
        Path store = dir.resolve("store.dat");
        Files.writeString(store, "A\nB\nC\n");
        Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rw-------");
        Files.setPosixFilePermissions(store, permissions);
        AtomicBoolean checked = new AtomicBoolean();
        repository = new FileIdempotentRepository(store.toFile(), new HashMap<>()) {
            @Override
            void writeKeys(OutputStream out, List<String> keys) throws IOException {
                // the temporary file has the permissions of the store before the keys are written to it
                assertThat(Files.getPosixFilePermissions(Path.of(store + ".tmp"))).isEqualTo(permissions);
                checked.set(true);
                super.writeKeys(out, keys);
            }
        };
        repository.start();

        assertThat(repository.remove("B")).isTrue();

        assertThat(checked).isTrue();
        assertThat(Files.getPosixFilePermissions(store)).isEqualTo(permissions);
        assertThat(Files.readString(store)).isEqualTo("A\nC\n");
    }

    /**
     * A repository whose rewrite of the file store fails after the first key, as when the disk is full
     */
    private static final class FailingRewriteRepository extends FileIdempotentRepository {
        private final AtomicBoolean diskFull;

        FailingRewriteRepository(Path store, AtomicBoolean diskFull) {
            super(store.toFile(), new HashMap<>());
            this.diskFull = diskFull;
        }

        @Override
        void writeKeys(OutputStream out, List<String> keys) throws IOException {
            if (diskFull.get()) {
                out.write((keys.get(0) + "\n").getBytes());
                out.flush();
                throw new IOException("No space left on device");
            }
            super.writeKeys(out, keys);
        }
    }
}
