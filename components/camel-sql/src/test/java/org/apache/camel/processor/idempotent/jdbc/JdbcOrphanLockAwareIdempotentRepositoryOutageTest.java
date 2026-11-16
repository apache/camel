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

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database outage longer than lockMaxAgeMillis: the keep-alive of node A fails, so the leases of the keys node A is
 * still processing grow older than lockMaxAgeMillis. Node A must still treat these keys as duplicates; another node can
 * take them over, as it cannot tell node A from a node that crashed (see the Orphan Lock section of the docs). Two
 * repository instances (two nodes) share one H2 database; only node A loses it.
 */
class JdbcOrphanLockAwareIdempotentRepositoryOutageTest {

    private static final String PROCESSOR_NAME = "APP_1";
    private static final long LOCK_MAX_AGE_MILLIS = 300_000L;

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private CamelContext context;
    private Outage outage;
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
        nodeA = newRepository(outage, LOCK_MAX_AGE_MILLIS, LOCK_MAX_AGE_MILLIS / 2);
        nodeB = newRepository(database, LOCK_MAX_AGE_MILLIS, LOCK_MAX_AGE_MILLIS / 2);
        nodeA.start();
        nodeB.start();
    }

    @AfterEach
    void tearDown() {
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
        List<String> warnings = new CopyOnWriteArrayList<>();
        List<String> errors = new CopyOnWriteArrayList<>();
        List<String> infos = new CopyOnWriteArrayList<>();
        // a short lock max age, so that the scheduled keep-alive fails for longer than it
        JdbcOrphanLockAwareIdempotentRepository node = newRepository(outage, 500L, 50L);
        node.log = recordingLogger(warnings, errors, infos);
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

    // records the format of the WARN, ERROR and INFO messages of the repository
    private static Logger recordingLogger(List<String> warnings, List<String> errors, List<String> infos) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] { Logger.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "recordingLogger";
                        case "warn":
                            warnings.add(String.valueOf(args[0]));
                            return null;
                        case "error":
                            errors.add(String.valueOf(args[0]));
                            return null;
                        case "info":
                            infos.add(String.valueOf(args[0]));
                            return null;
                        default:
                            break;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == String.class) {
                        return "test";
                    }
                    return null;
                });
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
