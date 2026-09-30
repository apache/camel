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
import java.nio.file.Files;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.TestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.support.processor.state.FileStateRepository.fileStateRepository;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileStateRepositoryTest extends TestSupport {

    private File repositoryStore;

    @BeforeEach
    public void setUpTemporaryFile() {
        repositoryStore = testFile("file-state-repository.dat").toFile();
    }

    @Test
    public void shouldPreventUsingDelimiterInKey() {
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

    @Test
    public void shouldSkipIncompleteLineWhenLoading() throws Exception {
        // Given a store whose last line is incomplete (such as after a crash while appending)
        Files.writeString(repositoryStore.toPath(), "key1=value1\nkey2=value2\nkey3");

        // When starting a FileStateRepository with that store
        FileStateRepository repository = createRepository();

        // Then it starts and has the complete entries
        assertEquals("value1", repository.getState("key1"));
        assertEquals("value2", repository.getState("key2"));
        assertNull(repository.getState("key3"));
    }

    @Test
    public void shouldKeepStoreWhenRewritingFails() throws Exception {
        // Given a FileStateRepository with some content, whose 1st level cache fails while it is written to the store
        FailingMap cache = new FailingMap();
        FileStateRepository repository = fileStateRepository(repositoryStore, cache);
        repository.start();
        repository.setState("key1", "value1");
        repository.setState("key2", "value2");
        repository.setState("key3", "value3");

        // When stopping it (which rewrites the store) and the rewrite fails half way
        cache.fail = true;
        assertThrows(IllegalStateException.class, repository::stop);

        // Then the store still has the previous content
        FileStateRepository newRepository = createRepository();
        assertEquals("value1", newRepository.getState("key1"));
        assertEquals("value2", newRepository.getState("key2"));
        assertEquals("value3", newRepository.getState("key3"));
    }

    @Test
    public void shouldNotLoseStateWhenUpdatedWhileStopping() throws Exception {
        // Given a FileStateRepository with some content
        CountDownLatch rewriting = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        PausingMap cache = new PausingMap(rewriting, resume);
        FileStateRepository repository = fileStateRepository(repositoryStore, cache);
        repository.start();
        for (int i = 0; i < 5; i++) {
            repository.setState("key" + i, "value" + i);
        }

        // When the state is updated by another thread while the repository is stopping (rewriting the store)
        AtomicReference<Exception> stopFailure = new AtomicReference<>();
        cache.pauseThread = new Thread(() -> {
            try {
                repository.stop();
            } catch (Exception e) {
                stopFailure.set(e);
            }
        });
        cache.pauseThread.start();
        assertTrue(rewriting.await(10, TimeUnit.SECONDS));

        Thread updater = new Thread(() -> repository.setState("key5", "value5"));
        updater.start();
        // the update must wait for the stop to complete
        await().atMost(10, TimeUnit.SECONDS).until(() -> updater.getState() == Thread.State.WAITING
                || updater.getState() == Thread.State.TERMINATED);
        resume.countDown();
        cache.pauseThread.join(10000);
        updater.join(10000);

        // Then stopping succeeded, and no state is lost
        assertNull(stopFailure.get());
        FileStateRepository newRepository = createRepository();
        for (int i = 0; i < 6; i++) {
            assertEquals("value" + i, newRepository.getState("key" + i));
        }
    }

    private FileStateRepository createRepository() {
        FileStateRepository repository = fileStateRepository(repositoryStore);
        repository.start();
        return repository;
    }

    /**
     * A cache which fails while its entries are iterated (when the store is rewritten), if requested.
     */
    private static final class FailingMap extends HashMap<String, String> {
        private volatile boolean fail;

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            Set<Map.Entry<String, String>> entries = super.entrySet();
            if (!fail) {
                return entries;
            }
            return new AbstractSet<>() {
                @Override
                public Iterator<Map.Entry<String, String>> iterator() {
                    Iterator<Map.Entry<String, String>> it = entries.iterator();
                    return new Iterator<>() {
                        private int count;

                        @Override
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        @Override
                        public Map.Entry<String, String> next() {
                            if (count++ == 1) {
                                throw new IllegalStateException("Simulated failure while rewriting the store");
                            }
                            return it.next();
                        }
                    };
                }

                @Override
                public int size() {
                    return entries.size();
                }
            };
        }
    }

    /**
     * A cache whose iteration (when the store is rewritten) by the given thread pauses after the first entry.
     */
    private static final class PausingMap extends HashMap<String, String> {
        private final CountDownLatch rewriting;
        private final CountDownLatch resume;
        private volatile Thread pauseThread;

        private PausingMap(CountDownLatch rewriting, CountDownLatch resume) {
            this.rewriting = rewriting;
            this.resume = resume;
        }

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            Set<Map.Entry<String, String>> entries = super.entrySet();
            if (Thread.currentThread() != pauseThread) {
                return entries;
            }
            return new AbstractSet<>() {
                @Override
                public Iterator<Map.Entry<String, String>> iterator() {
                    Iterator<Map.Entry<String, String>> it = entries.iterator();
                    return new Iterator<>() {
                        private int count;

                        @Override
                        public boolean hasNext() {
                            return it.hasNext();
                        }

                        @Override
                        public Map.Entry<String, String> next() {
                            Map.Entry<String, String> answer = it.next();
                            if (count++ == 0) {
                                rewriting.countDown();
                                try {
                                    resume.await(10, TimeUnit.SECONDS);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            return answer;
                        }
                    };
                }

                @Override
                public int size() {
                    return entries.size();
                }
            };
        }
    }
}
