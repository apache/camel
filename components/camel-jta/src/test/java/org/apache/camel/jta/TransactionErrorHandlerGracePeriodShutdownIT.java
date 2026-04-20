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
package org.apache.camel.jta;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import io.agroal.narayana.NarayanaTransactionIntegration;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sql.SqlComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.infra.postgres.services.PostgresLocalContainerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.xa.PGXADataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that mimics a production scenario where a transacted route performs a SQL operation followed by a
 * long-running step (e.g., a stored procedure call or a delay that exceeds the shutdown grace period).
 *
 * <p>
 * The key behavior being tested: when the shutdown grace period expires and forced shutdown is triggered, the
 * {@link TransactionErrorHandler} marks the in-flight exchange as {@code rollbackOnly}. After the delay/procedure
 * completes, the exchange finishes processing <b>without an exception</b>, but the {@code rollbackOnly} flag causes the
 * transaction to be rolled back instead of committed. Without the fix, the INSERT would be committed.
 */
public class TransactionErrorHandlerGracePeriodShutdownIT {

    @RegisterExtension
    static PostgresLocalContainerService postgres = new PostgresLocalContainerService();

    private CamelContext camelContext;
    private AgroalDataSource agroalDataSource;
    private PGSimpleDataSource plainDataSource;
    private TransactionManager tm;

    private final CountDownLatch firstInsertDone = new CountDownLatch(1);
    private final CountDownLatch delayStarted = new CountDownLatch(1);
    private final CountDownLatch releaseLatch = new CountDownLatch(1);

