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

import org.apache.camel.impl.DefaultCamelContext;
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
        HikariLikeDataSource hikariLike = new HikariLikeDataSource();

        // Act
        DataSourceHelper.evictDataSourceConnections(hikariLike, "test");

        // Assert
        assertTrue(hikariLike.mxBean.softEvictCalled.get(),
                "softEvictConnections() should have been called via HikariPoolMXBean");
    }

    @Test
    void evictDataSourceConnections_genericPool_doesNotThrow() {
        // Arrange: a DataSource without getHikariPoolMXBean() — the generic fallback path
        DataSource generic = new NoOpDataSource();

        // Act — must not throw
        DataSourceHelper.evictDataSourceConnections(generic, "test");
    }

    @Test
    void onSecretRotation_withComponentOwnedDataSource_evictsConnections() throws Exception {
        // Arrange: DataSource injected directly on the component
        HikariLikeDataSource hikariLike = new HikariLikeDataSource();

        SqlComponent component = new SqlComponent();
        component.setDataSource(hikariLike);
        DefaultCamelContext ctx = new DefaultCamelContext();
        component.setCamelContext(ctx);

        // Act
        component.onSecretRotation("vault-rotation");

        // Assert
        assertTrue(hikariLike.mxBean.softEvictCalled.get(),
                "Component-owned DataSource should have been soft-evicted");
    }

    @Test
    void onSecretRotation_withoutDataSource_doesNotThrow() throws Exception {
        // Arrange: no DataSource on component
        SqlComponent component = new SqlComponent();
        DefaultCamelContext ctx = new DefaultCamelContext();
        component.setCamelContext(ctx);

        // Act — must not throw even with no DataSource configured
        component.onSecretRotation("vault-rotation");
    }

    // ---------------------------------------------------------------------------
    // DataSource stubs — public so that reflection in DataSourceHelper
    // can invoke methods without setAccessible(true)
    // ---------------------------------------------------------------------------

    /** Simulates a HikariPoolMXBean with a trackable {@code softEvictConnections()} call. */
    public static class MockPoolMXBean {
        public final AtomicBoolean softEvictCalled = new AtomicBoolean(false);

        public void softEvictConnections() {
            softEvictCalled.set(true);
        }
    }

    /**
     * Simulates a {@code HikariDataSource} by exposing {@code getHikariPoolMXBean()}, which returns a
     * {@link MockPoolMXBean}. This matches the real HikariCP API where {@code softEvictConnections()} lives on
     * {@code HikariPoolMXBean}, not on {@code HikariDataSource} itself.
     */
    public static class HikariLikeDataSource implements DataSource {
        public final MockPoolMXBean mxBean = new MockPoolMXBean();

        public Object getHikariPoolMXBean() {
            return mxBean;
        }

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

    /** A plain DataSource without getHikariPoolMXBean() — exercises the generic fallback path. */
    public static class NoOpDataSource implements DataSource {
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
}
