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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code camel_get_files} on the selected integration: an exported Spring Boot project is a Maven layout, and the
 * running routes point at the files under {@code src/main}, so a model finds the route file in one call instead of
 * guessing names in the wrong folder (CAMEL-24798).
 */
class McpFacadeGetFilesTest {

    private static final String ROUTE = "- route:\n    id: timer-log\n    from:\n      uri: timer:tick\n"
                                        + "      steps:\n        - log:\n            message: hi\n";

    private static McpFacade facade(Path projectDir, String routeSource) {
        return facade(projectDir, "timer-log", routeSource);
    }

    private static McpFacade facade(Path projectDir, String routeId, String routeSource) {
        IntegrationInfo info = new IntegrationInfo();
        info.name = "timer-log";
        info.pid = "1";
        info.directory = projectDir.toString();
        RouteInfo route = new RouteInfo();
        route.routeId = routeId;
        route.source = routeSource;
        info.routes.add(route);
        return new McpFacade(
                null, new AtomicReference<>(List.of(info)), null, null, null, null, null, null, null, null, null,
                null, null);
    }

    @Test
    void anExportedProjectListsItsRouteFileAndWhereTheRunningRouteComesFrom(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src/main/resources/camel"));
        Files.createDirectories(dir.resolve("target/classes/camel"));
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Files.writeString(dir.resolve("src/main/resources/camel/timer-log.camel.yaml"), ROUTE);
        Files.writeString(dir.resolve("src/main/resources/application.properties"), "camel.main.name=timer-log\n");
        Files.writeString(dir.resolve("target/classes/camel/timer-log.camel.yaml"), ROUTE);
        McpFacade facade = facade(dir, "nested:" + dir
                                       + "/target/timer-log-1.0-SNAPSHOT.jar/!BOOT-INF/classes/!/camel/timer-log.camel.yaml:4");

        JsonObject list = facade.getFiles("timer-log", null);

        assertNotNull(list);
        assertEquals("maven", list.getString("layout"));
        assertEquals(List.of("src/main/resources/camel/timer-log.camel.yaml"), list.getCollection("routeFiles"));
        assertEquals(dir.toString(), list.getString("directory"));
        assertNotNull(list.getString("editing"), "the TUI still says whether the directory is editable");
        JsonArray routes = (JsonArray) list.get("routes");
        assertEquals(1, routes.size());
        JsonObject route = (JsonObject) routes.get(0);
        assertEquals("timer-log", route.getString("routeId"));
        assertEquals("src/main/resources/camel/timer-log.camel.yaml", route.getString("file"));
        assertEquals(4, route.getInteger("line"));
        assertNull(route.get("missing"));

        JsonObject file = facade.getFiles("timer-log", "src/main/resources/camel/timer-log.camel.yaml");
        assertEquals(ROUTE, file.getString("content"));
        assertNull(file.get("routes"), "reading one file does not repeat the route list");
    }

    @Test
    void theIntegrationNameIsAcceptedWhereTheToolAsksForADirectory(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);
        TuiToolRegistry registry = new TuiToolRegistry(facade(dir, "file:" + dir.resolve("demo.camel.yaml") + ":1"));

        // the model passed the integration's name as the directory: answered for that integration, not an error
        String answer = registry.execute("camel_get_files", Map.of("directory", "timer-log"));
        assertTrue(answer.contains("\"routeFiles\":[\"demo.camel.yaml\"]"), answer);
        assertTrue(answer.contains("\"editing\""), "answered by the TUI, with its directory knowledge: " + answer);
    }

    @Test
    void aRouteWithoutAnIdIsListedByItsFileAlone(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);
        McpFacade facade = facade(dir, null, "file:" + dir.resolve("demo.camel.yaml") + ":1");

        JsonObject list = facade.getFiles("timer-log", null);

        JsonArray routes = (JsonArray) list.get("routes");
        assertEquals(1, routes.size());
        JsonObject route = (JsonObject) routes.get(0);
        assertEquals("demo.camel.yaml", route.getString("file"));
        assertFalse(route.containsKey("routeId"), "an anonymous route has no routeId key, not a \"null\" one: " + route);
    }

    @Test
    void aMissingFileIsAnErrorWithTheDirectoryNotAMissingProject(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), ROUTE);
        McpFacade facade = facade(dir, "file:" + dir.resolve("demo.camel.yaml") + ":1");

        JsonObject missing = facade.getFiles("timer-log", "routes/demo.yaml");
        assertNotNull(missing);
        assertTrue(missing.getString("error").contains("No such file"), missing.getString("error"));
        assertEquals(dir.toString(), missing.getString("directory"));

        JsonObject escaped = facade.getFiles("timer-log", "../demo.camel.yaml");
        assertTrue(escaped.getString("error").contains("inside the directory"), escaped.getString("error"));

        JsonObject list = facade.getFiles("timer-log", null);
        assertEquals("flat", list.getString("layout"));
        assertEquals("demo.camel.yaml", ((JsonObject) ((JsonArray) list.get("routes")).get(0)).getString("file"));
    }
}
