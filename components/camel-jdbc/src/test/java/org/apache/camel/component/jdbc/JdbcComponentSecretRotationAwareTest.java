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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.camel.spi.SecretRotationAware;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link JdbcComponent} implements {@link SecretRotationAware} and correctly evicts stale connections on
 * rotation.
 */
class JdbcComponentSecretRotationAwareTest {

    @Test
    void implementsSecretRotationAware() {
        assertInstanceOf(SecretRotationAware.class, new JdbcComponent());
    }

    @Test
    void evictDataSourceConnections_hikariCpPool_callsSoftEvict() throws Exception {
        // Arrange: a DataSource that exposes softEvictConnections(), simulating HikariCP
        AtomicBoolean softEvictCalled = new AtomicBoolean(false);
        DataSource hikariLike = new HikariLikeDataSource() {
            @SuppressWarnings("unused")
            public void softEvictConnections() {
                softEvictCalled.set(true);
            }
        };

        // Act
        JdbcComponent.evictDataSourceConnections(hikariLike, "test");

        // Assert
        assertTrue(softEvictCalled.get(), "softEvictConnections() should have been called on a HikariCP-like pool");
    }

    @Test
    void evictDataSourceConnections_genericPool_doesNotThrow() throws Exception {
        // Arrange: a DataSource without softEvictConnections() — the generic fallback path
        DataSource generic = new NoOpDataSource();

        // Act — must not throw
        JdbcComponent.evictDataSourceConnections(generic, "test");
    }

    @Test
    void onSecretRotation_withRegistryDataSource_evictsConnections() throws Exception {
        // Arrange
        AtomicBoolean softEvictCalled = new AtomicBoolean(false);
        DataSource hikariLike = new HikariLikeDataSource() {
            @SuppressWarnings("unused")
            public void softEvictConnections() {
                softEvictCalled.set(true);
            }
        };

        JdbcComponent component = new JdbcComponent();
        // Use a real CamelContext so we can bind the DataSource to the registry
        org.apache.camel.impl.DefaultCamelContext ctx = new org.apache.camel.impl.DefaultCamelContext();
        ctx.getRegistry().bind("myDs", hikariLike);
        component.setCamelContext(ctx);

        // Act
        component.onSecretRotation("vault-rotation");

        // Assert
        assertTrue(softEvictCalled.get(), "DataSource in registry should have been soft-evicted");
    }

    @Test
    void onSecretRotation_withComponentOwnedDataSource_evictsConnections() throws Exception {
        // Arrange: DataSource injected directly on the component (not in registry)
        AtomicBoolean softEvictCalled = new AtomicBoolean(false);
        DataSource hikariLike = new HikariLikeDataSource() {
            @SuppressWarnings("unused")
            public void softEvictConnections() {
                softEvictCalled.set(true);
            }
        };

        JdbcComponent component = new JdbcComponent();
        component.setDataSource(hikariLike);
        org.apache.camel.impl.DefaultCamelContext ctx = new org.apache.camel.impl.DefaultCamelContext();
        component.setCamelContext(ctx);

        // Act
        component.onSecretRotation("vault-rotation");

        // Assert
        assertTrue(softEvictCalled.get(), "Component-owned DataSource should have been soft-evicted");
    }

    @Test
    void onSecretRotation_withoutDataSource_doesNotThrow() throws Exception {
        // Arrange: no DataSource in registry or on component
        JdbcComponent component = new JdbcComponent();
        org.apache.camel.impl.DefaultCamelContext ctx = new org.apache.camel.impl.DefaultCamelContext();
        component.setCamelContext(ctx);

        // Act — must not throw even with no DataSource configured
        component.onSecretRotation("vault-rotation");
    }

    // ---------------------------------------------------------------------------
    // Minimal DataSource stubs
    // ---------------------------------------------------------------------------

    /** Base class for the HikariCP-like stub — the subclass adds softEvictConnections() dynamically. */
    private abstract static class HikariLikeDataSource implements DataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            throw new SQLFeatureNotSupportedException();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("Not a wrapper for " + iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }
    }

    /** A plain DataSource without softEvictConnections(). */
    private static final class NoOpDataSource extends HikariLikeDataSource {
        // no softEvictConnections() — exercises the generic fallback path
    }
}
