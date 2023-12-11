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
package org.apache.camel.support.processor.state;

import java.io.File;

import org.apache.camel.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.processor.state.FileStateRepository.fileStateRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileStateRepositoryTest extends TestSupport {

    private File repositoryStore;

    @BeforeEach
    public void setUpTemporaryFile() {
        repositoryStore = testFile("file-state-repository.dat").toFile();
    }

    @Test
    public void shouldPreventUsingDelimiterInKey() throws Exception {
        // Given a FileStateRepository
        FileStateRepository repository = fileStateRepository(repositoryStore);

        // When trying to use the key delimiter in a key
        // Then an exception is thrown
        assertThrows(IllegalArgumentException.class, () -> repository.setState("=", "value"));

    }

    @Test
    public void shouldPreventUsingNewLineInKey() throws Exception {
        // Given a FileStateRepository
        FileStateRepository repository = createRepository();

        // When trying to use new line in a key
        // Then an exception is thrown
        assertThrows(IllegalArgumentException.class, () -> repository.setState("=", "value"));
    }

    @Test
    public void shouldPreventUsingNewLineInValue() throws Exception {
        // Given a FileStateRepository
        FileStateRepository repository = createRepository();

        // When trying to use new line in a key
        // Then an exception is thrown
        assertThrows(IllegalArgumentException.class, () -> repository.setState("key", "\n"));
    }

    @Test
    public void shouldSaveState() throws Exception {
        // Given an empty FileStateRepository
        FileStateRepository repository = createRepository();

        // When saving a state
        repository.setState("key", "value");

        // Then it should be retrieved afterwards
        assertEquals("value", repository.getState("key"));
    }

    @Test
    public void shouldUpdateState() throws Exception {
        // Given a FileStateRepository with a state in it
        FileStateRepository repository = createRepository();
        repository.setState("key", "value");

        // When updating the state
        repository.setState("key", "value2");

        // Then the new value should be retrieved afterwards
        assertEquals("value2", repository.getState("key"));
    }

    @Test
    public void shouldSynchronizeInFile() throws Exception {
        // Given a FileStateRepository with some content
        FileStateRepository repository = createRepository();
        repository.setState("key1", "value1");
        repository.setState("key2", "value2");
        repository.setState("key3", "value3");

        // When creating a new FileStateRepository with same file
        FileStateRepository newRepository = createRepository();

        // Then the new one should have the same content
        assertEquals("value1", newRepository.getState("key1"));
        assertEquals("value2", newRepository.getState("key2"));
        assertEquals("value3", newRepository.getState("key3"));
    }

    @Test
    public void shouldPreventRepositoryFileFromGrowingInfinitely() throws Exception {
        // Given a FileStateRepository with a maximum size of 100 bytes
        FileStateRepository repository = createRepository();
        repository.setMaxFileStoreSize(100);

        // And content just to this limit (10x10 bytes)
        for (int i = 0; i < 10; i++) {
            repository.setState("key", "xxxxx".replace('x', (char) ('0' + i)));
        }
        long previousSize = repositoryStore.length();

        // When updating the state
        repository.setState("key", "value");

        // Then it should be truncated
        assertTrue(repositoryStore.length() < previousSize);
    }

    private FileStateRepository createRepository() throws Exception {
        FileStateRepository repository = fileStateRepository(repositoryStore);
        repository.start();
        return repository;
    }
}
