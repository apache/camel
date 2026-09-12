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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        assertEquals(List.of("camel_catalog_doc", "camel_catalog_find", "camel_validate_source", "camel_get_files",
                "camel_write_file", "camel_run", "camel_control", "camel_get_log", "camel_get_errors",
                "camel_eval_expression", "camel_error_diagnose"), names);
        for (ToolDescriptor td : shared) {
            assertTrue(td.name().startsWith("camel_"), td.name());
            assertFalse(td.description().isBlank(), td.name());
            assertNotNull(td.executor(), td.name());
        }
        // what changes something is not read-only, so an access filter or a permission handler can tell
        for (String mutating : List.of("camel_write_file", "camel_run", "camel_control")) {
            assertFalse(ToolRegistry.findTool(mutating).isReadOnly(), mutating);
        }
        for (String reading : List.of("camel_catalog_doc", "camel_get_files", "camel_get_log", "camel_get_errors",
                "camel_eval_expression", "camel_error_diagnose", "camel_validate_source")) {
            assertTrue(ToolRegistry.findTool(reading).isReadOnly(), reading);
        }
        assertTrue(ToolRegistry.findTool("camel_control").isDestructive());
        assertFalse(ToolRegistry.findTool("camel_catalog_find").isCore(), "not needed by a small model");
        assertTrue(ToolRegistry.findTool("camel_write_file").isCore());
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
        JsonObject first = (JsonObject) list.getCollection("files").iterator().next();
        assertEquals("application.properties", first.getString("name"));
        assertEquals("properties", first.getString("type"));
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
    void fileNamesStayInsideTheDirectory(@TempDir Path dir) {
        for (String bad : List.of("../etc/passwd", "sub/x.yaml", "/tmp/x.yaml")) {
            ToolExecutionException e = assertThrows(ToolExecutionException.class,
                    () -> ToolRegistry.execute("camel_write_file", new ToolContext(),
                            Map.of("directory", dir.toString(), "file", bad, "content", "x")));
            assertTrue(e.getMessage().contains("plain file name"), bad + ": " + e.getMessage());
        }
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
