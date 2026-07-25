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

import org.duckdb.DuckDBDriver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuckDbJdbcSupportTest {

    @Test
    void resolvesMemoryDatabase() {
        assertThat(DuckDbJdbcSupport.resolveJdbcUrl(null, ":memory:")).isEqualTo("jdbc:duckdb:");
        assertThat(DuckDbJdbcSupport.resolveJdbcUrl("", null)).isEqualTo("jdbc:duckdb:");
    }

    @Test
    void resolvesFileDatabase() {
        assertThat(DuckDbJdbcSupport.resolveJdbcUrl(null, "warehouse.db")).isEqualTo("jdbc:duckdb:warehouse.db");
    }

    @Test
    void jdbcUrlOverrideWins() {
        assertThat(DuckDbJdbcSupport.resolveJdbcUrl("jdbc:duckdb:/tmp/x.db", "ignored"))
                .isEqualTo("jdbc:duckdb:/tmp/x.db");
    }

    @Test
    void readOnlyIsPassedAsConnectionProperty() {
        assertThat(DuckDbJdbcSupport.connectionProperties(false)).isEmpty();
        assertThat(DuckDbJdbcSupport.connectionProperties(true))
                .containsEntry(DuckDBDriver.DUCKDB_READONLY_PROPERTY, "true");
    }

    @Test
    void quotesIdentifiers() {
        assertThat(DuckDbJdbcSupport.quoteIdentifier("order")).isEqualTo("\"order\"");
        assertThat(DuckDbJdbcSupport.quoteIdentifier(" name ")).isEqualTo("\"name\"");
        assertThat(DuckDbJdbcSupport.quoteIdentifier("we\"ird")).isEqualTo("\"we\"\"ird\"");
    }

    @Test
    void quotesQualifiedIdentifiersPerSegment() {
        assertThat(DuckDbJdbcSupport.quoteQualifiedIdentifier("events")).isEqualTo("\"events\"");
        assertThat(DuckDbJdbcSupport.quoteQualifiedIdentifier("main.events")).isEqualTo("\"main\".\"events\"");
    }

    @Test
    void keepsAlreadyQuotedIdentifiers() {
        assertThat(DuckDbJdbcSupport.quoteIdentifier("\"MixedCase\"")).isEqualTo("\"MixedCase\"");
        assertThat(DuckDbJdbcSupport.quoteQualifiedIdentifier("\"main\".\"events\"")).isEqualTo("\"main\".\"events\"");
    }
}
