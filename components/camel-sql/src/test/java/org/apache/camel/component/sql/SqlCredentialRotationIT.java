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
package org.apache.camel.component.sql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.agroal.api.AgroalDataSource;
import io.agroal.api.AgroalDataSourceMetrics;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.infra.postgres.services.PostgresService;
import org.apache.camel.test.infra.postgres.services.PostgresServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end integration test for credential rotation in {@link SqlComponent}.
 *
 * <p>
 * Verifies the full rotation flow against a real PostgreSQL database:
 * <ol>
 * <li>Start the pool with credential A and exercise a Camel SQL route.</li>
 * <li>Change the database password to credential B via an admin connection.</li>
 * <li>Update the pool's password and call {@link SqlComponent#onSecretRotation(Object)}.</li>
 * <li>Verify that subsequent queries succeed and use a new physical connection (different
 * {@code pg_backend_pid()}).</li>
 * </ol>
 * Both HikariCP and Agroal pools are covered.
 */
class SqlCredentialRotationIT extends CamelTestSupport {

    private static final String CRED_A = "password_a_initial"; // NOSONAR test-only credentials
    private static final String CRED_B = "password_b_rotated"; // NOSONAR test-only credentials
    private static final String TEST_USER = "camel_sql_rotation_test";

    @RegisterExtension
    static PostgresService postgres = PostgresServiceFactory.createSingletonService();

    private SqlComponent sqlComponent;
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
        try (Connection adminConn = DriverManager.getConnection(
                postgres.jdbcUrl(), postgres.userName(), postgres.password());
             Statement st = adminConn.createStatement()) {
            st.execute("REVOKE ALL PRIVILEGES ON DATABASE " + postgres.database() + " FROM " + TEST_USER);
            st.execute("DROP ROLE IF EXISTS " + TEST_USER);
        }
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

        registry.bind("sqlRotationDs", hikariDs);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sql-pid")
                        .to("sql:SELECT pg_backend_pid() AS pid?dataSource=#sqlRotationDs");
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    void hikariCp_credentialRotation_newConnectionUsesNewPassword() throws Exception {
        sqlComponent = context.getComponent("sql", SqlComponent.class);

        // Step 1: verify the pool works with cred A
        int pidBefore = queryBackendPid();
        assertNotNull(pidBefore, "pg_backend_pid() must return a value with credential A");

        // Step 2: change the database password to cred B via the admin connection
        try (Connection adminConn = DriverManager.getConnection(
                postgres.jdbcUrl(), postgres.userName(), postgres.password());
             Statement st = adminConn.createStatement()) {
            st.execute("ALTER ROLE " + TEST_USER + " PASSWORD '" + CRED_B + "'");
        }

        // Step 3: update the pool's password to cred B, then trigger rotation
        hikariDs.setPassword(CRED_B);
        sqlComponent.onSecretRotation("test-rotation");

        // Step 4: verify that a new query succeeds with cred B on a fresh connection
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            int pidAfter = queryBackendPid();
            assertNotNull(pidAfter, "pg_backend_pid() must succeed after rotation");
            assertNotEquals(pidBefore, pidAfter,
                    "After rotation, pg_backend_pid() must differ — pool must have opened a fresh connection");
        });
    }

    @Test
    void agroal_credentialRotation_flushEvictsConnections() throws Exception {
        try (AgroalDataSource agroalDs = AgroalDataSource.from(
                new AgroalDataSourceConfigurationSupplier()
                        .metricsEnabled()
                        .connectionPoolConfiguration(pool -> pool
                                .maxSize(2)
                                .minSize(1)
                                .connectionFactoryConfiguration(factory -> factory
                                        .jdbcUrl(postgres.jdbcUrl())
                                        .credential(new NamePrincipal(TEST_USER))
                                        .credential(new SimplePassword(CRED_A)))))) {

            AgroalDataSourceMetrics metrics = agroalDs.getMetrics();

            // Verify the pool works
            try (Connection conn = agroalDs.getConnection()) {
                assertNotNull(conn.createStatement().executeQuery("SELECT 1").next());
            }
            long flushCountBefore = metrics.flushCount();

            // Build a temporary SqlComponent wired to the Agroal DataSource and trigger rotation
            SqlComponent agroalComponent = new SqlComponent();
            agroalComponent.setDataSource(agroalDs);
            agroalComponent.setCamelContext(context);
            agroalComponent.onSecretRotation("agroal-rotation");

            // Agroal's flush(GRACEFUL) is handed to a housekeeping executor — wait for it
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                long flushCountAfter = metrics.flushCount();
                assertNotEquals(flushCountBefore, flushCountAfter,
                        "Agroal flushCount must have increased after rotation — flush(GRACEFUL) must have been called");
            });

            // Verify the pool is still functional after the flush
            try (Connection conn = agroalDs.getConnection()) {
                assertNotNull(conn.createStatement().executeQuery("SELECT 1").next());
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private int queryBackendPid() {
        List<Map<String, Object>> rows = template.requestBody("direct:sql-pid", null, List.class);
        assertEquals(1, rows.size(), "SELECT pg_backend_pid() must return exactly one row");
        Object pid = rows.get(0).get("pid");
        assertNotNull(pid, "pg_backend_pid() must not be null");
        return ((Number) pid).intValue();
    }
}
