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
package org.apache.camel.component.duckdb;

import java.nio.file.Path;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuckDbComponentTest extends CamelTestSupport {

    @Test
    void createEndpointKeepsAbsoluteDatabasePath(@TempDir Path tempDir) {
        Path dbFile = tempDir.resolve("analytics.db");
        DuckDbEndpoint endpoint = context.getEndpoint("duckdb:" + dbFile + "?table=events", DuckDbEndpoint.class);

        assertThat(endpoint.getDatabasePath()).isEqualTo(dbFile.toString());
        assertThat(endpoint.getTable()).isEqualTo("events");
        assertThat(endpoint.resolvedJdbcUrl()).isEqualTo("jdbc:duckdb:" + dbFile);
    }

    @Test
    void createEndpointDefaultsToMemoryWhenPathEmpty() {
        DuckDbEndpoint endpoint = context.getEndpoint("duckdb::memory:", DuckDbEndpoint.class);

        assertThat(endpoint.getDatabasePath()).isEqualTo(":memory:");
    }

    @Test
    void createEndpointAppliesOptions() {
        DuckDbEndpoint endpoint = context.getEndpoint(
                "duckdb::memory:?operation=query&batchSize=100&format=csv&resultFormat=JSON&jdbcUrl=jdbc:duckdb:",
                DuckDbEndpoint.class);

        assertThat(endpoint.getOperation()).isEqualTo(DuckDbOperation.QUERY);
        assertThat(endpoint.getBatchSize()).isEqualTo(100);
        assertThat(endpoint.getFormat()).isEqualTo("csv");
        assertThat(endpoint.getResultFormat()).isEqualTo(DuckDbResultFormat.JSON);
        assertThat(endpoint.getJdbcUrl()).isEqualTo("jdbc:duckdb:");
    }

    @Test
    void createConsumerIsNotSupported() {
        DuckDbEndpoint endpoint = context.getEndpoint("duckdb::memory:", DuckDbEndpoint.class);

        assertThatThrownBy(() -> endpoint.createConsumer(exchange -> {
        })).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void componentLevelJdbcUrlIsInherited() {
        DuckDbComponent component = context.getComponent("duckdb", DuckDbComponent.class);
        component.setJdbcUrl("jdbc:duckdb:");

        DuckDbEndpoint endpoint = context.getEndpoint("duckdb::memory:", DuckDbEndpoint.class);

        assertThat(endpoint.getJdbcUrl()).isEqualTo("jdbc:duckdb:");
    }

    @Test
    void resolvedJdbcUrlUsesDatabasePath(@TempDir Path tempDir) {
        Path dbFile = tempDir.resolve("warehouse.db");
        DuckDbEndpoint endpoint = context.getEndpoint("duckdb:" + dbFile, DuckDbEndpoint.class);

        assertThat(endpoint.resolvedJdbcUrl()).isEqualTo("jdbc:duckdb:" + dbFile);
    }
}
