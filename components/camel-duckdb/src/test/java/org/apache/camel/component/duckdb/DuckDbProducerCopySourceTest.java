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
import java.util.Properties;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The copy operation loads a single local file unless an option allows a URL or a glob pattern, and copyBaseDirectory
 * keeps the source inside a directory.
 */
class DuckDbProducerCopySourceTest extends CamelTestSupport {

    @TempDir
    Path tempDir;

    private String uri;
    private Connection offlineConnection;

    private String endpointUri() throws Exception {
        if (uri == null) {
            String jdbcUrl = "jdbc:duckdb:" + tempDir.resolve("test.db").toAbsolutePath();
            uri = "duckdb::memory:?jdbcUrl=" + URLEncoder.encode(jdbcUrl, StandardCharsets.UTF_8);
            template.sendBody(uri + "&operation=execute", "CREATE TABLE events (id INTEGER, name VARCHAR)");
        }
        return uri;
    }

    private List<Object> names() throws Exception {
        List<?> rows = template.requestBody(endpointUri() + "&operation=query", "SELECT name FROM events ORDER BY id",
                List.class);
        return rows.stream().<Object> map(row -> ((Map<?, ?>) row).get("name")).toList();
    }

    private Path csv(Path dir, String fileName, int id, String name) throws Exception {
        Files.createDirectories(dir);
        return Files.writeString(dir.resolve(fileName), "id,name\n" + id + "," + name + "\n");
    }

    private static String encode(Path path) {
        return URLEncoder.encode(path.toAbsolutePath().toString(), StandardCharsets.UTF_8);
    }

