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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * MCP Tool for generating JUnit 5 test scaffolding for Camel routes.
 * <p>
 * Given a YAML or XML route definition, this tool produces a test class skeleton with CamelTestSupport (or
 * {@code @CamelSpringBootTest} for Spring Boot), mock endpoints for producers, and {@code @RegisterExtension} stubs for
 * infrastructure components like Kafka, JMS, MongoDB, etc.
 */
@ApplicationScoped
public class TestScaffoldTools {

    // YAML endpoint extraction patterns
    private static final Pattern YAML_FROM_URI
            = Pattern.compile("from:\\s*\\n\\s+uri:\\s*[\"']?([^\"'\\s#]+)", Pattern.MULTILINE);
    private static final Pattern YAML_FROM_INLINE
            = Pattern.compile("from:\\s+[\"']?([a-zA-Z][a-zA-Z0-9+.-]*:[^\"'\\s#]+)", Pattern.MULTILINE);
    private static final Pattern YAML_TO_URI
            = Pattern.compile("-\\s+to:\\s*\\n\\s+uri:\\s*[\"']?([^\"'\\s#]+)", Pattern.MULTILINE);
    private static final Pattern YAML_TO_INLINE
            = Pattern.compile("-\\s+to:\\s+[\"']?([a-zA-Z][a-zA-Z0-9+.-]*:[^\"'\\s#]+)", Pattern.MULTILINE);
    private static final Pattern YAML_TOD_URI
            = Pattern.compile("-\\s+toD:\\s*\\n\\s+uri:\\s*[\"']?([^\"'\\s#]+)", Pattern.MULTILINE);
    private static final Pattern YAML_TOD_INLINE
            = Pattern.compile("-\\s+toD:\\s+[\"']?([a-zA-Z][a-zA-Z0-9+.-]*:[^\"'\\s#]+)", Pattern.MULTILINE);

