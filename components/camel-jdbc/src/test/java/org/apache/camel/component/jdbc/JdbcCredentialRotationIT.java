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
package org.apache.camel.component.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.AgroalSecurityProvider;
import io.agroal.api.security.NamePrincipal;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.apache.camel.test.infra.postgres.services.PostgresService;
import org.apache.camel.test.infra.postgres.services.PostgresServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end integration test for credential rotation in {@link JdbcComponent}.
 *
 * <p>
 * Verifies the full rotation flow against a real PostgreSQL database:
 * <ol>
 * <li>Start the pool with credential A and exercise a Camel JDBC route.</li>
 * <li>Change the database password to credential B via an admin connection.</li>
 * <li>Update the pool's password and call {@link JdbcComponent#onSecretRotation(Object)}.</li>
 * <li>Verify that subsequent queries succeed and use a new physical connection (different
 * {@code pg_backend_pid()}).</li>
 * </ol>
 * HikariCP is tested with a direct pool credential update; Agroal is tested with a custom
 * {@link AgroalSecurityProvider} that supplies the current password on each new connection.
 */
class JdbcCredentialRotationIT extends CamelTestSupport {

    private static final String CRED_A = "password_a_initial"; // NOSONAR test-only credentials
    private static final String CRED_B = "password_b_rotated"; // NOSONAR test-only credentials
    private static final String TEST_USER = "camel_rotation_test";

    @RegisterExtension
    static PostgresService postgres = PostgresServiceFactory.createSingletonService();

    private JdbcComponent jdbcComponent;
    private HikariDataSource hikariDs;

    /**
     * Creates the test database role before the Camel context is started.
     * <p>
     * {@code setupResources()} runs before {@link #bindToRegistry(Registry)}, so the role exists when HikariCP
     * initialises its connection pool.
     */
    @Override
    protected void setupResources() throws Exception {
        super.setupResources();
        try (Connection adminConn = DriverManager.getConnection(
                postgres.jdbcUrl(), postgres.userName(), postgres.password());
             Statement st = adminConn.createStatement()) {
            st.execute("DROP ROLE IF EXISTS " + TEST_USER);
            st.execute("CREATE ROLE " + TEST_USER + " LOGIN PASSWORD '" + CRED_A + "'");
            st.execute("GRANT CONNECT ON DATABASE " + postgres.database() + " TO " + TEST_USER);
        }
    }

    @AfterEach
    void dropTestUser() throws Exception {
        if (hikariDs != null) {
            hikariDs.close();
        }
        try (Connection adminConn = DriverManager.getConnection(
                postgres.jdbcUrl(), postgres.userName(), postgres.password());
             Statement st = adminConn.createStatement()) {
            st.execute("REVOKE ALL PRIVILEGES ON DATABASE " + postgres.database() + " FROM " + TEST_USER);
            st.execute("DROP ROLE IF EXISTS " + TEST_USER);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:jdbc-query")
                        .to("jdbc:rotationTestDs");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.jdbcUrl());
        config.setUsername(TEST_USER);
        config.setPassword(CRED_A);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTestQuery("SELECT 1");
        hikariDs = new HikariDataSource(config);

        registry.bind("rotationTestDs", hikariDs);
    }

