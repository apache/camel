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

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.camel.support.DataSourceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for {@link DataSourceHelper#evictDataSourceConnections} against a real {@link HikariDataSource}
 * backed by h2. Covers both the started-pool and the not-started-pool code paths.
 */
class DataSourceHelperHikariIntegrationTest {

    @Test
    void evict_startedPool_callsSoftEvictWithoutError() throws Exception {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:evict_started;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(2);

        try (HikariDataSource ds = new HikariDataSource(config)) {
            // Force pool initialisation by opening a connection
            try (var conn = ds.getConnection()) {
                assertNotNull(conn);
            }

            // Eviction must succeed — real HikariPoolMXBean.softEvictConnections()
            assertDoesNotThrow(() -> DataSourceHelper.evictDataSourceConnections(ds, "test-rotation"));
        }
    }

    @Test
    void evict_notStartedPool_handlesNullMXBeanGracefully() {
        // HikariDataSource built with config — pool is not started until first getConnection()
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:evict_notstarted;DB_CLOSE_DELAY=-1");
        config.setUsername("sa");
        config.setPassword("");

        try (HikariDataSource ds = new HikariDataSource(config)) {
            // Do NOT call getConnection() — pool stays uninitialised, getHikariPoolMXBean() returns null
            assertDoesNotThrow(() -> DataSourceHelper.evictDataSourceConnections(ds, "test-rotation"));
        }
    }
}
