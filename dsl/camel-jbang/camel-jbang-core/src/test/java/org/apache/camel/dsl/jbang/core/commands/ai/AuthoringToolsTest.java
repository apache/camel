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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The neutral authoring tools (CAMEL-24695): defined once, exposed by camel-jbang-mcp and the TUI under the same camel_
 * names, self-contained over a project directory argument.
 */
class AuthoringToolsTest {

    private static final String VALID_ROUTE = """
            - route:
                id: timer-log
                from:
                  uri: timer:tick
                  parameters:
                    period: 1000
                  steps:
                    - log:
                        message: "${body}"
                        loggingLevel: WARN
            """;

    private static final String INVALID_ROUTE = VALID_ROUTE.replace("loggingLevel", "logLevel");

    private static JsonObject call(String tool, ToolContext ctx, Map<String, Object> args) {
        Map<String, String> stringArgs = new HashMap<>();
        args.forEach((k, v) -> stringArgs.put(k, String.valueOf(v)));
        Object result = ToolRegistry.execute(tool, ctx, stringArgs);
        try {
            return (JsonObject) Jsoner.deserialize(String.valueOf(result));
        } catch (Exception e) {
            throw new IllegalStateException("Not JSON: " + result, e);
        }
    }

    @Test
    void theSharedSetHasNeutralNamesAndFlags() {
        List<ToolDescriptor> shared = ToolRegistry.authoringTools();
        List<String> names = shared.stream().map(ToolDescriptor::name).toList();
        assertEquals(List.of("camel_catalog_doc", "camel_catalog_find", "camel_catalog_sample", "camel_validate_source",
                "camel_get_files",
                "camel_write_file", "camel_run", "camel_control", "camel_get_log", "camel_get_errors",
                "camel_eval_expression", "camel_dependency_for_class", "camel_error_diagnose"), names);
        for (ToolDescriptor td : shared) {
            assertTrue(td.name().startsWith("camel_"), td.name());
            assertFalse(td.description().isBlank(), td.name());
            assertNotNull(td.executor(), td.name());
        }
        // what changes something is not read-only, so an access filter or a permission handler can tell
        for (String mutating : List.of("camel_write_file", "camel_run", "camel_control")) {
            assertFalse(ToolRegistry.findTool(mutating).isReadOnly(), mutating);
        }
        for (String reading : List.of("camel_catalog_doc", "camel_catalog_sample", "camel_get_files", "camel_get_log",
                "camel_get_errors",
                "camel_eval_expression", "camel_dependency_for_class", "camel_error_diagnose", "camel_validate_source")) {
            assertTrue(ToolRegistry.findTool(reading).isReadOnly(), reading);
        }
        assertTrue(ToolRegistry.findTool("camel_control").isDestructive());
        // the core subset is what a local model gets: find turns a product or protocol into a component, the first
        // question of most tasks (CAMEL-24760); starting an integration is the TUI's own run form there
        assertTrue(ToolRegistry.findTool("camel_catalog_find").isCore(), "a local model asks for mqtt, not paho-mqtt5");
        assertTrue(ToolRegistry.findTool("camel_write_file").isCore());
        assertFalse(ToolRegistry.findTool("camel_run").isCore(), "not needed by a small model");
    }

    @Test
    void inputSchemaListsTheParametersWithTypesAndTheRequiredOnes() {
        JsonObject schema = ToolRegistry.findTool("camel_write_file").inputSchema();
        assertEquals("object", schema.getString("type"));
        JsonObject properties = schema.getMap("properties");
        JsonObject file = properties.getMap("file");
        JsonObject validate = properties.getMap("validate");
        assertEquals("string", file.getString("type"));
        assertEquals("boolean", validate.getString("type"));
        assertEquals(List.of("file", "content"), List.copyOf(schema.getCollection("required")));
        JsonObject logProperties = ToolRegistry.findTool("camel_get_log").inputSchema().getMap("properties");
        JsonObject limit = logProperties.getMap("limit");
        assertEquals("integer", limit.getString("type"));
    }

