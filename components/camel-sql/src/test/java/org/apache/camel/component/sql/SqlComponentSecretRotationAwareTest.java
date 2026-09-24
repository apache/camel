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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.camel.spi.SecretRotationAware;
import org.apache.camel.support.DataSourceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that {@link SqlComponent} implements {@link SecretRotationAware} and correctly evicts stale connections on
 * rotation.
 */
class SqlComponentSecretRotationAwareTest {

    @Test
    void implementsSecretRotationAware() {
        assertInstanceOf(SecretRotationAware.class, new SqlComponent());
    }

    @Test
    void evictDataSourceConnections_hikariCpPool_callsSoftEvict() throws Exception {
        // Arrange: a DataSource that simulates HikariDataSource by exposing getHikariPoolMXBean(),
        // which returns a mock MXBean with softEvictConnections(). This matches the real HikariCP API
        // where softEvictConnections() lives on HikariPoolMXBean, not on HikariDataSource itself.
        AtomicBoolean softEvictCalled = new AtomicBoolean(false);
        Object mockMXBean = new Object() {
            @SuppressWarnings("unused")
            public void softEvictConnections() {
                softEvictCalled.set(true);
            }
        };
        DataSource hikariLike = new HikariLikeDataSource() {
            @SuppressWarnings("unused")
            public Object getHikariPoolMXBean() {
                return mockMXBean;
            }
        };

        // Act
        DataSourceHelper.evictDataSourceConnections(hikariLike, "test");

        // Assert
        assertTrue(softEvictCalled.get(), "softEvictConnections() should have been called via HikariPoolMXBean");
    }

    @Test
    void evictDataSourceConnections_genericPool_doesNotThrow() {
        // Arrange: a DataSource without getHikariPoolMXBean() — the generic fallback path
        DataSource generic = new NoOpDataSource();

        // Act — must not throw
        DataSourceHelper.evictDataSourceConnections(generic, "test");
    }

    @Test
    void onSecretRotation_withRegistryDataSource_evictsConnections() throws Exception {
        // Arrange
        AtomicBoolean softEvictCalled = new AtomicBoolean(false);
        Object mockMXBean = new Object() {
            @SuppressWarnings("unused")
            public void softEvictConnections() {
                softEvictCalled.set(true);
            }
        };
        DataSource hikariLike = new HikariLikeDataSource() {
            @SuppressWarnings("unused")
            public Object getHikariPoolMXBean() {
                return mockMXBean;
            }
        };

        SqlComponent component = new SqlComponent();
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
        Object mockMXBean = new Object() {
            @SuppressWarnings("unused")
            public void softEvictConnections() {
                softEvictCalled.set(true);
            }
        };
        DataSource hikariLike = new HikariLikeDataSource() {
            @SuppressWarnings("unused")
            public Object getHikariPoolMXBean() {
                return mockMXBean;
            }
        };

        SqlComponent component = new SqlComponent();
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
        SqlComponent component = new SqlComponent();
        org.apache.camel.impl.DefaultCamelContext ctx = new org.apache.camel.impl.DefaultCamelContext();
        component.setCamelContext(ctx);

        // Act — must not throw even with no DataSource configured
        component.onSecretRotation("vault-rotation");
    }

    // ---------------------------------------------------------------------------
    // Minimal DataSource stubs
    // ---------------------------------------------------------------------------

    /**
     * Base class for the HikariCP-like stub. Subclasses add {@code getHikariPoolMXBean()} to simulate the real
     * {@code HikariDataSource} API (where {@code softEvictConnections()} lives on the MXBean, not the DataSource).
     */
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

    /** A plain DataSource without getHikariPoolMXBean(). */
    private static final class NoOpDataSource extends HikariLikeDataSource {
        // no getHikariPoolMXBean() — exercises the generic fallback path
    }
}
