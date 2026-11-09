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
package org.apache.camel.component.mcp.server.vertx.integration;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mcp.server.McpServerBridge;
import org.apache.camel.component.mcp.server.McpServerConfiguration;
import org.apache.camel.component.platform.http.main.MainHttpServer;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.fail;

/**
 * Runs the official {@code @modelcontextprotocol/conformance} npm suite against the Camel Main HTTP MCP server.
 * <p>
 * The suite ({@code npx @modelcontextprotocol/conformance server --url ...}) connects to the server as an MCP client,
 * sends protocol-defined requests, and validates the responses against the MCP specification. Known gaps — features not
 * yet implemented by Camel's MCP server — are listed in {@code expected-failures.yaml} so the test stays green while
 * regressions still cause CI to fail.
 * <p>
 * The server routes registered here deliberately match the tool names and return values documented by each conformance
 * scenario. Run {@code npx @modelcontextprotocol/conformance server --url ... --scenario <name>} against a live server
 * to view the per-scenario requirements.
 * <p>
 * <strong>Prerequisites:</strong> Node.js ≥ 18 and npm registry access (for the first run; npx caches the package after
 * the initial download). Enable this test by activating the {@code oc} Maven profile ({@code -Poc}) or by setting the
 * system property {@code -Dofficial-mcp-conformance}.
 */