    @Test
    void copyRefusesUrlSourceByDefault() throws Exception {
        String copyUri = "duckdb::memory:?operation=copy&table=events";
        useConnectionWithoutExtensionLoading(copyUri);

        assertThatThrownBy(() -> template.sendBody(copyUri, "https://example.com/data.csv"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowUrlCopySource=true");
    }

    @Test
    void copyPassesUrlSourceToDuckDbWhenAllowed() throws Exception {
        String copyUri = "duckdb::memory:?operation=copy&table=events&allowUrlCopySource=true";
        useConnectionWithoutExtensionLoading(copyUri);

        // DuckDB gets the URL: it fails here only because the connection may not load the extension that reads it
        assertThatThrownBy(() -> template.sendBody(copyUri, "https://example.com/data.csv"))
                .isInstanceOf(CamelExecutionException.class)
                .rootCause()
                .isInstanceOf(SQLException.class);
    }

    @Test
    void copyRefusesGlobPatternByDefault() throws Exception {
        csv(tempDir, "one.csv", 1, "ada");
        csv(tempDir, "two.csv", 2, "bob");
        String copyUri = endpointUri() + "&operation=copy&table=events";

        assertThatThrownBy(() -> template.sendBody(copyUri, tempDir.toAbsolutePath() + "/*.csv"))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowGlobCopySource=true");
        assertThat(names()).isEmpty();
    }

    @Test
    void copyLoadsGlobPatternWhenAllowed() throws Exception {
        csv(tempDir, "one.csv", 1, "ada");
        csv(tempDir, "two.csv", 2, "bob");

        Long written = template.requestBody(endpointUri() + "&operation=copy&table=events&allowGlobCopySource=true",
                tempDir.toAbsolutePath() + "/*.csv", Long.class);

        assertThat(written).isEqualTo(2L);
        assertThat(names()).containsExactly("ada", "bob");
    }

    @Test
    void copyRefusesFileNameWithGlobCharacters() throws Exception {
        csv(tempDir, "data1.csv", 1, "ada");
        Path file = csv(tempDir, "data[1].csv", 2, "bob");
        String copyUri = endpointUri() + "&operation=copy&table=events";

        assertThatThrownBy(() -> template.sendBody(copyUri, file.toFile()))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowGlobCopySource=true");
        assertThatThrownBy(() -> template.sendBody(copyUri, file))
                .isInstanceOf(CamelExecutionException.class)
                .cause()
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowGlobCopySource=true");
        assertThat(names()).isEmpty();
    }

    @Test
    void copyLoadsFileInsideBaseDirectory() throws Exception {
        Path base = tempDir.resolve("in");
        Path file = csv(base, "one.csv", 1, "ada");
        csv(base.resolve("sub"), "two.csv", 2, "bob");
        String copyUri = endpointUri() + "&operation=copy&table=events&copyBaseDirectory=" + encode(base);

        template.sendBody(copyUri, file.toFile());
        // a relative source is resolved against the base directory
        template.sendBody(copyUri, "sub/two.csv");

        assertThat(names()).containsExactly("ada", "bob");
    }

    @Test
    void copyRefusesFileOutsideBaseDirectory() throws Exception {
        Path base = tempDir.resolve("in");
        Files.createDirectories(base);
        Path outside = csv(tempDir.resolve("out"), "one.csv", 1, "ada");
        // a sibling directory whose name starts with the name of the base directory is outside it too
        Path sibling = csv(tempDir.resolve("in-other"), "two.csv", 2, "bob");
        String copyUri = endpointUri() + "&operation=copy&table=events&copyBaseDirectory=" + encode(base);

        for (Object body : List.of(outside.toAbsolutePath().toString(), outside.toFile(), "../out/one.csv",
                sibling.toAbsolutePath().toString())) {
            assertThatThrownBy(() -> template.sendBody(copyUri, body))
                    .isInstanceOf(CamelExecutionException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("copyBaseDirectory");
        }
        assertThat(names()).isEmpty();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void copyRefusesSymbolicLinkLeavingBaseDirectory() throws Exception {
        Path base = tempDir.resolve("in");
        Files.createDirectories(base);
        Path outside = csv(tempDir.resolve("out"), "one.csv", 1, "ada");
        Files.createSymbolicLink(base.resolve("link.csv"), outside);
        Files.createSymbolicLink(base.resolve("linkdir"), outside.getParent());
        String copyUri = endpointUri() + "&operation=copy&table=events&copyBaseDirectory=" + encode(base);

        for (String body : List.of("link.csv", "linkdir/one.csv")) {
            assertThatThrownBy(() -> template.sendBody(copyUri, body))
                    .isInstanceOf(CamelExecutionException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("copyBaseDirectory");
        }
        assertThat(names()).isEmpty();
    }

    @Test
    void copyLoadsGlobPatternOnlyInsideBaseDirectory() throws Exception {
        Path base = tempDir.resolve("in");
        csv(base, "one.csv", 1, "ada");
        csv(base, "two.csv", 2, "bob");
        csv(tempDir.resolve("out"), "three.csv", 3, "carl");
        String copyUri = endpointUri() + "&operation=copy&table=events&allowGlobCopySource=true&copyBaseDirectory="
                         + encode(base);

        for (String body : List.of("../out/*.csv", tempDir.resolve("out").toAbsolutePath() + "/*.csv",
                "*/../../out/*.csv")) {
            assertThatThrownBy(() -> template.sendBody(copyUri, body))
                    .isInstanceOf(CamelExecutionException.class)
                    .cause()
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("copyBaseDirectory");
        }

        Long written = template.requestBody(copyUri, "*.csv", Long.class);
        assertThat(written).isEqualTo(2L);
        assertThat(names()).containsExactly("ada", "bob");
    }

    /**
     * An in-memory connection that may not install or load DuckDB extensions, so a URL source never reaches the
     * network.
     */
    private void useConnectionWithoutExtensionLoading(String copyUri) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("autoinstall_known_extensions", "false");
        properties.setProperty("autoload_known_extensions", "false");
        Connection connection = DriverManager.getConnection("jdbc:duckdb:", properties);
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE events (id INTEGER, name VARCHAR)");
        }
        context.getEndpoint(copyUri, DuckDbEndpoint.class).setConnectionForTesting(connection);
        offlineConnection = connection;
    }

    @AfterEach
    void closeConnection() throws SQLException {
        if (offlineConnection != null) {
            offlineConnection.close();
        }
    }
}
