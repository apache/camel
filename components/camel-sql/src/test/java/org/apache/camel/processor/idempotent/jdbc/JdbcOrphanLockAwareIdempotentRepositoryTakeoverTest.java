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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Node A cannot reach the database for longer than lockMaxAgeMillis, so node B takes over a key that node A still
 * processes (the documented limitation: the message is processed twice, once on each node). Node A must then leave the
 * lease of node B alone: its remove must not delete it (a third node C would take the key while node B still processes
 * it), and its keep-alive must not refresh it (node C must take it over once node B crashes). A confirm of node A
 * confirms the key, as the message was processed, and node B must not remove the confirmed key then. Three repository
 * instances (three nodes) share one H2 database, each with its scheduled keep-alive; only node A loses the database.
 */
class JdbcOrphanLockAwareIdempotentRepositoryTakeoverTest {

    private static final String PROCESSOR_NAME = "APP_1";
    private static final long LOCK_MAX_AGE_MILLIS = 1000L;
    private static final long LOCK_KEEP_ALIVE_INTERVAL_MILLIS = 40L;

    private EmbeddedDatabase database;
    private JdbcTemplate jdbcTemplate;
    private CamelContext context;
    private Outage outage;
    private RecordingLogger logsOfA;
    private JdbcOrphanLockAwareIdempotentRepository nodeA;
    private JdbcOrphanLockAwareIdempotentRepository nodeB;
    private JdbcOrphanLockAwareIdempotentRepository nodeC;

    @BeforeEach
    void setup() {
        database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(database);
        context = new DefaultCamelContext();
        outage = new Outage(database);
        logsOfA = new RecordingLogger();
        nodeA = newRepository(outage);
        nodeA.log = logsOfA.logger();
        nodeB = newRepository(database);
        nodeC = newRepository(database);
        nodeA.start();
        nodeB.start();
        nodeC.start();
    }

    @AfterEach
    void tearDown() {
        nodeA.shutdown();
        nodeB.shutdown();
        nodeC.shutdown();
        context.stop();
        database.shutdown();
    }

    @Test
    void testOldHolderDoesNotRemoveTheLeaseOfTheNewHolder() {
        assertTrue(nodeA.add("ORDER_1"));
        nodeBTakesOverDuringAnOutageOfNodeA("ORDER_1");
        assertFalse(nodeC.add("ORDER_1"), "The lease of node B must protect the key");

        // the processing of ORDER_1 fails on node A, so it removes the key (removeOnFailure; also the remove of a file
        // read lock with readLockRemoveOnCommit=true once the file is processed)
        assertFalse(nodeA.remove("ORDER_1"), "Node A must not delete the lease that node B took over");
        assertNotNull(createdAt("ORDER_1"), "The lease of node B must still be there");
        assertTrue(logsOfA.warnings().stream().anyMatch(warning -> warning.contains("another instance took it over")),
                () -> "Node A must log that it does not remove the key: " + logsOfA.warnings());
        assertFalse(nodeC.add("ORDER_1"), "The key must not be taken a third time while node B processes it");

        // node B processes ORDER_1 as usual
        assertTrue(nodeB.confirm("ORDER_1"));
        assertNull(createdAt("ORDER_1"));
        assertFalse(nodeA.add("ORDER_1"));
        assertFalse(nodeC.add("ORDER_1"));
    }

    @Test
    void testNewHolderDoesNotRemoveAKeyThatTheOldHolderConfirmed() {
        assertTrue(nodeA.add("ORDER_2"));
        nodeBTakesOverDuringAnOutageOfNodeA("ORDER_2");

        // the processing of ORDER_2 succeeds on node A: its confirm confirms the key, as the message was processed
        assertTrue(nodeA.confirm("ORDER_2"));
        assertNull(createdAt("ORDER_2"));

        // the processing of ORDER_2 fails on node B: its remove must not delete the confirmed key
        assertFalse(nodeB.remove("ORDER_2"), "Node B must not delete a key that node A confirmed");
        assertEquals(1, rows("ORDER_2"));
        assertNull(createdAt("ORDER_2"));
        assertFalse(nodeC.add("ORDER_2"), "A confirmed key must stay a duplicate");
    }

