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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database outage longer than lockMaxAgeMillis: the keep-alive of node A fails, so the leases of the keys node A is
 * still processing grow older than lockMaxAgeMillis. Node A must still treat these keys as duplicates; another node can
 * take them over, as it cannot tell node A from a node that crashed (see the Orphan Lock section of the docs). Two
 * repository instances (two nodes) share one H2 database; only node A loses it. A statement of node A can be held
 * before it is sent, so that a copy of a message reaches node A while it confirms or removes the key.
 */
class JdbcOrphanLockAwareIdempotentRepositoryOutageTest {

    private static final String PROCESSOR_NAME = "APP_1";
    private static final long LOCK_MAX_AGE_MILLIS = 300_000L;

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private CamelContext context;
    private Outage outage;
    private PausingJdbcTemplate statements;
    private ExecutorService processing;
    private JdbcOrphanLockAwareIdempotentRepository nodeA;
    private JdbcOrphanLockAwareIdempotentRepository nodeB;

    @BeforeEach
    void setup() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        context = new DefaultCamelContext();
        outage = new Outage(database);
        statements = new PausingJdbcTemplate(outage);
        processing = Executors.newSingleThreadExecutor();
        nodeA = newRepository(outage, LOCK_MAX_AGE_MILLIS, LOCK_MAX_AGE_MILLIS / 2);
        // no statement is held unless a test says so
        nodeA.setJdbcTemplate(statements);
        nodeB = newRepository(database, LOCK_MAX_AGE_MILLIS, LOCK_MAX_AGE_MILLIS / 2);
        nodeA.start();
        nodeB.start();
    }

    @AfterEach
    void tearDown() {
        statements.resume();
        processing.shutdownNow();
        nodeA.shutdown();
        nodeB.shutdown();
        context.stop();
        database.shutdown();
    }

    @Test
    void testKeyInFlightIsStillADuplicateOnItsNodeAfterAnOutageLongerThanLockMaxAge() {
        // node A processes ORDER_1 (the Idempotent Consumer added it, and confirms it once the message is processed)
        assertTrue(nodeA.add("ORDER_1"));

        outage.begin();
        // the keep-alive fails (it logs an ERROR), and the outage lasts longer than the lock max age
        nodeA.keepAlive();
        assertThrows(RuntimeException.class, () -> nodeA.add("ORDER_1"));
        Timestamp expired = expireLeases();
        outage.end();

        // a copy of ORDER_1 (a redelivery, or the same file polled again) reaches node A, which still processes ORDER_1
        assertTrue(nodeA.contains("ORDER_1"), "A key that node A still holds must be a duplicate on node A");
        assertFalse(nodeA.add("ORDER_1"), "A key that node A still holds must not be taken over by node A");

        // the next keep-alive refreshes the lease, so the key is a lock of node A again for the other nodes
        nodeA.keepAlive();
        assertTrue(createdAt("ORDER_1").after(expired), "The keep-alive did not refresh the lease after the outage");
        assertFalse(nodeB.add("ORDER_1"));

        // once processed, the key is confirmed as usual
        assertTrue(nodeA.confirm("ORDER_1"));
        assertFalse(nodeA.add("ORDER_1"));
    }

    @Test
    void testKeyThatFailedDuringTheOutageIsStillADuplicateOnItsNode() {
        assertTrue(nodeA.add("ORDER_2"));

        outage.begin();
        // the processing of ORDER_2 fails, and neither the remove nor the confirm of the key reaches the database: the
        // key stays a lock of node A, which keeps it alive once the database is back
        assertThrows(RuntimeException.class, () -> nodeA.remove("ORDER_2"));
        assertFalse(nodeA.confirm("ORDER_2"));
        nodeA.keepAlive();
        expireLeases();
        outage.end();

        assertFalse(nodeA.add("ORDER_2"), "A key that node A still holds must not be taken over by node A");
        // the key can be removed once the database is back, and added again
        assertTrue(nodeA.remove("ORDER_2"));
        assertTrue(nodeA.add("ORDER_2"));
    }

    @Test
    void testKeyBeingConfirmedIsStillADuplicateOnItsNodeAfterAnOutage() throws Exception {
        assertTrue(nodeA.add("ORDER_5"));

        outage.begin();
        nodeA.keepAlive();
        expireLeases();
        outage.end();

        // ORDER_5 is processed right after the outage, before the next keep-alive: its confirm takes the key out of the
        // keep-alive first, then waits before its UPDATE (as when it waits for a connection of the pool)
        statements.pauseBefore(nodeA.getConfirmQuery());
        Future<Boolean> confirmed = processing.submit(() -> nodeA.confirm("ORDER_5"));
        assertTrue(statements.awaitPaused(), "The confirm did not reach its UPDATE");

        // a copy of ORDER_5 reaches node A meanwhile
        assertFalse(nodeA.add("ORDER_5"), "A key that node A is confirming must not be taken over by node A");
        assertTrue(nodeA.contains("ORDER_5"), "A key that node A is confirming must be a duplicate on node A");

        statements.resume();
        assertTrue(confirmed.get(10, TimeUnit.SECONDS));
        assertNull(createdAt("ORDER_5"), "The key must be confirmed");
        assertTrue(nodeA.getProcessorNameMessageIdSet().isEmpty(), "A confirmed key must no longer be kept alive");
        assertFalse(nodeA.add("ORDER_5"));
    }

    @Test
    void testKeyBeingRemovedIsStillADuplicateOnItsNodeUntilItsDelete() throws Exception {
        assertTrue(nodeA.add("ORDER_6"));

        outage.begin();
        nodeA.keepAlive();
        expireLeases();
        outage.end();

        // the processing of ORDER_6 fails right after the outage: the remove takes the key out of the keep-alive only
        // after its DELETE
        statements.pauseBefore(nodeA.getDeleteString());
        Future<Boolean> removed = processing.submit(() -> nodeA.remove("ORDER_6"));
        assertTrue(statements.awaitPaused(), "The remove did not reach its DELETE");

        assertFalse(nodeA.add("ORDER_6"), "A key that node A is removing must not be taken over by node A");

        statements.resume();
        assertTrue(removed.get(10, TimeUnit.SECONDS));
        // once removed, a copy is processed again, as the remove asks for
        assertTrue(nodeA.add("ORDER_6"));
    }

    @Test
    void testAnotherNodeTakesOverAKeyWhoseLeaseExpiredDuringTheOutage() {
        // the limitation of the lease, as documented: node B, which still reaches the database, cannot tell node A from
        // a node that crashed, so it takes over a lease that node A could not refresh for longer than the lock max age
        assertTrue(nodeA.add("ORDER_3"));

        outage.begin();
        nodeA.keepAlive();
        expireLeases();
        assertTrue(nodeB.add("ORDER_3"));
        outage.end();

        // node A, which may still process ORDER_3, does not take it a third time
        assertFalse(nodeA.add("ORDER_3"));
        assertFalse(nodeB.add("ORDER_3"));
    }

    @Test
    void testKeepAliveLogsOnceThatTheLeasesMayExpire() {
        RecordingLogger logs = new RecordingLogger();
        List<String> warnings = logs.warnings();
        List<String> errors = logs.errors();
        List<String> infos = logs.infos();
        // a short lock max age, so that the scheduled keep-alive fails for longer than it
        JdbcOrphanLockAwareIdempotentRepository node = newRepository(outage, 500L, 50L);
        node.log = logs.logger();
        node.start();
        try {
            assertTrue(node.add("ORDER_4"));

            outage.begin();
            await().atMost(10, TimeUnit.SECONDS).until(() -> !warnings.isEmpty());
            assertTrue(warnings.get(0).contains("lockMaxAgeMillis"), () -> "Unexpected WARN: " + warnings);
            int failed = errors.size();
            await().atMost(10, TimeUnit.SECONDS).until(() -> errors.size() >= failed + 3);
            assertEquals(1, warnings.size(), () -> "The keep-alive must log the WARN once per outage: " + warnings);

            outage.end();
            await().atMost(10, TimeUnit.SECONDS).until(() -> infos.stream().anyMatch(info -> info.contains("again")));
            assertFalse(node.add("ORDER_4"));
        } finally {
            node.shutdown();
        }
    }

    private JdbcOrphanLockAwareIdempotentRepository newRepository(
            DataSource dataSource, long lockMaxAgeMillis, long lockKeepAliveIntervalMillis) {
        JdbcOrphanLockAwareIdempotentRepository repository
                = new JdbcOrphanLockAwareIdempotentRepository(dataSource, PROCESSOR_NAME, context);
        repository.setLockMaxAgeMillis(lockMaxAgeMillis);
        // with the default lock max age the test calls keepAlive() itself
        repository.setLockKeepAliveIntervalMillis(lockKeepAliveIntervalMillis);
        return repository;
    }

    // the time passes: every lease is older than the lock max age, as no node could refresh it
    private Timestamp expireLeases() {
        Timestamp expired = new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS - 60_000L);
        jdbcTemplate.update("UPDATE CAMEL_MESSAGEPROCESSED SET createdAt = ? WHERE createdAt IS NOT NULL", expired);
        return expired;
    }

    private Timestamp createdAt(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT createdAt FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?", Timestamp.class,
                PROCESSOR_NAME, key);
    }

    /**
     * Holds the next run of a statement before it is sent, until resumed.
     */
    private static final class PausingJdbcTemplate extends JdbcTemplate {

        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch resumed = new CountDownLatch(1);
        private volatile String pausedSql;

        PausingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        void pauseBefore(String sql) {
            pausedSql = sql;
        }

        boolean awaitPaused() throws InterruptedException {
            return paused.await(10, TimeUnit.SECONDS);
        }

        void resume() {
            resumed.countDown();
        }

        @Override
        public int update(String sql, Object... args) {
            // the repository may add a condition to the statement (the remove of a key it holds)
            String pauseAt = pausedSql;
            if (pauseAt != null && sql.startsWith(pauseAt)) {
                pausedSql = null;
                paused.countDown();
                try {
                    if (!resumed.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("The paused statement was not resumed");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
            }
            return super.update(sql, args);
        }
    }

    /**
     * The database as node A sees it: unreachable during an outage, so every statement fails as it cannot get a
     * connection.
     */
    private static final class Outage extends DelegatingDataSource {

        private volatile boolean down;

        Outage(EmbeddedDatabase database) {
            super(database);
        }

        void begin() {
            down = true;
        }

        void end() {
            down = false;
        }

        @Override
        public Connection getConnection() throws SQLException {
            if (down) {
                throw new SQLException("Connection refused: the database is unreachable");
            }
            return super.getConnection();
        }
    }
}