@EnabledIfSystemProperty(named = "official-mcp-conformance", matches = ".*")
class OfficialMcpConformanceIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(OfficialMcpConformanceIT.class);

    /**
     * Tag used to select which ai-tool routes are exposed by the bridge. All conformance routes share this tag so that
     * non-conformance routes registered elsewhere in test infrastructure are never accidentally exposed.
     */
    private static final String CONFORMANCE_TAG = "official-conformance";

    /**
     * The pinned npm package reference for the official conformance suite. Used in both the command and the re-run hint
     * so bumping the version only requires changing this constant.
     */
    private static final String CONFORMANCE_PACKAGE = "@modelcontextprotocol/conformance@0.1.16";

    /**
     * Minimal 1×1 red-pixel PNG encoded as Base64, used by the tools-call-image and tools-call-mixed-content scenarios.
     */
    private static final String MINIMAL_PNG_BASE64
            = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI6QAAAABJRU5ErkJggg==";

    /**
     * Minimal silent WAV (44-byte RIFF/PCM header, 0 samples) as Base64, for the tools-call-audio scenario.
     */
    private static final String MINIMAL_WAV_BASE64
            = "UklGRiQAAABXQVZFZm10IBAAAAABAAEARKwAAIhYAQACABAAZGF0YQAAAAA=";

    private final int port = AvailablePortFinder.getNextAvailable();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        MainHttpServer server = new MainHttpServer();
        server.setCamelContext(camelContext);
        server.setHost("0.0.0.0");
        server.setPort(port);
        camelContext.addService(server);

        McpServerConfiguration configuration = new McpServerConfiguration();
        configuration.setTags(CONFORMANCE_TAG);
        McpServerBridge bridge = new McpServerBridge(configuration);
        camelContext.addService(bridge);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                // ---- resources-read-text ---------------------------------------------------
                // The conformance suite reads test://static-text and expects a TextResourceContents
                // response. VertxMcpServerEngine routes reads to ai-resource routes by URI.
                from("ai-resource:static_text?resourceUri=test://static-text&tags=" + CONFORMANCE_TAG
                     + "&description=A static text resource for conformance testing&mimeType=text/plain")
                        .routeId("conformance-resource-static-text")
                        .setBody(constant("This is a static text resource."));

                // ---- resources-read-binary -------------------------------------------------
                // The conformance suite reads test://static-binary and expects a BlobResourceContents
                // response with mimeType=image/png. The engine returns the byte[] as a blob when
                // the route body is a byte[] and mimeType is set.
                from("ai-resource:static_binary?resourceUri=test://static-binary&tags=" + CONFORMANCE_TAG
                     + "&description=A static binary resource for conformance testing&mimeType=image/png")
                        .routeId("conformance-resource-static-binary")
                        .setBody(constant(Base64.getDecoder().decode(MINIMAL_PNG_BASE64)));

                // ---- tools-call-simple-text ------------------------------------------------
                // Expected: text "This is a simple text response for testing."
                from("ai-tool:test_simple_text?tags=" + CONFORMANCE_TAG
                     + "&description=Returns simple text content")
                        .routeId("conformance-simple-text")
                        .setBody(constant("This is a simple text response for testing."));

                // ---- tools-call-image (expected failure) ------------------------------------
                // Requires ImageContent in the MCP response — Camel wraps String bodies as TextContent only.
                from("ai-tool:test_image_content?tags=" + CONFORMANCE_TAG
                     + "&description=Returns an image content item")
                        .routeId("conformance-image")
                        .setBody(constant(MINIMAL_PNG_BASE64));

                // ---- tools-call-audio (expected failure) ------------------------------------
                // Requires AudioContent in the MCP response — not yet supported.
                from("ai-tool:test_audio_content?tags=" + CONFORMANCE_TAG
                     + "&description=Returns an audio content item")
                        .routeId("conformance-audio")
                        .setBody(constant(MINIMAL_WAV_BASE64));

                // ---- tools-call-embedded-resource (expected failure) ------------------------
                // Requires ResourceContent in the MCP response — not yet supported.
                from("ai-tool:test_embedded_resource?tags=" + CONFORMANCE_TAG
                     + "&description=Returns a resource content item")
                        .routeId("conformance-embedded-resource")
                        .setBody(constant("This is an embedded resource content."));

                // ---- tools-call-mixed-content (expected failure) ----------------------------
                // Requires multiple content types in one response — not yet supported.
                from("ai-tool:test_multiple_content_types?tags=" + CONFORMANCE_TAG
                     + "&description=Returns multiple content types")
                        .routeId("conformance-mixed-content")
                        .setBody(constant("Multiple content types test:"));

                // ---- tools-call-with-logging (expected failure) -----------------------------
                // Requires server→client log notifications during tool execution — not yet supported.
                from("ai-tool:test_tool_with_logging?tags=" + CONFORMANCE_TAG
                     + "&description=Sends log notifications during execution")
                        .routeId("conformance-with-logging")
                        .setBody(constant("Tool execution completed"));

                // ---- tools-call-error -------------------------------------------------------
                // Scenario expects: isError=true with non-empty error text.
                // Camel MCP bridge wraps uncaught exceptions as isError=true responses with a
                // generic "Tool execution failed" message; the scenario only checks non-empty.
                from("ai-tool:test_error_handling?tags=" + CONFORMANCE_TAG
                     + "&description=Always returns an error")
                        .routeId("conformance-error")
                        .process(e -> {
                            throw new IllegalStateException("This tool intentionally returns an error for testing");
                        });

                // ---- tools-call-with-progress (expected failure) ----------------------------
                // Requires progress notifications (notifications/progress) — not yet supported.
                from("ai-tool:test_tool_with_progress?tags=" + CONFORMANCE_TAG
                     + "&description=Reports progress during execution")
                        .routeId("conformance-progress")
                        .setBody(constant("Tool execution completed with progress"));

                // ---- tools-call-sampling (expected failure) ---------------------------------
                // Requires the server to call sampling/createMessage on the client — not yet supported.
                from("ai-tool:test_sampling?tags=" + CONFORMANCE_TAG
                     + "&description=Requests LLM sampling from the client"
                     + "&parameter.prompt=string&parameter.prompt.description=The prompt to sample&parameter.prompt.required=true")
                        .routeId("conformance-sampling")
                        .setBody(constant("Sampling not supported"));

                // ---- tools-call-elicitation + elicitation-sep* (expected failures) ----------
                // Requires the server to call elicitation/create on the client — not yet supported.
                from("ai-tool:test_elicitation?tags=" + CONFORMANCE_TAG
                     + "&description=Requests user input via elicitation"
                     + "&parameter.message=string&parameter.message.description=Message to show&parameter.message.required=true")
                        .routeId("conformance-elicitation")
                        .setBody(constant("Elicitation not supported"));

                from("ai-tool:test_elicitation_sep1034_defaults?tags=" + CONFORMANCE_TAG
                     + "&description=Elicitation with default values (SEP-1034)")
                        .routeId("conformance-elicitation-sep1034")
                        .setBody(constant("Elicitation not supported"));

                from("ai-tool:test_elicitation_sep1330_enums?tags=" + CONFORMANCE_TAG
                     + "&description=Elicitation with enum schemas (SEP-1330)")
                        .routeId("conformance-elicitation-sep1330")
                        .setBody(constant("Elicitation not supported"));
            }
        };
    }

    @Test
    void testOfficialMcpConformanceSuite() throws Exception {
        String serverUrl = "http://localhost:" + port + "/mcp";
        Path expectedFailures = expectedFailuresPath();

        LOG.info("Running official MCP conformance suite against {}", serverUrl);

        List<String> command = buildConformanceCommand(serverUrl, expectedFailures);

        Path log = Files.createTempFile("mcp-conformance", ".log");
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("CI", "true");
        pb.redirectErrorStream(true);
        pb.redirectOutput(log.toFile());
        Process process = pb.start();
        try {
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            String output = Files.readString(log, StandardCharsets.UTF_8);
            LOG.info("[conformance]\n{}", output);
            if (!finished) {
                fail("Official MCP conformance suite timed out after 5 minutes.\nOutput:\n" + output);
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                fail("Official MCP conformance suite failed (exit " + exitCode + ").\n"
                     + "Output:\n" + output + "\n"
                     + "To register a known failure add the scenario name to " + expectedFailures.getFileName() + ".\n"
                     + "Re-run manually: npx " + CONFORMANCE_PACKAGE + " server"
                     + " --url " + serverUrl
                     + " --expected-failures " + expectedFailures.toAbsolutePath());
            }
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            Files.deleteIfExists(log);
        }
    }

    // -------------------------------------------------- helpers

    /**
     * Locates {@code expected-failures.yaml} from the test-resources directory. Maven copies the file to
     * {@code target/test-classes/} during the process-test-resources phase, so the classpath lookup works both under
     * Maven and in an IDE that mirrors that layout.
     */
    private static Path expectedFailuresPath() throws Exception {
        URL resource = OfficialMcpConformanceIT.class.getResource("/expected-failures.yaml");
        if (resource != null) {
            return Paths.get(resource.toURI());
        }
        // Fallback for IDE runs whose resource root is the source directory
        Path fromSource = Paths.get("src/test/resources/expected-failures.yaml");
        if (Files.exists(fromSource)) {
            return fromSource;
        }
        // Return a path whose absence will be reported clearly by npx
        return Paths.get("expected-failures.yaml");
    }

    private static List<String> buildConformanceCommand(String serverUrl, Path expectedFailures) throws Exception {
        if (!Files.exists(expectedFailures)) {
            fail("expected-failures.yaml not found at " + expectedFailures.toAbsolutePath()
                 + " — cannot run the conformance suite without a baseline");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add("npx");
        cmd.add("--yes");
        cmd.add(CONFORMANCE_PACKAGE);
        cmd.add("server");
        cmd.add("--url");
        cmd.add(serverUrl);
        cmd.add("--expected-failures");
        cmd.add(expectedFailures.toAbsolutePath().toString());
        return cmd;
    }
}