    @Test
    void testOldHolderDoesNotKeepAliveTheLeaseOfTheNewHolder() {
        assertTrue(nodeA.add("ORDER_3"));
        nodeBTakesOverDuringAnOutageOfNodeA("ORDER_3");

        // node B crashes while it processes ORDER_3, while node A still processes it and its keep-alive runs again: the
        // lease of node B must expire, so that node C takes the key over, as for any node that crashed
        nodeB.stop();
        await().atMost(10, TimeUnit.SECONDS).pollInterval(20, TimeUnit.MILLISECONDS)
                .until(() -> nodeC.add("ORDER_3"));
        await().atMost(10, TimeUnit.SECONDS).pollInterval(20, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> assertTrue(
                        logsOfA.warnings().stream().anyMatch(warning -> warning.contains("no longer keeps the key")),
                        () -> "Node A must log that it no longer keeps the key alive: " + logsOfA.warnings()));

        // node A, which still treats the key as a duplicate, leaves the lease of node C alone too
        assertFalse(nodeA.add("ORDER_3"));
        assertFalse(nodeA.remove("ORDER_3"));
        assertNotNull(createdAt("ORDER_3"));
    }

    @Test
    void testLeaseWrittenBeforeTheEndOfDaylightSavingTimeStillMatches() {
        // a createdAt column without time zone holds the local time of the JVM, which goes back an hour on 2026-10-25 at
        // 03:00 in Berlin: the lease of this instance written shortly before must still be earlier than the bound, in
        // local time too. The same around the start of daylight saving time, and on a day without change
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        long margin = (LOCK_MAX_AGE_MILLIS + LOCK_KEEP_ALIVE_INTERVAL_MILLIS) / 2;
        for (String change : List.of("2026-10-25T01:00:00Z", "2026-03-29T01:00:00Z", "2026-06-01T01:00:00Z")) {
            long at = Instant.parse(change).toEpochMilli();
            for (long writtenAt = at - 2 * margin; writtenAt <= at + 2 * margin; writtenAt += 100) {
                Timestamp bound = JdbcOrphanLockAwareIdempotentRepository.ownedBefore(writtenAt, margin, berlin);
                LocalDateTime written = LocalDateTime.ofInstant(Instant.ofEpochMilli(writtenAt), berlin);
                LocalDateTime localBound = LocalDateTime.ofInstant(bound.toInstant(), berlin);
                assertFalse(localBound.isBefore(written.plusNanos(margin * 1_000_000)),
                        "The lease written at " + written + " must be earlier than the bound in local time: " + localBound);
                assertTrue(bound.getTime() >= writtenAt + margin,
                        "The lease written at " + Instant.ofEpochMilli(writtenAt) + " must be earlier than the bound: "
                                                                  + bound.toInstant());
            }
        }
    }

    // node A cannot refresh its lease; once the lease is older than the lock max age, node B takes the key over (as it
    // cannot tell node A from a node that crashed), and node A reaches the database again
    private void nodeBTakesOverDuringAnOutageOfNodeA(String key) {
        outage.begin();
        await().atMost(10, TimeUnit.SECONDS).pollInterval(20, TimeUnit.MILLISECONDS)
                .until(() -> nodeB.add(key));
        outage.end();
    }

    private JdbcOrphanLockAwareIdempotentRepository newRepository(DataSource dataSource) {
        JdbcOrphanLockAwareIdempotentRepository repository
                = new JdbcOrphanLockAwareIdempotentRepository(dataSource, PROCESSOR_NAME, context);
        repository.setLockMaxAgeMillis(LOCK_MAX_AGE_MILLIS);
        repository.setLockKeepAliveIntervalMillis(LOCK_KEEP_ALIVE_INTERVAL_MILLIS);
        return repository;
    }

    private Timestamp createdAt(String key) {
        List<Timestamp> createdAt = jdbcTemplate.queryForList(
                "SELECT createdAt FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?", Timestamp.class,
                PROCESSOR_NAME, key);
        assertEquals(1, createdAt.size(), () -> "No row for the key " + key);
        return createdAt.get(0);
    }

    private int rows(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?", Integer.class,
                PROCESSOR_NAME, key);
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
