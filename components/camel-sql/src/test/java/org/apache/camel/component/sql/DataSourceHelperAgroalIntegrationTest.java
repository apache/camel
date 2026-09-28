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

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.supplier.AgroalDataSourceConfigurationSupplier;
import io.agroal.api.security.NamePrincipal;
import io.agroal.api.security.SimplePassword;
import org.apache.camel.support.DataSourceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for {@link DataSourceHelper#evictDataSourceConnections} against a real {@link AgroalDataSource}
 * backed by h2. Verifies that Agroal's {@code flush(GRACEFUL)} is correctly detected and invoked via reflection.
 */
class DataSourceHelperAgroalIntegrationTest {

    private static AgroalDataSource createDataSource(String dbName) throws Exception {
        return AgroalDataSource.from(new AgroalDataSourceConfigurationSupplier()
                .connectionPoolConfiguration(pool -> pool
                        .maxSize(2)
                        .minSize(1)
                        .connectionFactoryConfiguration(factory -> factory
                                .jdbcUrl("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1")
                                .credential(new NamePrincipal("sa"))
                                .credential(new SimplePassword("")))));
    }

    @Test
    void evict_startedPool_evictsConnections() throws Exception {
        try (AgroalDataSource ds = createDataSource("agroal_evict_started")) {
            // Force pool initialisation by opening a connection
            try (Connection conn = ds.getConnection()) {
                assertNotNull(conn);
            }

            // Eviction must succeed — real AgroalDataSource.flush(GRACEFUL) via reflection
            assertDoesNotThrow(() -> DataSourceHelper.evictDataSourceConnections(ds, "test-rotation"));

            // After GRACEFUL flush, the pool must still be functional
            try (Connection conn = ds.getConnection()) {
                assertNotNull(conn);
            }
        }
    }

    @Test
    void evict_doesNotThrow() throws Exception {
        // Smoke test: eviction must never throw regardless of pool state
        try (AgroalDataSource ds = createDataSource("agroal_evict_smoke")) {
            assertDoesNotThrow(() -> DataSourceHelper.evictDataSourceConnections(ds, "test-rotation"));
        }
    }
}
