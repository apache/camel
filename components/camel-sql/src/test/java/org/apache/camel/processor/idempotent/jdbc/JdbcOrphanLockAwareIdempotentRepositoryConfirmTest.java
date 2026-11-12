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
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.idempotent.jdbc.JdbcOrphanLockAwareIdempotentRepository.ProcessorNameAndMessageId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A key that was processed and confirmed stays a duplicate after the node that added it stops, while a lease that was
 * not confirmed is still taken over. Two repository instances (two nodes) share one H2 database.
 */
class JdbcOrphanLockAwareIdempotentRepositoryConfirmTest {

    private static final String PROCESSOR_NAME = "APP_1";
    private static final long LOCK_MAX_AGE_MILLIS = 300_000L;

    private EmbeddedDatabase dataSource;
    private JdbcTemplate jdbcTemplate;
    private CamelContext context;
    private JdbcOrphanLockAwareIdempotentRepository nodeA;
    private JdbcOrphanLockAwareIdempotentRepository nodeB;

    @BeforeEach
    void setup() {
        dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        context = new DefaultCamelContext();
        nodeA = newRepository();
        nodeB = newRepository();
        nodeA.start();
        nodeB.start();
    }

    @AfterEach
    void tearDown() {
        nodeA.shutdown();
        nodeB.shutdown();
        context.stop();
        dataSource.shutdown();
    }

    @Test
    void testConfirmedKeyStaysADuplicateAfterItsNodeStops() {
        assertTrue(nodeA.add("FILE_1"));
        assertTrue(nodeA.confirm("FILE_1"));

        // node A stops (a rolling restart, or a crash), and the lock max age passes
        nodeA.stop();
        ageLeases();

        assertTrue(nodeB.contains("FILE_1"));
        assertFalse(nodeB.add("FILE_1"), "A processed and confirmed key must not be taken over");
    }

    @Test
    void testLeaseThatIsNotConfirmedIsTakenOverAfterItsNodeStops() {
        // control: the orphan lock of an instance that stopped while processing is still taken over
        assertTrue(nodeA.add("FILE_2"));

        nodeA.stop();
        ageLeases();

        assertFalse(nodeB.contains("FILE_2"));
        assertTrue(nodeB.add("FILE_2"));
    }

    @Test
    void testConfirmedKeyIsNoLongerKeptAlive() {
        assertTrue(nodeA.add("FILE_3"));
        assertTrue(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_3")));

        assertTrue(nodeA.confirm("FILE_3"));
        assertFalse(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_3")),
                "A confirmed key must not be kept alive");

        // a keep-alive does not make the confirmed key a lease again
        nodeA.keepAlive();
        assertNull(createdAt("FILE_3"));
    }

    @Test
    void testKeyAddedAndConfirmedInOneTransactionIsNotKeptAlive() {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            assertTrue(nodeA.add("FILE_4"));
            assertTrue(nodeA.confirm("FILE_4"));
        });