    @Test
    void filesAreListedAndReadFromTheDirectoryArgument(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), VALID_ROUTE);
        Files.writeString(dir.resolve("application.properties"), "camel.main.name=demo\n");
        Files.createDirectory(dir.resolve("sub"));
        JsonObject list = call("camel_get_files", new ToolContext(), Map.of("directory", dir.toString()));
        assertEquals(2, list.getInteger("totalFiles"), "directories are not listed");
        assertEquals("flat", list.getString("layout"));
        assertEquals(List.of("demo.camel.yaml"), list.getCollection("routeFiles"));
        assertEquals(List.of("application.properties"), list.getCollection("configFiles"));
        JsonObject first = (JsonObject) list.getCollection("files").iterator().next();
        assertEquals("application.properties", first.getString("name"));
        assertEquals("properties", first.getString("type"));
        assertEquals("config", first.getString("kind"));
        JsonObject file = call("camel_get_files", new ToolContext(),
                Map.of("directory", dir.toString(), "file", "demo.camel.yaml"));
        assertEquals(VALID_ROUTE, file.getString("content"));
        assertEquals("yaml", file.getString("type"));
        // the TUI fills the directory in from its selection; a server without one requires it
        ToolContext selected = new ToolContext();
        selected.setDefaultDirectory(dir);
        assertEquals(2, call("camel_get_files", selected, Map.of()).getInteger("totalFiles"));
        ToolExecutionException e = assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("camel_get_files", new ToolContext(), Map.of()));
        assertTrue(e.getMessage().contains("directory is required"), e.getMessage());
        assertThrows(ToolExecutionException.class, () -> ToolRegistry.execute("camel_get_files", new ToolContext(),
                Map.of("directory", dir.toString(), "file", "missing.yaml")));
    }

    @Test
    void writeValidatesFirstAndRefusesInvalidContent(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("demo.camel.yaml"), "- route: {}\n");
        JsonObject invalid = call("camel_write_file", new ToolContext(),
                Map.of("directory", dir.toString(), "file", "demo.camel.yaml", "content", INVALID_ROUTE));
        assertEquals("invalid", invalid.getString("status"));
        assertTrue(invalid.getCollection("errors").stream().anyMatch(m -> m.toString().contains("logLevel")),
                invalid.toJson());
        assertEquals("- route: {}\n", Files.readString(dir.resolve("demo.camel.yaml"), StandardCharsets.UTF_8));

        JsonObject written = call("camel_write_file", new ToolContext(),
                Map.of("directory", dir.toString(), "file", "demo.camel.yaml", "content", VALID_ROUTE));
        assertEquals("overwritten", written.getString("status"));
        assertEquals(VALID_ROUTE, Files.readString(dir.resolve("demo.camel.yaml"), StandardCharsets.UTF_8));
        assertEquals(10, written.getInteger("lines"));

        JsonObject created = call("camel_write_file", new ToolContext(),
                Map.of("directory", dir.toString(), "file", "notes.txt", "content", "logLevel"));
        assertEquals("created", created.getString("status"), "other file types are not validated");
        // validation can be switched off, and a properties file is validated too
        assertEquals("invalid", call("camel_write_file", new ToolContext(), Map.of("directory", dir.toString(),
                "file", "application.properties", "content", "camel.main.nme=x")).getString("status"));
        assertEquals("created", call("camel_write_file", new ToolContext(), Map.of("directory", dir.toString(),
                "file", "application.properties", "content", "camel.main.nme=x", "validate", false))
                .getString("status"));
    }

    @Test
    void filePathsStayInsideTheDirectoryButMayNameASubdirectory(@TempDir Path dir) throws IOException {
        for (String bad : List.of("../etc/passwd", "/tmp/x.yaml", "sub/../../x.yaml")) {
            ToolExecutionException e = assertThrows(ToolExecutionException.class,
                    () -> ToolRegistry.execute("camel_write_file", new ToolContext(),
                            Map.of("directory", dir.toString(), "file", bad, "content", "x")));
            assertTrue(e.getMessage().contains("inside the directory") || e.getMessage().contains("relative to"),
                    bad + ": " + e.getMessage());
        }
        // a relative path is fine, and its directories are created
        JsonObject written = call("camel_write_file", new ToolContext(),
                Map.of("directory", dir.toString(), "file", "src/main/resources/camel/x.camel.yaml", "content",
                        VALID_ROUTE));
        assertEquals("created", written.getString("status"));
        assertEquals(VALID_ROUTE, Files.readString(dir.resolve("src/main/resources/camel/x.camel.yaml")));
    }

    @Test
    void aMavenProjectListsItsRouteFilesFirstAndReadsByRelativePath(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("pom.xml"), "<project/>");
        Files.createDirectories(dir.resolve("src/main/resources/camel"));
        Files.createDirectories(dir.resolve("src/main/java/org/acme"));
        Files.createDirectories(dir.resolve("target/classes/camel"));
        Files.createDirectories(dir.resolve(".mvn"));
        Files.writeString(dir.resolve("src/main/resources/camel/timer-log.camel.yaml"), VALID_ROUTE);
        Files.writeString(dir.resolve("src/main/resources/application.properties"), "camel.main.name=timer-log\n");
        Files.writeString(dir.resolve("src/main/resources/log4j2.properties"), "rootLogger.level=info\n");
        Files.writeString(dir.resolve("src/main/java/org/acme/MyRoute.java"),
                "package org.acme;\nimport org.apache.camel.builder.RouteBuilder;\n"
                                                                              + "public class MyRoute extends RouteBuilder { public void configure() { } }\n");
        Files.writeString(dir.resolve("src/main/java/org/acme/Helper.java"), "package org.acme;\nclass Helper { }\n");
        Files.writeString(dir.resolve("target/classes/camel/timer-log.camel.yaml"), VALID_ROUTE);
        Files.writeString(dir.resolve(".mvn/maven.config"), "-T1\n");
        Files.writeString(dir.resolve("README.md"), "# demo\n");

        JsonObject list = call("camel_get_files", new ToolContext(), Map.of("directory", dir.toString()));
        assertEquals("maven", list.getString("layout"));
        assertEquals(List.of("src/main/java/org/acme/MyRoute.java", "src/main/resources/camel/timer-log.camel.yaml"),
                list.getCollection("routeFiles"));
        assertEquals(List.of("src/main/resources/application.properties"), list.getCollection("configFiles"));
        assertEquals(7, list.getInteger("totalFiles"), "target and .mvn are skipped");
        assertTrue(list.getString("hint").contains("src/main/resources/camel"), list.getString("hint"));
        List<String> names = new ArrayList<>();
        for (Object o : list.getCollection("files")) {
            names.add(((JsonObject) o).getString("name"));
        }
        assertTrue(names.contains("pom.xml") && names.contains("src/main/resources/log4j2.properties"), names.toString());
        assertTrue(names.stream().noneMatch(n -> n.startsWith("target/") || n.startsWith(".mvn/")), names.toString());

        JsonObject file = call("camel_get_files", new ToolContext(),
                Map.of("directory", dir.toString(), "file", "src/main/resources/camel/timer-log.camel.yaml"));
        assertEquals(VALID_ROUTE, file.getString("content"));
        ToolExecutionException e = assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("camel_get_files", new ToolContext(),
                        Map.of("directory", dir.toString(), "file", "camel/timer-log.camel.yaml")));
        assertTrue(e.getMessage().contains("routeFiles"), "the error says how to find the file: " + e.getMessage());
    }

    @Test
    void routeSourcesMapTheRuntimesLocationsOntoProjectFiles(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("src/main/resources/camel"));
        Files.createDirectories(dir.resolve("src/main/java/org/acme"));
        Files.writeString(dir.resolve("src/main/resources/camel/timer-log.camel.yaml"), VALID_ROUTE);
        Files.writeString(dir.resolve("src/main/java/org/acme/MyRoute.java"), "class MyRoute {}");
        Files.writeString(dir.resolve("flat.camel.yaml"), VALID_ROUTE);

        // Spring Boot fat jar, Quarkus / classpath, a plain file, a Java class, a moved file and an unknown one
        List<Map<String, String>> routes = List.of(
                Map.of("routeId", "boot", "source", "nested:" + dir + "/target/app-1.0.jar/!BOOT-INF/classes/!/camel/"
                                                    + "timer-log.camel.yaml:4"),
                Map.of("routeId", "cp", "source", "classpath:camel/timer-log.camel.yaml:7"),
                Map.of("routeId", "abs", "source", "file:" + dir.resolve("flat.camel.yaml") + ":1"),
                Map.of("routeId", "java", "source", "org.acme.MyRoute:12"),
                Map.of("routeId", "moved", "source", "file:old/place/flat.camel.yaml"),
                Map.of("routeId", "gone", "source", "classpath:camel/nowhere.yaml:3"));
        JsonArray mapped = AuthoringTools.routeSources(routes, dir);

        assertEquals(6, mapped.size());
        JsonObject boot = (JsonObject) mapped.get(0);
        assertEquals("src/main/resources/camel/timer-log.camel.yaml", boot.getString("file"));
        assertEquals(4, boot.getInteger("line"));
        assertEquals(null, boot.get("missing"));
        assertEquals("src/main/resources/camel/timer-log.camel.yaml", ((JsonObject) mapped.get(1)).getString("file"));
        assertEquals("flat.camel.yaml", ((JsonObject) mapped.get(2)).getString("file"));
        assertEquals("src/main/java/org/acme/MyRoute.java", ((JsonObject) mapped.get(3)).getString("file"));
        assertEquals(12, ((JsonObject) mapped.get(3)).getInteger("line"));
        assertEquals("flat.camel.yaml", ((JsonObject) mapped.get(4)).getString("file"), "found by name");
        JsonObject gone = (JsonObject) mapped.get(5);
        assertEquals("camel/nowhere.yaml", gone.getString("file"));
        assertEquals(Boolean.TRUE, gone.get("missing"));
        assertTrue(AuthoringTools.routeSources(null, dir).isEmpty());
        // a route without an id is listed by its file alone, not as "null"
        JsonObject anonymous = (JsonObject) AuthoringTools.routeSources(
                List.of(Map.of("source", "file:" + dir.resolve("flat.camel.yaml"))), dir).get(0);
        assertEquals("flat.camel.yaml", anonymous.getString("file"));
        assertFalse(anonymous.containsKey("routeId"));
        // a digit suffix too long for a line number is not one: no exception, no line
        JsonObject overflow = (JsonObject) AuthoringTools.routeSources(
                List.of(Map.of("routeId", "big", "source", "file:flat.camel.yaml:2147483648")), dir).get(0);
        assertEquals("flat.camel.yaml:2147483648", overflow.getString("file"));
        assertFalse(overflow.containsKey("line"));
        assertEquals(Boolean.TRUE, overflow.get("missing"));
    }

    @Test
    void validateSourceChecksContentOrAnExistingFile(@TempDir Path dir) throws IOException {
        JsonObject content = call("camel_validate_source", new ToolContext(),
                Map.of("file", "new.camel.yaml", "content", INVALID_ROUTE));
        assertFalse(content.getBoolean("valid"));
        assertEquals(1, content.getCollection("errors").size());
        assertTrue(call("camel_validate_source", new ToolContext(),
                Map.of("file", "new.camel.yaml", "content", VALID_ROUTE)).getBoolean("valid"));

        Files.writeString(dir.resolve("demo.camel.yaml"), INVALID_ROUTE);
        JsonObject file = call("camel_validate_source", new ToolContext(),
                Map.of("directory", dir.toString(), "file", "demo.camel.yaml"));
        assertFalse(file.getBoolean("valid"), "the file is read when no content is given");

        ToolExecutionException e = assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("camel_validate_source", new ToolContext(),
                        Map.of("file", "notes.txt", "content", "x")));
        assertTrue(e.getMessage().contains("No validation for notes.txt"), e.getMessage());
    }

    @Test
    void catalogFindAnswersAProtocolOrProductName() {
        JsonObject result = call("camel_catalog_find", new ToolContext(), Map.of("term", "mqtt", "kind", "component"));
        assertTrue(result.getInteger("count") > 0);
        JsonObject first = (JsonObject) result.getCollection("matches").iterator().next();
        assertEquals("component", first.getString("kind"));
        assertTrue(first.getString("name").contains("mqtt"), first.toJson());
        assertNotNull(first.get("syntax"));
    }

    @Test
    void errorDiagnoseNamesTheExceptionAndTheComponent() {
        String error = "org.apache.camel.ResolveEndpointFailedException: Failed to resolve endpoint:"
                       + " timer://tick?periodd=1000 due to: There are 1 parameters that couldn't be set on the endpoint";
        JsonObject result = call("camel_error_diagnose", new ToolContext(), Map.of("error", error));
        JsonObject summary = result.getMap("summary");
        assertTrue(summary.getBoolean("diagnosed"));
        JsonObject ex = (JsonObject) result.getCollection("identifiedExceptions").iterator().next();
        assertEquals("ResolveEndpointFailedException", ex.getString("exception"));
        assertFalse(ex.getCollection("suggestedFixes").isEmpty());
        assertTrue(result.getCollection("identifiedComponents").stream()
                .anyMatch(c -> "timer".equals(((JsonObject) c).getString("name"))), result.toJson());
        JsonObject none = call("camel_error_diagnose", new ToolContext(), Map.of("error", "nothing camel here"))
                .getMap("summary");
        assertFalse(none.getBoolean("diagnosed"));
    }

    @Test
    void runtimeToolsSayWhatToDoWithoutAnIntegration() {
        ToolExecutionException e = assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("camel_get_errors", new ToolContext(), Map.of("name", "no-such-app-xyz")));
        assertTrue(e.getMessage().contains("no-such-app-xyz"), e.getMessage());
        e = assertThrows(ToolExecutionException.class,
                () -> ToolRegistry.execute("camel_control", new ToolContext(), Map.of("action", "stop", "name",
                        "no-such-app-xyz")));
        assertTrue(e.getMessage().contains("no-such-app-xyz"), e.getMessage());
    }
}
