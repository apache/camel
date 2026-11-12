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
package org.apache.camel.processor.idempotent.jdbc;

import java.sql.Timestamp;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A key is cached only once the transaction that inserted its row commits. In a transaction of the caller that rolls
 * back (for example a transacted route), the row is gone, so the redelivery of the message must not be rejected as a
 * duplicate from the cache.
 */
public class JdbcCachedMessageIdRepositoryTransactionTest {

    private static final String PROCESSOR_NAME = "myProcessorName";
    private static final String KEY = "KEY";

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private JdbcCachedMessageIdRepository repository;

    @BeforeEach
    public void setUp() throws Exception {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcCachedMessageIdRepository(dataSource, PROCESSOR_NAME);
        repository.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (repository != null) {
            repository.stop();
        }
        if (dataSource != null) {
            dataSource.shutdown();
        }
    }

    @Test
    public void addInACallerTransactionThatRollsBackIsNotCached() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            status.setRollbackOnly();
        });

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY), "the key of a rolled back add must not be a duplicate");
        // the redelivery of the message is processed
        assertTrue(repository.add(KEY));
        assertEquals(1, rows());
    }

    @Test
    public void addInACallerTransactionIsCachedAfterTheCommit() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            // not cached before the commit: contains reads the row
            int misses = repository.getMissCount();
            assertTrue(repository.contains(KEY));
            assertEquals(misses + 1, repository.getMissCount());
        });
        int hits = repository.getHitCount();

        assertEquals(1, rows());
        assertTrue(repository.contains(KEY));
        assertEquals(hits + 1, repository.getHitCount(), "the committed key must be answered from the cache");
    }

    @Test
    public void addIsCached() {
        assertTrue(repository.add(KEY));
        int hits = repository.getHitCount();

        assertFalse(repository.add(KEY));
        assertEquals(hits + 1, repository.getHitCount());
    }

    @Test
    public void addAndRemoveInOneCallerTransactionIsNotCached() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            assertTrue(repository.remove(KEY));
        });

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY));
        assertTrue(repository.add(KEY));
    }

    @Test
    public void addAndClearInOneCallerTransactionIsNotCached() {
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.add(KEY));
            repository.clear();
        });

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY));
        assertTrue(repository.add(KEY));
    }

    @Test
    public void removeInACallerTransactionEvictsTheKeyThatAnotherThreadCachedBeforeTheCommit() throws Exception {
        assertTrue(repository.add(KEY));
        // the other thread adds the key again while the transaction that deletes its row is open: it still sees the
        // row, so its add is a duplicate, and it caches the key
        assertTrue(removeInAnOpenTransactionWhile(() -> assertFalse(repository.add(KEY)), false));

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY), "the key whose row was deleted must not stay a duplicate in the cache");
        assertTrue(repository.add(KEY));
        assertEquals(1, rows());
    }

    @Test
    public void clearInACallerTransactionEvictsTheKeyThatAnotherThreadCachedBeforeTheCommit() throws Exception {
        assertTrue(repository.add(KEY));
        assertTrue(removeInAnOpenTransactionWhile(() -> assertFalse(repository.add(KEY)), true));

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY), "the key whose row was deleted must not stay a duplicate in the cache");
        assertTrue(repository.add(KEY));
    }

    @Test
    public void removeInACallerTransactionThatRollsBackKeepsTheRow() {
        assertTrue(repository.add(KEY));
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.remove(KEY));
            status.setRollbackOnly();
        });

        assertEquals(1, rows());
        assertTrue(repository.contains(KEY));
        assertFalse(repository.add(KEY));
    }

    @Test
    public void removeAndAddInOneCallerTransactionIsCached() {
        assertTrue(repository.add(KEY));
        repository.transactionTemplate.executeWithoutResult(status -> {
            assertTrue(repository.remove(KEY));
            assertTrue(repository.add(KEY));
        });
        int hits = repository.getHitCount();

        assertEquals(1, rows());
        assertTrue(repository.contains(KEY));
        assertEquals(hits + 1, repository.getHitCount(), "the key added after the remove must be cached at the commit");
    }

    @Test
    public void addOnAnotherThreadThatReadTheRowBeforeARemoveIsNotCached() throws Exception {
        // the row is there, but not in the cache (such as a row of another node), so the add reads it
        insertRowNotCached();
        int misses = repository.getMissCount();
        // the other thread reads the row (its add is a duplicate), the remove deletes the row and evicts the key, and
        // only then does the other thread cache the key, when its transaction commits
        assertFalse(inAnOpenTransactionWhile(() -> repository.add(KEY), () -> assertTrue(repository.remove(KEY))));
        assertEquals(misses + 1, repository.getMissCount(), "the add must have read the row");

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY), "the key whose row was deleted must not stay a duplicate in the cache");
        assertTrue(repository.add(KEY));
        assertEquals(1, rows());
    }

    @Test
    public void addOnAnotherThreadThatReadTheRowBeforeAClearIsNotCached() throws Exception {
        insertRowNotCached();
        assertFalse(inAnOpenTransactionWhile(() -> repository.add(KEY), repository::clear));

        assertEquals(0, rows());
        assertFalse(repository.contains(KEY), "the key whose row was deleted must not stay a duplicate in the cache");
        assertTrue(repository.add(KEY));
    }

    @Test
    public void addOnAnotherThreadIsCachedWhenNoRowIsRemovedMeanwhile() throws Exception {
        assertTrue(inAnOpenTransactionWhile(() -> repository.add(KEY), () -> assertFalse(repository.remove("OTHER"))));
        int hits = repository.getHitCount();

        assertTrue(repository.contains(KEY));
        assertEquals(hits + 1, repository.getHitCount(), "the committed key must be answered from the cache");
    }

    @Test
    public void clearAndAddInOneCallerTransactionKeepsTheRow() {
        assertTrue(repository.add(KEY));
        repository.transactionTemplate.executeWithoutResult(status -> {
            repository.clear();
            assertTrue(repository.add(KEY));
        });

        assertEquals(1, rows());
        assertTrue(repository.contains(KEY));
        assertFalse(repository.add(KEY));
    }

    /**
     * Removes the key (or clears) in a transaction of the caller on another thread, runs the action on this thread
     * while that transaction is open, and then lets it commit. Returns whether the remove returned true.
     */
    private boolean removeInAnOpenTransactionWhile(Runnable action, boolean clear) throws Exception {
        return inAnOpenTransactionWhile(() -> {
            if (clear) {
                repository.clear();
                return true;
            }
            return repository.remove(KEY);
        }, action);
    }

    /**
     * Runs the step in a transaction of the caller on another thread, runs the action on this thread while that
     * transaction is open, and then lets it commit. Returns the result of the step.
     */
    private boolean inAnOpenTransactionWhile(BooleanSupplier step, Runnable action) throws Exception {
        CountDownLatch stepped = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        AtomicReference<Boolean> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                repository.transactionTemplate.executeWithoutResult(status -> {
                    result.set(step.getAsBoolean());
                    stepped.countDown();
                    try {
                        assertTrue(commit.await(20, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                });
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                stepped.countDown();
            }
        }, "caller-transaction");
        caller.start();
        try {
            assertTrue(stepped.await(20, TimeUnit.SECONDS));
            action.run();
        } finally {
            commit.countDown();
            caller.join(TimeUnit.SECONDS.toMillis(20));
        }
        assertFalse(caller.isAlive());
        if (failure.get() != null) {
            throw new AssertionError("the transaction of the caller failed", failure.get());
        }
        return result.get();
    }

    private void insertRowNotCached() {
        jdbcTemplate.update("INSERT INTO CAMEL_MESSAGEPROCESSED (processorName, messageId, createdAt) VALUES (?, ?, ?)",
                PROCESSOR_NAME, KEY, new Timestamp(System.currentTimeMillis()));
        assertEquals(1, rows());
    }

    private int rows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ?",
                Integer.class, PROCESSOR_NAME);
    }
}
