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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DuckDbProducerTest extends CamelTestSupport {

    private static String jdbcEndpoint(Path dbFile) {
        String jdbcUrl = "jdbc:duckdb:" + dbFile.toAbsolutePath();
        return "duckdb::memory:?jdbcUrl=" + URLEncoder.encode(jdbcUrl, StandardCharsets.UTF_8);
    }

    @Test
    void insertAndQueryRows(@TempDir Path tempDir) throws Exception {
        String uri = jdbcEndpoint(tempDir.resolve("test.db"));
        template.sendBody(uri + "&operation=execute", "CREATE TABLE events (id INTEGER, name VARCHAR)");
        template.sendBody(uri + "&operation=insert&table=events", List.of(Map.of("id", 1, "name", "ada")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = template.requestBody(uri + "&operation=query", "SELECT name FROM events", List.class);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("name")).isEqualTo("ada");
    }

    @Test
    void copyFromCsvFile(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("test.db");
        String uri = jdbcEndpoint(dbFile);
        template.sendBody(uri + "&operation=execute", "CREATE TABLE events (id INTEGER, name VARCHAR)");

        Path csv = tempDir.resolve("data.csv");
        Files.writeString(csv, "id,name\n2,bob\n");

        template.sendBody(uri + "&operation=copy&table=events&format=csv", csv.toFile());

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = template.requestBody(uri + "&operation=query", "SELECT name FROM events WHERE id = 2",
                List.class);
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("bob");
    }

    @Test
    void insertQuotesReservedIdentifiers(@TempDir Path tempDir) throws Exception {
        String uri = jdbcEndpoint(tempDir.resolve("test.db"));
        template.sendBody(uri + "&operation=execute", "CREATE TABLE \"order\" (\"select\" INTEGER, \"group\" VARCHAR)");
        template.sendBody(uri + "&operation=insert&table=order", List.of(Map.of("select", 7, "group", "cli")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows
                = template.requestBody(uri + "&operation=query", "SELECT \"group\" FROM \"order\"", List.class);
        assertThat(rows).extracting(row -> row.get("group")).containsExactly("cli");
    }

    @Test
    void readOnlyPreventsWrites(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("readonly.db");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + dbFile.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE events (id INTEGER, name VARCHAR)");
        }

        String uri = jdbcEndpoint(dbFile) + "&readOnly=true";

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows
                = template.requestBody(uri + "&operation=query", "SELECT count(*) AS total FROM events", List.class);
        assertThat(rows).hasSize(1);

        assertThatThrownBy(() -> template.sendBody(uri + "&operation=insert&table=events",
                List.of(Map.of("id", 1, "name", "ada"))))
                .rootCause()
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void databasePathHeaderOverridesEndpointDatabase(@TempDir Path tempDir) throws Exception {
        Path otherDb = tempDir.resolve("other.db");
        try (Connection connection = DriverManager.getConnection("jdbc:duckdb:" + otherDb.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE events (id INTEGER, name VARCHAR)");
            statement.execute("INSERT INTO events VALUES (1, 'header-db')");
        }

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:query-override")
                        .to("duckdb::memory:?operation=query");
            }
        });

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = template.requestBodyAndHeader(
                "direct:query-override",
                "SELECT name FROM events",
                DuckDbConstants.DATABASE_PATH,
                otherDb.toAbsolutePath().toString(),
                List.class);
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("header-db");
    }
}
