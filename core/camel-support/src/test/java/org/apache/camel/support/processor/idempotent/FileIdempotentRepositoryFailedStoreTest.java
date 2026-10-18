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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.RuntimeCamelException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A key whose add failed because the file store could not be read or written is not stored, so adding it again (the
 * retry of the message) must not be taken for a duplicate.
 */
class FileIdempotentRepositoryFailedStoreTest {

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
    void keyIsNotAddedWhenTheStoreCannotBeWritten() throws Exception {
        AtomicBoolean diskFull = new AtomicBoolean(true);
        repository = new FileIdempotentRepository(dir.resolve("store.dat").toFile(), new HashMap<>()) {
            @Override
            protected void appendToStore(String key) {
                if (diskFull.get()) {
                    throw new RuntimeCamelException("No space left on device");
                }
                super.appendToStore(key);
            }
        };
        repository.start();

        assertThatThrownBy(() -> repository.add("A")).hasMessageContaining("No space left on device");

        diskFull.set(false);
        assertThat(repository.add("A")).as("the retry of a key that was not stored").isTrue();
        assertThat(repository.add("A")).as("a duplicate").isFalse();
        assertThat(Files.readString(dir.resolve("store.dat"))).isEqualTo("A\n");
    }

    @Test
    void keyIsNotAddedWhenTheStoreCannotBeRead() throws Exception {
        File store = dir.resolve("store.dat").toFile();
        repository = new FileIdempotentRepository(store, new HashMap<>());
        repository.start();

        // the store cannot be read (here: a directory took its place)
        assertThat(store.delete()).isTrue();
        assertThat(store.mkdir()).isTrue();
        assertThatThrownBy(() -> repository.add("A")).isInstanceOf(RuntimeCamelException.class);

        assertThat(store.delete()).isTrue();
        assertThat(repository.add("A")).as("the retry of a key that was not stored").isTrue();
        assertThat(repository.contains("A")).isTrue();
    }

    @Test
    void keyFoundInTheStoreIsCached() throws Exception {
        Path store = dir.resolve("store.dat");
        repository = new FileIdempotentRepository(store.toFile(), new HashMap<>());
        repository.start();
        // written by another repository after this one loaded the store
        Files.writeString(store, "A\n");

        assertThat(repository.add("A")).isFalse();
        assertThat(repository.getCache()).containsKey("A");
    }
}
