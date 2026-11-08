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
package org.apache.camel.processor.keyvalue.jdbc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.apache.camel.support.KeyValueRepositoryHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two repositories (two nodes) on one database. The statements of node A can be paused, so that the calls of both nodes
 * interleave in a given order.
 */
class JdbcKeyValueRepositoryConcurrencyTest {

    private EmbeddedDatabase dataSource;
    private PausingJdbcTemplate jdbcA;
    private JdbcKeyValueRepository nodeA;
    private JdbcKeyValueRepository nodeB;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private void startNodes(int isolationLevel) throws Exception {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcA = new PausingJdbcTemplate(dataSource);
        nodeA = new JdbcKeyValueRepository(jdbcA, transactionTemplate(isolationLevel));
        nodeB = new JdbcKeyValueRepository(new JdbcTemplate(dataSource), transactionTemplate(isolationLevel));
        nodeA.init();
        nodeA.start();
        nodeB.init();
        nodeB.start();
    }

    private TransactionTemplate transactionTemplate(int isolationLevel) {
        TransactionTemplate template = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        template.setIsolationLevel(isolationLevel);
        return template;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (jdbcA != null) {
            jdbcA.releaseAll();
        }
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        if (nodeA != null) {
            nodeA.stop();
            nodeB.stop();
        }
        if (dataSource != null) {
            dataSource.shutdown();
        }
    }

    @Test
    void putIfAbsentHasOneWinnerUnderRepeatableRead() throws Exception {
        startNodes(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        Gate beforeInsert = jdbcA.pause("INSERT", 1);

        Future<Object> a = executor.submit(() -> nodeA.putIfAbsent("order-1", "A", null));
        // A has read the key (absent), so its snapshot does not show what B stores now
        beforeInsert.awaitReached();
        assertNull(nodeB.putIfAbsent("order-1", "B", null), "B stores the key first");
        // the INSERT of A fails with a duplicate key
        beforeInsert.release();

        assertEquals("B", a.get(10, TimeUnit.SECONDS), "A lost the race and must get the value of B");
        assertEquals("B", nodeA.get("order-1"));
    }

    @Test
    void putIfAbsentDoesNotSucceedWithoutStoringTheValue() throws Exception {
        startNodes(TransactionDefinition.ISOLATION_READ_COMMITTED);
        Gate beforeInsert = jdbcA.pause("INSERT", 1);
        Gate beforeSecondRead = jdbcA.pause("SELECT", 2);

        Future<Object> a = executor.submit(() -> nodeA.putIfAbsent("lease-1", "A", null));
        beforeInsert.awaitReached();
        assertNull(nodeB.putIfAbsent("lease-1", "B", null), "B stores the key first");
        // the INSERT of A fails with a duplicate key, then A reads the key again
        beforeInsert.release();
        beforeSecondRead.awaitReached();
        assertEquals("B", nodeB.delete("lease-1"), "B releases the key");
        beforeSecondRead.release();

        // the key was free when A read it again, so A may get it, but then its value must be stored
        assertNull(a.get(10, TimeUnit.SECONDS));
        assertEquals("A", nodeB.get("lease-1"), "A was told that its put succeeded");
    }

    @Test
    void readingAnExpiredEntryDoesNotDeleteTheEntryStoredMeanwhile() throws Exception {
        startNodes(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // an entry that expired long ago
        nodeB.getJdbcTemplate().update(nodeB.getInsertString(), "job-42", KeyValueRepositoryHelper.serialize("old"), 1L);
        Gate beforeDelete = jdbcA.pause("DELETE", 1);

        Future<Object> a = executor.submit(() -> nodeA.get("job-42"));
        // A has read the entry as expired and is about to delete it
        beforeDelete.awaitReached();
        assertNull(nodeB.putIfAbsent("job-42", "B", null), "the expired entry is absent, so B gets the key");
        beforeDelete.release();

        assertNull(a.get(10, TimeUnit.SECONDS), "A read the entry while it was expired");
        assertEquals("B", nodeB.get("job-42"), "the entry stored by B must be kept");
        assertEquals("B", nodeA.putIfAbsent("job-42", "A", null), "B still holds the key");
    }

    @Test
    void putIfAbsentGivesUpAfterThreeDuplicateKeys() throws Exception {
        startNodes(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertNull(nodeB.putIfAbsent("job-1", "B", null));
        // node A does not find the stored entry, so each of its INSERTs fails with a duplicate key
        nodeA.setSelectString(nodeA.getSelectString() + " AND 1 = 0");

        assertThrows(DuplicateKeyException.class, () -> nodeA.putIfAbsent("job-1", "A", null));
        assertEquals(3, jdbcA.statements("INSERT"));
    }

    @Test
    void putIfAbsentInTransactionOfCallerDoesNotTryAgain() throws Exception {
        startNodes(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertNull(nodeB.putIfAbsent("job-1", "B", null));
        nodeA.setSelectString(nodeA.getSelectString() + " AND 1 = 0");
        TransactionTemplate caller = transactionTemplate(TransactionDefinition.ISOLATION_READ_COMMITTED);

        // the transaction of the caller cannot go on after the duplicate key, so the key is not read again in it
        assertThrows(DuplicateKeyException.class,
                () -> caller.executeWithoutResult(status -> nodeA.putIfAbsent("job-1", "A", null)));
        assertEquals(1, jdbcA.statements("INSERT"));
    }

    /**
     * Pauses the n-th statement of a {@link PausingJdbcTemplate} that starts with the given SQL verb, until released.
     */
    private static final class Gate {
        private final String verb;
        private final int occurrence;
        private final AtomicInteger seen = new AtomicInteger();
        private final CountDownLatch reached = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);

        private Gate(String verb, int occurrence) {
            this.verb = verb;
            this.occurrence = occurrence;
        }

        private void beforeStatement(String sql) {
            if (sql.startsWith(verb) && seen.incrementAndGet() == occurrence) {
                reached.countDown();
                await(released);
            }
        }

        void awaitReached() {
            await(reached);
        }

        void release() {
            released.countDown();
        }

        private static void await(CountDownLatch latch) {
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timeout waiting for the other node");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static final class PausingJdbcTemplate extends JdbcTemplate {
        private final List<Gate> gates = new CopyOnWriteArrayList<>();
        private final List<String> executed = new CopyOnWriteArrayList<>();

        private PausingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        Gate pause(String verb, int occurrence) {
            Gate gate = new Gate(verb, occurrence);
            gates.add(gate);
            return gate;
        }

        long statements(String verb) {
            return executed.stream().filter(sql -> sql.startsWith(verb)).count();
        }

        void releaseAll() {
            gates.forEach(Gate::release);
        }

        private void beforeStatement(String sql) {
            executed.add(sql);
            gates.forEach(gate -> gate.beforeStatement(sql));
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            beforeStatement(sql);
            return super.queryForObject(sql, rowMapper, args);
        }

        @Override
        public int update(String sql, Object... args) {
            beforeStatement(sql);
            return super.update(sql, args);
        }
    }
}