    @Test
    void hikariCp_credentialRotation_newConnectionUsesNewPassword() throws Exception {
        jdbcComponent = context.getComponent("jdbc", JdbcComponent.class);

        DefaultContextReloadStrategy reload = new DefaultContextReloadStrategy();
        context.addService(reload);

        // Step 1: verify the pool works with cred A
        int pidBefore = queryBackendPid();
        assertNotEquals(0, pidBefore, "pg_backend_pid() must return a non-zero value with credential A");

        // Step 2: change the database password to cred B via the admin connection
        try (Connection adminConn = DriverManager.getConnection(
                postgres.jdbcUrl(), postgres.userName(), postgres.password());
             Statement st = adminConn.createStatement()) {
            st.execute("ALTER ROLE " + TEST_USER + " PASSWORD '" + CRED_B + "'");
        }

        // Verify old password no longer works for new connections
        assertThrows(SQLException.class,
                () -> DriverManager.getConnection(postgres.jdbcUrl(), TEST_USER, CRED_A),
                "Old password must be rejected after ALTER ROLE");

        // Step 3: update the pool's password to cred B, then trigger rotation via reload strategy
        hikariDs.setPassword(CRED_B);
        reload.onReload("test-rotation");

        // Step 4: verify that a new query succeeds with cred B on a fresh connection
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            int pidAfter = queryBackendPid();
            assertNotEquals(0, pidAfter, "pg_backend_pid() must return a non-zero value after rotation");
            assertNotEquals(pidBefore, pidAfter,
                    "After rotation, pg_backend_pid() must differ — pool must have opened a fresh connection");
        });
    }

    @Test
    void agroal_credentialRotation_newConnectionUsesNewPassword() throws Exception {
        // A marker credential object that our custom AgroalSecurityProvider recognises.
        // The provider returns the current password from an AtomicReference, allowing
        // the test to rotate the credential without rebuilding the DataSource.
        record RotatingCredential() {
        }

        AtomicReference<String> currentPassword = new AtomicReference<>(CRED_A);

        AgroalSecurityProvider rotating = credential -> {
            if (credential instanceof RotatingCredential) {
                Properties p = new Properties();
                p.setProperty("password", currentPassword.get());
                return p;
            }
            return null;
        };

        try (AgroalDataSource agroalDs = AgroalDataSource.from(
                new AgroalDataSourceConfigurationSupplier()
                        .metricsEnabled()
                        .connectionPoolConfiguration(pool -> pool
                                .maxSize(2)
                                .minSize(1)
                                .connectionFactoryConfiguration(factory -> factory
                                        .jdbcUrl(postgres.jdbcUrl())
                                        .principal(new NamePrincipal(TEST_USER))
                                        .credential(new RotatingCredential())
                                        .addSecurityProvider(rotating))))) {

            // Register the Agroal DataSource in the registry so it can be discovered by the component
            context.getRegistry().bind("agroalRotationDs", agroalDs);

            // Build a temporary JdbcComponent wired to the Agroal DataSource
            JdbcComponent agroalComponent = new JdbcComponent();
            agroalComponent.setDataSource(agroalDs);
            agroalComponent.setCamelContext(context);

            // Step 1: verify the pool works with cred A — grab a PID
            int pidBefore;
            try (Connection conn = agroalDs.getConnection();
                 var rs = conn.createStatement().executeQuery("SELECT pg_backend_pid() AS pid")) {
                assertTrue(rs.next(), "SELECT pg_backend_pid() must return a row");
                pidBefore = rs.getInt("pid");
                assertNotEquals(0, pidBefore, "pg_backend_pid() must return a non-zero value with credential A");
            }

            long flushCountBefore = agroalDs.getMetrics().flushCount();

            // Step 2: change the database password to cred B via the admin connection
            try (Connection adminConn = DriverManager.getConnection(
                    postgres.jdbcUrl(), postgres.userName(), postgres.password());
                 Statement st = adminConn.createStatement()) {
                st.execute("ALTER ROLE " + TEST_USER + " PASSWORD '" + CRED_B + "'");
            }

            // Verify old password no longer works for new connections
            assertThrows(SQLException.class,
                    () -> DriverManager.getConnection(postgres.jdbcUrl(), TEST_USER, CRED_A),
                    "Old password must be rejected after ALTER ROLE");

            // Step 3: update the rotating credential and trigger rotation
            currentPassword.set(CRED_B);
            agroalComponent.onSecretRotation("agroal-rotation");

            // Step 4: wait for the flush and verify a new connection uses cred B
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                long flushCountAfter = agroalDs.getMetrics().flushCount();
                assertTrue(flushCountAfter > flushCountBefore,
                        "Agroal flushCount must have increased after rotation — flush(GRACEFUL) must have been called");
            });

            // Step 5: verify the pool creates a new connection (different PID) with the new password
            try (Connection conn = agroalDs.getConnection();
                 var rs = conn.createStatement().executeQuery("SELECT pg_backend_pid() AS pid")) {
                assertTrue(rs.next(), "SELECT pg_backend_pid() must return a row after rotation");
                int pidAfter = rs.getInt("pid");
                assertNotEquals(0, pidAfter, "pg_backend_pid() must return a non-zero value after rotation");
                assertNotEquals(pidBefore, pidAfter,
                        "After rotation, pg_backend_pid() must differ — pool must have opened a fresh connection");
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private int queryBackendPid() {
        List<Map<String, Object>> rows = template.requestBody(
                "direct:jdbc-query", "SELECT pg_backend_pid() AS pid", List.class);
        assertEquals(1, rows.size(), "SELECT pg_backend_pid() must return exactly one row");
        Object pid = rows.get(0).get("pid");
        assertNotEquals(0, ((Number) pid).intValue(), "pg_backend_pid() must not be zero");
        return ((Number) pid).intValue();
    }
}