        assertFalse(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_4")),
                "The add of the same transaction must not keep the confirmed key alive after the commit");
        nodeA.stop();
        ageLeases();
        assertFalse(nodeB.add("FILE_4"));
    }

    @Test
    void testConfirmThatRollsBackKeepsTheLeaseAlive() {
        assertTrue(nodeA.add("FILE_7"));

        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> {
            assertTrue(nodeA.confirm("FILE_7"));
            // the key leaves the keep-alive right away, as in a remove, so a later keep-alive does not wait for its row
            assertFalse(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_7")),
                    "A key confirmed in a transaction of the caller must leave the keep-alive right away");
            status.setRollbackOnly();
        });

        // the confirm did not happen, so the lease is kept alive again while this instance runs
        assertTrue(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_7")),
                "A confirm that rolled back must keep the lease alive again");
        assertNotNull(createdAt("FILE_7"));
    }

    @Test
    void testConfirmOfAKeyWhoseRowWasDeletedDoesNotKeepItAlive() {
        assertTrue(nodeA.add("FILE_15"));
        // removed meanwhile, for example by a clear of another instance
        jdbcTemplate.update("DELETE FROM CAMEL_MESSAGEPROCESSED WHERE messageId = 'FILE_15'");

        assertFalse(nodeA.confirm("FILE_15"));
        assertFalse(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_15")),
                "A key without a row must not be kept alive, as another instance may insert it later");
    }

    @Test
    void testKeepAliveDoesNotWaitForAConfirmInACallerTransaction() throws Exception {
        assertTrue(nodeA.add("FILE_12"));
        assertTrue(nodeA.add("FILE_13"));
        Timestamp old = new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS / 2);
        jdbcTemplate.update("UPDATE CAMEL_MESSAGEPROCESSED SET createdAt = ? WHERE messageId = 'FILE_13'", old);

        ExecutorService keeper = Executors.newSingleThreadExecutor();
        try {
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.executeWithoutResult(status -> {
                // the confirm locks the row of FILE_12 until this transaction commits
                assertTrue(nodeA.confirm("FILE_12"));
                try {
                    // a keep-alive meanwhile refreshes FILE_13, and does not wait for the row of FILE_12 (which would end
                    // in a lock timeout of the whole batch)
                    keeper.submit(nodeA::keepAlive).get(30, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                Timestamp refreshed = createdAt("FILE_13");
                assertTrue(refreshed.after(old), "The keep-alive must refresh the other leases while the confirm is open");
            });
        } finally {
            keeper.shutdownNow();
        }
        assertNull(createdAt("FILE_12"));
    }

    @Test
    void testCheckAtStartWithAConfirmQueryThatDoesNotStoreNull() {
        // as a MySQL TIMESTAMP column with explicit_defaults_for_timestamp=OFF, which stores the current time for null
        List<String> warnings = new CopyOnWriteArrayList<>();
        JdbcOrphanLockAwareIdempotentRepository node = newRepository();
        node.log = recordingLogger(warnings);
        node.setConfirmQuery(
                "UPDATE CAMEL_MESSAGEPROCESSED SET createdAt = CURRENT_TIMESTAMP WHERE processorName = ? AND messageId = ?");
        node.start();
        try {
            assertEquals(1, warnings.size(), "The check at start must log one WARN: " + warnings);
            assertTrue(node.add("FILE_8"));
            Timestamp leased = new Timestamp(System.currentTimeMillis() - 60_000L);
            jdbcTemplate.update("UPDATE CAMEL_MESSAGEPROCESSED SET createdAt = ? WHERE messageId = 'FILE_8'", leased);

            // the key is not confirmed, and no statement is sent: the key stays a lock that is kept alive, as before
            assertTrue(node.confirm("FILE_8"));
            assertTrue(node.getProcessorNameMessageIdSet().contains(id("FILE_8")),
                    "A key that is not confirmed must still be kept alive");
            assertEquals(leased, createdAt("FILE_8"), "No confirm statement must be sent");
            assertEquals(1, warnings.size(), "A confirm must not log again: " + warnings);
        } finally {
            node.shutdown();
        }
    }

    @Test
    void testCheckAtStartWithANotNullColumnKeepsTheCallerTransactionCommittable() {
        jdbcTemplate.execute("CREATE TABLE CAMEL_MESSAGEPROCESSED_NN (processorName VARCHAR(255), messageId VARCHAR(100), "
                             + "createdAt TIMESTAMP NOT NULL, PRIMARY KEY (processorName, messageId))");
        List<String> warnings = new CopyOnWriteArrayList<>();
        JdbcOrphanLockAwareIdempotentRepository node = newRepository();
        node.log = recordingLogger(warnings);
        node.setTableName("CAMEL_MESSAGEPROCESSED_NN");
        node.start();
        try {
            assertEquals(1, warnings.size(), "The check at start must log one WARN: " + warnings);
            assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED_NN", Integer.class),
                    "The check at start must roll back its row");

            // a transacted route with completionEager=true on the same DataSource: the confirm must not mark the
            // transaction of the caller as rollback only, so that it commits as before
            TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            transaction.executeWithoutResult(status -> {
                assertTrue(node.add("FILE_9"));
                assertTrue(node.confirm("FILE_9"));
            });

            assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM CAMEL_MESSAGEPROCESSED_NN", Integer.class));
            assertTrue(node.getProcessorNameMessageIdSet().contains(id("FILE_9")),
                    "A key that is not confirmed must still be kept alive");
        } finally {
            node.shutdown();
        }
    }

    @Test
    void testConfirmThatFailsLogsAWarnEachTime() {
        List<String> warnings = new CopyOnWriteArrayList<>();
        nodeA.log = recordingLogger(warnings);
        assertTrue(nodeA.add("FILE_9"));
        assertTrue(nodeA.add("FILE_14"));
        // the column no longer stores null after the start (a failure the check at start cannot see)
        jdbcTemplate.execute("ALTER TABLE CAMEL_MESSAGEPROCESSED ALTER COLUMN createdAt SET NOT NULL");

        assertFalse(nodeA.confirm("FILE_9"));
        assertFalse(nodeA.confirm("FILE_14"));

        assertTrue(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_9")),
                "A key that could not be confirmed must still be kept alive");
        assertTrue(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_14")));
        assertTrue(nodeA.contains("FILE_9"));
        assertEquals(2, warnings.size(), "Each failed confirm must log a WARN: " + warnings);
        assertFalse(warnings.get(1).contains("nullable"), "A failed confirm must not log the hint of the check at start");
    }

    @Test
    void testNoConfirmQueryKeepsConfirmedKeysAsLocks() {
        // the way back to the behaviour before: a confirmed key stays a lock
        nodeA.setConfirmQuery(null);
        assertTrue(nodeA.add("FILE_10"));
        assertTrue(nodeA.confirm("FILE_10"));
        assertTrue(nodeA.getProcessorNameMessageIdSet().contains(id("FILE_10")));

        nodeA.stop();
        ageLeases();
        assertTrue(nodeB.add("FILE_10"));
    }

    @Test
    void testConfirmWithACustomTableName() {
        JdbcOrphanLockAwareIdempotentRepository ordersA = newRepository();
        JdbcOrphanLockAwareIdempotentRepository ordersB = newRepository();
        ordersA.setTableName("CAMEL_MESSAGEPROCESSED_ORDERS");
        ordersB.setTableName("CAMEL_MESSAGEPROCESSED_ORDERS");
        ordersA.start();
        ordersB.start();
        try {
            assertTrue(ordersA.add("ORDER_1"));
            assertTrue(ordersA.confirm("ORDER_1"));

            ordersA.stop();
            jdbcTemplate.update("UPDATE CAMEL_MESSAGEPROCESSED_ORDERS SET createdAt = ? WHERE createdAt IS NOT NULL",
                    new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS - 60_000L));
            assertFalse(ordersB.add("ORDER_1"));
        } finally {
            ordersA.shutdown();
            ordersB.shutdown();
        }
    }

    @Test
    void testConfirmOfAnUnknownKey() {
        assertFalse(nodeA.confirm("FILE_5"));
        assertTrue(nodeA.add("FILE_5"));
    }

    @Test
    void testRemovedKeyCanBeAddedAgain() {
        assertTrue(nodeA.add("FILE_6"));
        assertTrue(nodeA.confirm("FILE_6"));
        assertTrue(nodeA.remove("FILE_6"));

        assertTrue(nodeB.add("FILE_6"));
    }

    @Test
    void testIdempotentConsumerSkipsADuplicateAfterTheNodeThatProcessedItStopped() throws Exception {
        CamelContext a = new DefaultCamelContext();
        CamelContext b = new DefaultCamelContext();
        try {
            a.addRoutes(route(nodeA));
            b.addRoutes(route(nodeB));
            a.start();
            b.start();

            MockEndpoint processedOnA = a.getEndpoint("mock:processed", MockEndpoint.class);
            processedOnA.expectedMessageCount(1);
            a.createProducerTemplate().sendBodyAndHeader("direct:start", "order", "orderId", "ORDER_1");
            processedOnA.assertIsSatisfied();

            // node A stops, the lock max age passes, and a duplicate of the order reaches node B
            a.stop();
            nodeA.stop();
            ageLeases();

            MockEndpoint processedOnB = b.getEndpoint("mock:processed", MockEndpoint.class);
            processedOnB.expectedMessageCount(0);
            b.createProducerTemplate().sendBodyAndHeader("direct:start", "order", "orderId", "ORDER_1");
            processedOnB.assertIsSatisfied();
        } finally {
            a.stop();
            b.stop();
        }
    }

    private static RouteBuilder route(JdbcOrphanLockAwareIdempotentRepository repository) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .idempotentConsumer(header("orderId"), repository)
                        .to("mock:processed");
            }
        };
    }

    private JdbcOrphanLockAwareIdempotentRepository newRepository() {
        JdbcOrphanLockAwareIdempotentRepository repository
                = new JdbcOrphanLockAwareIdempotentRepository(dataSource, PROCESSOR_NAME, context);
        repository.setLockMaxAgeMillis(LOCK_MAX_AGE_MILLIS);
        // the test calls keepAlive() itself
        repository.setLockKeepAliveIntervalMillis(LOCK_MAX_AGE_MILLIS / 2);
        return repository;
    }

    // the time passes: every lease is older than the lock max age, as no node refreshes it any more
    private void ageLeases() {
        Timestamp expired = new Timestamp(System.currentTimeMillis() - LOCK_MAX_AGE_MILLIS - 60_000L);
        jdbcTemplate.update("UPDATE CAMEL_MESSAGEPROCESSED SET createdAt = ? WHERE createdAt IS NOT NULL", expired);
    }

    private Timestamp createdAt(String key) {
        return jdbcTemplate.queryForObject(
                "SELECT createdAt FROM CAMEL_MESSAGEPROCESSED WHERE processorName = ? AND messageId = ?", Timestamp.class,
                PROCESSOR_NAME, key);
    }

    // records the WARN messages of the repository
    private static Logger recordingLogger(List<String> warnings) {
        return (Logger) Proxy.newProxyInstance(Logger.class.getClassLoader(), new Class<?>[] { Logger.class },
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "recordingLogger";
                        default:
                            break;
                    }
                    if ("warn".equals(method.getName()) && args != null && args.length > 0) {
                        warnings.add(String.valueOf(args[0]));
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

    private static ProcessorNameAndMessageId id(String key) {
        return new ProcessorNameAndMessageId(PROCESSOR_NAME, key);
    }
}