    @BeforeEach
    public void setUp() throws Exception {
        // Narayana transaction manager and synchronization registry
        tm = com.arjuna.ats.jta.TransactionManager.transactionManager();
        jakarta.transaction.TransactionSynchronizationRegistry tsr
                = new com.arjuna.ats.internal.jta.transaction.arjunacore.TransactionSynchronizationRegistryImple();

        String jdbcUrl = postgres.jdbcUrl();

        // Agroal DataSource with Narayana integration — same stack as Quarkus.
        // Agroal automatically enlists connections in the active JTA transaction
        // via NarayanaTransactionIntegration, no custom wrapper needed.
        agroalDataSource = AgroalDataSource.from(
                new AgroalDataSourceConfigurationSupplier()
                        .connectionPoolConfiguration(pool -> pool
                                .maxSize(5)
                                .transactionIntegration(new NarayanaTransactionIntegration(tm, tsr))
                                .connectionFactoryConfiguration(cf -> cf
                                        .connectionProviderClass(PGXADataSource.class)
                                        .jdbcUrl(jdbcUrl)
                                        .principal(new NamePrincipal(postgres.userName()))
                                        .credential(new SimplePassword(postgres.password())))));

        // Plain DataSource for assertions (outside of JTA)
        plainDataSource = new PGSimpleDataSource();
        plainDataSource.setServerNames(new String[] { postgres.host() });
        plainDataSource.setPortNumbers(new int[] { postgres.port() });
        plainDataSource.setDatabaseName(postgres.database());
        plainDataSource.setUser(postgres.userName());
        plainDataSource.setPassword(postgres.password());

        // Create test table
        try (Connection conn = plainDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS orders");
            stmt.execute("CREATE TABLE orders (id SERIAL PRIMARY KEY, item VARCHAR(255))");
        }

        // JtaTransactionPolicy backed by Narayana (same pattern as Quarkus RequiredJtaTransactionPolicy)
        JtaTransactionPolicy requiredPolicy = new JtaTransactionPolicy() {
            @Override
            public void run(Runnable runnable) throws Throwable {
                boolean isNew = tm.getStatus() == Status.STATUS_NO_TRANSACTION;
                if (isNew) {
                    tm.begin();
                }
                try {
                    runnable.run();
                } catch (Throwable e) {
                    if (isNew) {
                        tm.rollback();
                    } else {
                        tm.setRollbackOnly();
                    }
                    throw e;
                }
                if (isNew) {
                    tm.commit();
                }
            }
        };

        camelContext = new DefaultCamelContext();

        // Short shutdown timeout: simulates the production grace period expiring
        camelContext.getShutdownStrategy().setTimeout(2);
        camelContext.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        // Register Agroal DataSource for camel-sql
        SqlComponent sqlComponent = new SqlComponent();
        sqlComponent.setDataSource(agroalDataSource);
        camelContext.addComponent("sql", sqlComponent);

        camelContext.getRegistry().bind("PROPAGATION_REQUIRED", requiredPolicy);

        // Route: SQL INSERT then a long delay.
        // No steps after the delay — the exchange completes normally (no exception).
        // The ONLY thing that should cause rollback is the rollbackOnly flag
        // set by TransactionErrorHandler.prepareShutdown(forced=true).
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("transactedRoute")
                        .transacted()
                        // SQL insert — completes successfully and is enlisted in the JTA transaction
                        .to("sql:INSERT INTO orders(item) VALUES ('first')")
                        .process(exchange -> firstInsertDone.countDown())
                        // Long delay (simulates stored procedure or long-running operation
                        // that exceeds the shutdown grace period)
                        .process(exchange -> {
                            delayStarted.countDown();
                            releaseLatch.await(30, TimeUnit.SECONDS);
                        });
            }
        });

        camelContext.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        releaseLatch.countDown();
        if (camelContext != null && camelContext.isStarted()) {
            camelContext.stop();
        }
        if (agroalDataSource != null) {
            agroalDataSource.close();
        }
    }

    /**
     * Simulates the production scenario:
     * <ol>
     * <li>Exchange enters transacted route</li>
     * <li>SQL INSERT ('first') completes against PostgreSQL (enlisted in JTA tx)</li>
     * <li>Long delay begins (simulating stored procedure exceeding grace period)</li>
     * <li>CamelContext.stop() -> DefaultShutdownStrategy waits 2s -> forced shutdown</li>
     * <li>TransactionErrorHandler.prepareShutdown(forced=true) marks exchange rollbackOnly</li>
     * <li>context.stop() returns (timeout expired, in-flight exchange still running)</li>
     * <li>Delay released, exchange completes normally (no exception)</li>
     * <li>TransactionErrorHandler checks preparingShutdown -> sets rollbackOnly -> throws</li>
     * <li>JtaTransactionPolicy.run() catches -> Narayana rolls back the JTA transaction</li>
     * <li>Assert: the INSERT is NOT in the database (rolled back)</li>
     * </ol>
     *
     * <p>
     * Without the fix, step 8 does not happen — the exchange commits normally because there is no exception and
     * rollbackOnly is never set. The INSERT persists in the database.
     */
    @Test
    public void testForcedShutdownRollsBackDatabaseTransaction() throws Exception {
        assertEquals(0, countOrders(), "Table should be empty initially");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            // Send message in a background thread (the route blocks during the delay)
            executor.submit(() -> {
                try {
                    camelContext.createProducerTemplate().sendBody("direct:start", "trigger");
                } catch (Exception e) {
                    // expected — rollback triggers TransactionRolledbackException
                }
                return null;
            });

            assertTrue(firstInsertDone.await(10, TimeUnit.SECONDS),
                    "SQL insert should have completed");

            assertTrue(delayStarted.await(10, TimeUnit.SECONDS),
                    "Long delay should have started");

            // Stop the CamelContext. The DefaultShutdownStrategy will:
            //   1. Stop the direct consumer (no new messages)
            //   2. Wait up to 2s for in-flight exchanges
            //   3. Exchange is stuck in the delay -> timeout expires
            //   4. Forced shutdown -> prepareShutdown(false, true) on TransactionErrorHandler
            //   5. context.stop() returns (in-flight exchange still blocked)
            camelContext.stop();

            // Now release the delay. The exchange wakes up and finishes processing.
            // The route has no more steps, so processByErrorHandler() returns normally.
            // Back in doInTransactionTemplate(), the preparingShutdown check (the fix)
            // sets rollbackOnly, causing the transaction to be rolled back.
            releaseLatch.countDown();

            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "Exchange processing should have completed");

            int finalCount = countOrders();
            assertEquals(0, finalCount,
                    "No rows should be in the database — the JTA transaction should have been rolled back. "
                                        + "Found " + finalCount + " rows instead.");
        } finally {
            releaseLatch.countDown();
            executor.shutdownNow();
        }
    }

    private int countOrders() throws SQLException {
        try (Connection conn = plainDataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM orders")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