    // XML endpoint extraction patterns
    private static final Pattern XML_FROM = Pattern.compile("<from\\s+uri=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_TO = Pattern.compile("<to\\s+uri=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_TOD = Pattern.compile("<toD\\s+uri=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    @Inject
    CatalogService catalogService;

    @Inject
    TestInfraData testInfraData;

    /**
     * Tool to generate a JUnit 5 test skeleton for a Camel route.
     */
    @Tool(description = "Generate a JUnit 5 test skeleton for a Camel route. "
                        + "Given a YAML or XML route definition, produces a test class with "
                        + "CamelTestSupport or @CamelSpringBootTest boilerplate, "
                        + "mock endpoints for producer endpoints, MockEndpoint assertions, "
                        + "and @RegisterExtension stubs for infrastructure components (Kafka, AWS, etc.).")
    public String camel_route_test_scaffold(
            @ToolArg(description = "The Camel route definition (YAML or XML)") String route,
            @ToolArg(description = "Route format: yaml or xml (default: yaml)") String format,
            @ToolArg(description = "Target runtime: main or spring-boot (default: main)") String runtime,
            @ToolArg(description = "Camel version to use for catalog lookups (e.g., 4.17.0). "
                                   + "If not specified, uses the default catalog version.") String camelVersion,
            @ToolArg(description = "Platform BOM coordinates in GAV format (groupId:artifactId:version). "
                                   + "When provided, overrides camelVersion for catalog lookups.") String platformBom) {

        if (route == null || route.isBlank()) {
            throw new ToolCallException("Route content is required", null);
        }

        try {
            boolean hasVersion = camelVersion != null && !camelVersion.isBlank();
            boolean hasBom = platformBom != null && !platformBom.isBlank();
            CamelCatalog catalog;
            if (hasVersion || hasBom) {
                catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);
            } else {
                catalog = catalogService.getDefaultCatalog();
            }

            String resolvedFormat = resolveFormat(format);
            String resolvedRuntime = resolveRuntime(runtime);

            // Extract endpoints from route
            List<String> fromEndpoints = extractFromEndpoints(route, resolvedFormat);
            List<String> toEndpoints = extractToEndpoints(route, resolvedFormat);

            // Collect all unique component schemes
            List<String> allSchemes = collectSchemes(fromEndpoints, toEndpoints);

            // Determine which to endpoints need mock replacements
            List<String> mockEndpoints = toEndpoints.stream()
                    .filter(uri -> {
                        String scheme = extractScheme(uri);
                        return scheme != null && !testInfraData.getTrivialSchemes().contains(scheme);
                    })
                    .toList();

            // Determine test-infra services needed (deduplicated)
            List<TestInfraData.TestInfraInfo> infraServices = resolveInfraServices(allSchemes);

            // Generate test code
            String testCode = "spring-boot".equals(resolvedRuntime)
                    ? generateSpringBootTest(fromEndpoints, mockEndpoints, infraServices)
                    : generateMainTest(fromEndpoints, mockEndpoints, infraServices);

            // Build JSON result
            return buildResult(catalog, testCode, resolvedFormat, resolvedRuntime,
                    allSchemes, fromEndpoints, toEndpoints, mockEndpoints, infraServices);

        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to generate test scaffold (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }
    }

    // ---- Endpoint extraction ----

    List<String> extractFromEndpoints(String route, String format) {
        List<String> endpoints = new ArrayList<>();
        if ("xml".equals(format)) {
            addMatches(endpoints, XML_FROM, route);
        } else {
            addMatches(endpoints, YAML_FROM_URI, route);
            if (endpoints.isEmpty()) {
                addMatches(endpoints, YAML_FROM_INLINE, route);
            }
        }
        return endpoints;
    }

    List<String> extractToEndpoints(String route, String format) {
        List<String> endpoints = new ArrayList<>();
        if ("xml".equals(format)) {
            addMatches(endpoints, XML_TO, route);
            addMatches(endpoints, XML_TOD, route);
        } else {
            addMatches(endpoints, YAML_TO_URI, route);
            addMatches(endpoints, YAML_TO_INLINE, route);
            addMatches(endpoints, YAML_TOD_URI, route);
            addMatches(endpoints, YAML_TOD_INLINE, route);
        }
        return endpoints;
    }

    private void addMatches(List<String> list, Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        while (m.find()) {
            String uri = m.group(1).trim();
            // Remove trailing quotes if present
            if (uri.endsWith("\"") || uri.endsWith("'")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            if (!uri.isEmpty()) {
                list.add(uri);
            }
        }
    }

    // ---- Scheme extraction and mock naming ----

    String extractScheme(String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        // Handle https:// and http:// specially
        if (uri.startsWith("https://")) {
            return "https";
        }
        if (uri.startsWith("http://")) {
            return "http";
        }
        int idx = uri.indexOf(':');
        if (idx > 0) {
            return uri.substring(0, idx);
        }
        return null;
    }

    String toMockName(String uri) {
        String scheme = extractScheme(uri);
        if (scheme == null) {
            return "mock:unknown";
        }

        // Extract path part (after scheme: and optional //)
        String rest = uri.substring(scheme.length());
        if (rest.startsWith("://")) {
            rest = rest.substring(3);
        } else if (rest.startsWith(":")) {
            rest = rest.substring(1);
        }

        // Remove query parameters
        int qIdx = rest.indexOf('?');
        if (qIdx >= 0) {
            rest = rest.substring(0, qIdx);
        }

        // Sanitize: replace non-alphanumeric with hyphens, collapse, trim
        String sanitized = rest.replaceAll("[^a-zA-Z0-9]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (sanitized.isEmpty()) {
            return "mock:" + scheme;
        }
        return "mock:" + scheme + "-" + sanitized;
    }

    String toMockVariableName(String mockUri) {
        // mock:kafka-orders -> mockKafkaOrders
        String name = mockUri.replace("mock:", "");
        StringBuilder sb = new StringBuilder("mock");
        boolean capitalizeNext = true;
        for (char c : name.toCharArray()) {
            if (c == '-' || c == '_') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
                capitalizeNext = false;
            }
        }
        return sb.toString();
    }

    // ---- Test-infra resolution ----

    private List<TestInfraData.TestInfraInfo> resolveInfraServices(List<String> schemes) {
        Set<TestInfraData.TestInfraInfo> seen = new LinkedHashSet<>();
        for (String scheme : schemes) {
            TestInfraData.TestInfraInfo info = testInfraData.getTestInfra(scheme);
            if (info != null) {
                seen.add(info);
            }
        }
        return new ArrayList<>(seen);
    }

    // ---- Code generation ----

    private String generateMainTest(
            List<String> fromEndpoints, List<String> mockEndpoints,
            List<TestInfraData.TestInfraInfo> infraServices) {

        StringBuilder sb = new StringBuilder();

        // Imports
        sb.append("import org.apache.camel.RoutesBuilder;\n");
        sb.append("import org.apache.camel.builder.RouteBuilder;\n");
        if (!mockEndpoints.isEmpty()) {
            sb.append("import org.apache.camel.component.mock.MockEndpoint;\n");
        }
        sb.append("import org.apache.camel.test.junit5.CamelTestSupport;\n");
        if (usesNotifyBuilder(fromEndpoints)) {
            sb.append("import org.apache.camel.builder.NotifyBuilder;\n");
            sb.append("import java.util.concurrent.TimeUnit;\n");
        }
        sb.append("import org.junit.jupiter.api.Test;\n");
        for (TestInfraData.TestInfraInfo info : infraServices) {
            sb.append("import org.junit.jupiter.api.extension.RegisterExtension;\n");
            break;
        }
        for (TestInfraData.TestInfraInfo info : infraServices) {
            sb.append("import ").append(info.packageName()).append('.').append(info.serviceClass()).append(";\n");
            sb.append("import ").append(info.packageName()).append('.').append(info.factoryClass()).append(";\n");
        }
        sb.append("\n");
        sb.append("import static org.junit.jupiter.api.Assertions.assertTrue;\n");
        sb.append("\n");

        // Class declaration
        sb.append("class RouteTest extends CamelTestSupport {\n\n");

        // @RegisterExtension fields
        for (TestInfraData.TestInfraInfo info : infraServices) {
            String fieldName = Character.toLowerCase(info.serviceClass().charAt(0)) + info.serviceClass().substring(1);
            sb.append("    @RegisterExtension\n");
            sb.append("    static ").append(info.serviceClass()).append(' ').append(fieldName);
            sb.append(" = ").append(info.factoryClass()).append(".createService();\n\n");
        }

        // createRouteBuilder
        sb.append("    @Override\n");
        sb.append("    protected RoutesBuilder createRouteBuilder() {\n");
        sb.append("        return new RouteBuilder() {\n");
        sb.append("            @Override\n");
        sb.append("            public void configure() {\n");

        String fromUri = fromEndpoints.isEmpty() ? "direct:start" : fromEndpoints.get(0);
        String fromScheme = extractScheme(fromUri);
        boolean isSendable = fromScheme != null && testInfraData.getSendableSchemes().contains(fromScheme);
        boolean isTimer = "timer".equals(fromScheme);

        if (!isSendable && !isTimer && !fromEndpoints.isEmpty()) {
            sb.append("                // TODO: Replace with actual consumer URI or use direct:start for unit testing\n");
        }
        sb.append("                from(\"").append(escapeJava(fromUri)).append("\")\n");

        if (mockEndpoints.isEmpty()) {
            sb.append("                    .log(\"Route executed\");\n");
        } else {
            for (int i = 0; i < mockEndpoints.size(); i++) {
                String mockName = toMockName(mockEndpoints.get(i));
                String originalUri = mockEndpoints.get(i);
                sb.append("                    .to(\"").append(escapeJava(mockName)).append("\")");
                sb.append(" // replaces ").append(extractScheme(originalUri)).append(":...");
                if (i == mockEndpoints.size() - 1) {
                    sb.append(";\n");
                } else {
                    sb.append("\n");
                }
            }
        }

        sb.append("            }\n");
        sb.append("        };\n");
        sb.append("    }\n\n");

        // Test method
        sb.append("    @Test\n");
        sb.append("    void testRoute() throws Exception {\n");

        // Mock endpoint declarations
        for (String mockUri : mockEndpoints) {
            String mockName = toMockName(mockUri);
            String varName = toMockVariableName(mockName);
            sb.append("        MockEndpoint ").append(varName).append(" = getMockEndpoint(\"");
            sb.append(escapeJava(mockName)).append("\");\n");
            sb.append("        ").append(varName).append(".expectedMinimumMessageCount(1);\n\n");
        }

        // Send or wait
        if (isTimer) {
            sb.append("        // Timer route fires automatically; use NotifyBuilder to wait\n");
            sb.append("        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();\n");
            sb.append(
                    "        assertTrue(notify.matches(10, TimeUnit.SECONDS), \"Route should complete within timeout\");\n\n");
        } else if (isSendable) {
            sb.append("        template.sendBody(\"").append(escapeJava(fromUri)).append("\", \"test message\");\n\n");
        } else {
            sb.append("        // TODO: Send a test message to trigger the route\n");
            sb.append("        // template.sendBody(\"").append(escapeJava(fromUri)).append("\", \"test message\");\n\n");
        }

        // Assert
        if (!mockEndpoints.isEmpty()) {
            sb.append("        MockEndpoint.assertIsSatisfied(context);\n");
        }

        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    private String generateSpringBootTest(
            List<String> fromEndpoints, List<String> mockEndpoints,
            List<TestInfraData.TestInfraInfo> infraServices) {

        StringBuilder sb = new StringBuilder();

        // Imports
        sb.append("import org.apache.camel.CamelContext;\n");
        sb.append("import org.apache.camel.ProducerTemplate;\n");
        if (!mockEndpoints.isEmpty()) {
            sb.append("import org.apache.camel.component.mock.MockEndpoint;\n");
        }
        sb.append("import org.apache.camel.test.spring.junit5.CamelSpringBootTest;\n");
        if (usesNotifyBuilder(fromEndpoints)) {
            sb.append("import org.apache.camel.builder.NotifyBuilder;\n");
            sb.append("import java.util.concurrent.TimeUnit;\n");
        }
        sb.append("import org.junit.jupiter.api.Test;\n");
        sb.append("import org.springframework.beans.factory.annotation.Autowired;\n");
        sb.append("import org.springframework.boot.test.context.SpringBootTest;\n");
        for (TestInfraData.TestInfraInfo info : infraServices) {
            sb.append("import org.junit.jupiter.api.extension.RegisterExtension;\n");
            break;
        }
        for (TestInfraData.TestInfraInfo info : infraServices) {
            sb.append("import ").append(info.packageName()).append('.').append(info.serviceClass()).append(";\n");
            sb.append("import ").append(info.packageName()).append('.').append(info.factoryClass()).append(";\n");
        }
        sb.append("\n");
        sb.append("import static org.junit.jupiter.api.Assertions.assertTrue;\n");
        sb.append("\n");

        // Class declaration
        sb.append("@CamelSpringBootTest\n");
        sb.append("@SpringBootTest\n");
        sb.append("class RouteTest {\n\n");

        // Injected fields
        sb.append("    @Autowired\n");
        sb.append("    private CamelContext context;\n\n");
        sb.append("    @Autowired\n");
        sb.append("    private ProducerTemplate template;\n\n");

        // @RegisterExtension fields
        for (TestInfraData.TestInfraInfo info : infraServices) {
            String fieldName = Character.toLowerCase(info.serviceClass().charAt(0)) + info.serviceClass().substring(1);
            sb.append("    @RegisterExtension\n");
            sb.append("    static ").append(info.serviceClass()).append(' ').append(fieldName);
            sb.append(" = ").append(info.factoryClass()).append(".createService();\n\n");
        }

        // Test method
        sb.append("    @Test\n");
        sb.append("    void testRoute() throws Exception {\n");

        // Mock endpoint declarations
        for (String mockUri : mockEndpoints) {
            String mockName = toMockName(mockUri);
            String varName = toMockVariableName(mockName);
            sb.append("        // TODO: Use AdviceWith or @MockEndpointsAndSkip to intercept endpoints\n");
            sb.append("        MockEndpoint ").append(varName);
            sb.append(" = context.getEndpoint(\"").append(escapeJava(mockName));
            sb.append("\", MockEndpoint.class);\n");
            sb.append("        ").append(varName).append(".expectedMinimumMessageCount(1);\n\n");
        }

        // Send or wait
        String fromUri = fromEndpoints.isEmpty() ? "direct:start" : fromEndpoints.get(0);
        String fromScheme = extractScheme(fromUri);
        boolean isSendable = fromScheme != null && testInfraData.getSendableSchemes().contains(fromScheme);
        boolean isTimer = "timer".equals(fromScheme);

        if (isTimer) {
            sb.append("        // Timer route fires automatically; use NotifyBuilder to wait\n");
            sb.append("        NotifyBuilder notify = new NotifyBuilder(context).whenDone(1).create();\n");
            sb.append(
                    "        assertTrue(notify.matches(10, TimeUnit.SECONDS), \"Route should complete within timeout\");\n\n");
        } else if (isSendable) {
            sb.append("        template.sendBody(\"").append(escapeJava(fromUri)).append("\", \"test message\");\n\n");
        } else {
            sb.append("        // TODO: Send a test message to trigger the route\n");
            sb.append("        // template.sendBody(\"direct:start\", \"test message\");\n\n");
        }

        // Assert
        if (!mockEndpoints.isEmpty()) {
            sb.append("        MockEndpoint.assertIsSatisfied(context);\n");
        }

        sb.append("    }\n");
        sb.append("}\n");

        return sb.toString();
    }

    // ---- Helpers ----

    private boolean usesNotifyBuilder(List<String> fromEndpoints) {
        if (fromEndpoints.isEmpty()) {
            return false;
        }
        String scheme = extractScheme(fromEndpoints.get(0));
        return "timer".equals(scheme);
    }

    private List<String> collectSchemes(List<String> fromEndpoints, List<String> toEndpoints) {
        List<String> schemes = new ArrayList<>();
        for (String uri : fromEndpoints) {
            String s = extractScheme(uri);
            if (s != null && !schemes.contains(s)) {
                schemes.add(s);
            }
        }
        for (String uri : toEndpoints) {
            String s = extractScheme(uri);
            if (s != null && !schemes.contains(s)) {
                schemes.add(s);
            }
        }
        return schemes;
    }

    private String resolveFormat(String format) {
        return format != null && !format.isBlank() ? format.toLowerCase() : "yaml";
    }

    private String resolveRuntime(String runtime) {
        return runtime != null && !runtime.isBlank() ? runtime.toLowerCase() : "main";
    }

    private String escapeJava(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ---- JSON result builder ----

    private String buildResult(
            CamelCatalog catalog, String testCode, String format, String runtime,
            List<String> allSchemes, List<String> fromEndpoints, List<String> toEndpoints,
            List<String> mockEndpoints, List<TestInfraData.TestInfraInfo> infraServices) {

        JsonObject result = new JsonObject();
        result.put("testCode", testCode);
        result.put("runtime", runtime);
        result.put("format", format);

        // Detected components
        JsonArray componentsJson = new JsonArray();
        for (String scheme : allSchemes) {
            JsonObject comp = new JsonObject();
            comp.put("scheme", scheme);
            ComponentModel model = catalog.componentModel(scheme);
            if (model != null) {
                comp.put("title", model.getTitle());
                comp.put("producerOnly", model.isProducerOnly());
                comp.put("consumerOnly", model.isConsumerOnly());
            }
            componentsJson.add(comp);
        }
        result.put("detectedComponents", componentsJson);

        // Mock endpoints
        JsonArray mocksJson = new JsonArray();
        for (String uri : mockEndpoints) {
            JsonObject mockObj = new JsonObject();
            mockObj.put("originalUri", uri);
            mockObj.put("mockUri", toMockName(uri));
            mocksJson.add(mockObj);
        }
        result.put("mockEndpoints", mocksJson);

        // Test-infra services
        JsonArray infraJson = new JsonArray();
        for (TestInfraData.TestInfraInfo info : infraServices) {
            JsonObject infraObj = new JsonObject();
            infraObj.put("service", info.serviceClass());
            infraObj.put("factory", info.factoryClass());
            infraObj.put("artifactId", info.artifactId());
            infraObj.put("package", info.packageName());
            infraJson.add(infraObj);
        }
        result.put("testInfraServices", infraJson);

        // Maven dependencies
        JsonArray depsJson = new JsonArray();
        addDependency(depsJson, "camel-test-junit5");
        if (!mockEndpoints.isEmpty()) {
            addDependency(depsJson, "camel-mock");
        }
        for (TestInfraData.TestInfraInfo info : infraServices) {
            addDependency(depsJson, info.artifactId());
        }
        if ("spring-boot".equals(runtime)) {
            addDependency(depsJson, "camel-test-spring-junit5");
        }
        result.put("mavenDependencies", depsJson);

        // Summary
        JsonObject summary = new JsonObject();
        summary.put("componentCount", allSchemes.size());
        summary.put("mockEndpointCount", mockEndpoints.size());
        summary.put("testInfraServiceCount", infraServices.size());
        summary.put("fromEndpointCount", fromEndpoints.size());
        summary.put("toEndpointCount", toEndpoints.size());
        result.put("summary", summary);

        return result.toJson();
    }

    private void addDependency(JsonArray array, String artifactId) {
        JsonObject dep = new JsonObject();
        dep.put("groupId", "org.apache.camel");
        dep.put("artifactId", artifactId);
        dep.put("scope", "test");
        array.add(dep);
    }
}
