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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.apache.camel.support.DataSourceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration test for {@link DataSourceHelper#evictDataSourceConnections} against a real {@link HikariDataSource}
 * backed by h2. Covers both the started-pool and the not-started-pool code paths.
 */
class DataSourceHelperHikariIntegrationTest {

    @Test
    void evict_startedPool_evictsIdleConnections() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:evict_started;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);

        try (HikariDataSource ds = new HikariDataSource(config)) {
            HikariPoolMXBean poolMXBean = ds.getHikariPoolMXBean();
            assertNotNull(poolMXBean, "Pool MXBean should be available after pool start");

            // Grab a connection and remember the underlying physical connection
            Connection physicalBefore;
            try (Connection conn = ds.getConnection()) {
                physicalBefore = conn.unwrap(Connection.class);
                assertNotNull(physicalBefore);
            }

            // Evict — softEvictConnections() marks idle connections for eviction
            DataSourceHelper.evictDataSourceConnections(ds, "test-rotation");

            // After soft eviction, requesting a new connection forces the pool to
            // replace the evicted one with a fresh physical connection.
            Connection physicalAfter;
            try (Connection conn = ds.getConnection()) {
                physicalAfter = conn.unwrap(Connection.class);
                assertNotNull(physicalAfter);
            }

            // The pool replaced the evicted connection — the physical connection must differ
            assertNotSame(physicalBefore, physicalAfter,
                    "After soft eviction, the pool should return a new physical connection");
        }
    }

    @Test
    void evict_notStartedPool_handlesNullMXBeanGracefully() {
        // Use no-arg constructor + setters — pool is lazy, started on first getConnection()
        try (HikariDataSource ds = new HikariDataSource()) {
            ds.setJdbcUrl("jdbc:h2:mem:evict_notstarted;DB_CLOSE_DELAY=-1");
            ds.setUsername("sa");
            ds.setPassword("");

            // Pool is NOT started — getHikariPoolMXBean() returns null
            assertNull(ds.getHikariPoolMXBean(), "Pool MXBean should be null before pool start");

            // Eviction must handle null MXBean gracefully (DEBUG log, no exception)
            assertDoesNotThrow(() -> DataSourceHelper.evictDataSourceConnections(ds, "test-rotation"));
        }
    }
}
