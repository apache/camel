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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

/**
 * MCP Tools for contract-first OpenAPI support in Apache Camel.
 *
 * Since Camel 4.6, the recommended approach is contract-first: referencing the OpenAPI spec directly at runtime via
 * rest:openApi. These tools help validate, scaffold, and provide mock guidance for that workflow.
 */
@ApplicationScoped
public class OpenApiTools {

    private static final Set<String> VALID_MISSING_OPERATION_MODES = Set.of("fail", "ignore", "mock");

    @Tool(description = "Validate an OpenAPI specification for use with Camel's contract-first REST support. "
                        + "Checks for compatibility issues like missing operationIds, unsupported security schemes, "
                        + "and OpenAPI 3.1 features that Camel does not fully support.")
    public ValidateResult camel_openapi_validate(
            @ToolArg(description = "OpenAPI 3.x specification content (JSON or YAML string)") String spec) {

        OpenAPI openAPI = parseSpec(spec);

        List<DiagnosticMessage> errors = new ArrayList<>();
        List<DiagnosticMessage> warnings = new ArrayList<>();
        List<DiagnosticMessage> info = new ArrayList<>();

        // Check OpenAPI version for 3.1 limitations
        if (openAPI.getOpenapi() != null && openAPI.getOpenapi().startsWith("3.1")) {
            warnings.add(new DiagnosticMessage(
                    "OPENAPI_31",
                    "OpenAPI 3.1 detected. Camel supports 3.0.x fully; 3.1 features like "
                                  + "webhooks and advanced JSON Schema may not be supported.",
                    null));
        }

        // Check for paths
        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            errors.add(new DiagnosticMessage(
                    "NO_PATHS",
                    "No paths defined in the specification. Camel REST requires at least one path with operations.",
                    null));
        } else {
            // Check each operation
            for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                String path = pathEntry.getKey();
                PathItem pathItem = pathEntry.getValue();

                if (pathItem.readOperationsMap() == null || pathItem.readOperationsMap().isEmpty()) {
                    warnings.add(new DiagnosticMessage(
                            "EMPTY_PATH_ITEM",
                            "Path '" + path + "' has no operations defined.",
                            path));
                    continue;
                }

                for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                    Operation op = opEntry.getValue();
                    String method = opEntry.getKey().name();
                    if (op.getOperationId() == null || op.getOperationId().isBlank()) {
                        String generated = "GENOPID_" + method + path.replace("/", "_").replace("{", "").replace("}", "");
                        warnings.add(new DiagnosticMessage(
                                "MISSING_OPERATION_ID",
                                "Operation " + method + " " + path + " has no operationId. "
                                                        + "Camel will auto-generate: " + generated,
                                path));
                    }
                }
            }
        }

        // Check webhooks
        if (openAPI.getWebhooks() != null && !openAPI.getWebhooks().isEmpty()) {
            warnings.add(new DiagnosticMessage(
                    "WEBHOOKS_PRESENT",
                    "Webhooks are defined in the spec but are not supported by Camel's REST OpenAPI integration.",
                    null));
        }

        // Check security schemes
        if (openAPI.getComponents() != null && openAPI.getComponents().getSecuritySchemes() != null) {
            for (Map.Entry<String, SecurityScheme> schemeEntry : openAPI.getComponents().getSecuritySchemes().entrySet()) {
                String name = schemeEntry.getKey();
                SecurityScheme scheme = schemeEntry.getValue();
                checkSecurityScheme(name, scheme, warnings, info);
            }
        }

        int operationCount = countOperations(openAPI);
        boolean valid = errors.isEmpty();

        return new ValidateResult(valid, errors, warnings, info, operationCount);
    }

    @Tool(description = "Generate Camel YAML scaffold for contract-first OpenAPI integration. "
                        + "Produces a rest:openApi configuration block and route stubs for each operation "
                        + "defined in the spec. This is the recommended approach since Camel 4.6.")
    public ScaffoldResult camel_openapi_scaffold(
            @ToolArg(description = "OpenAPI 3.x specification content (JSON or YAML string)") String spec,
            @ToolArg(description = "Filename of the OpenAPI spec file as it will be referenced at runtime "
                                   + "(default: 'openapi.json')") String specFilename,
            @ToolArg(description = "Behavior when a route is missing for an operationId: "
                                   + "'fail' (default, throw error), 'ignore' (skip silently), "
                                   + "or 'mock' (return mock responses)") String missingOperation) {

        OpenAPI openAPI = parseSpec(spec);

        String filename = (specFilename == null || specFilename.isBlank()) ? "openapi.json" : specFilename.strip();
        String mode
                = (missingOperation == null || missingOperation.isBlank()) ? "fail" : missingOperation.strip().toLowerCase();

        if (!VALID_MISSING_OPERATION_MODES.contains(mode)) {
            throw new ToolCallException(
                    "'missingOperation' must be 'fail', 'ignore', or 'mock', got: " + missingOperation, null);
        }

        String apiTitle = openAPI.getInfo() != null ? openAPI.getInfo().getTitle() : null;
        List<OperationStub> stubs = collectOperationStubs(openAPI);

        StringBuilder yaml = new StringBuilder();

        // rest:openApi block
        yaml.append("- rest:\n");
        yaml.append("    openApi:\n");
        yaml.append("      specification: ").append(filename).append("\n");
        if (!"fail".equals(mode)) {
            yaml.append("      missingOperation: ").append(mode).append("\n");
        }

        // Route stubs
        for (OperationStub stub : stubs) {
            yaml.append("- route:\n");
            yaml.append("    id: ").append(stub.operationId()).append("\n");
            yaml.append("    from:\n");
            yaml.append("      uri: direct:").append(stub.operationId()).append("\n");
            yaml.append("      steps:\n");

            // Set Content-Type header if we know it
            if (stub.contentType() != null) {
                yaml.append("        - setHeader:\n");
                yaml.append("            name: Content-Type\n");
                yaml.append("            constant: ").append(stub.contentType()).append("\n");
            }

            // Set response code if we know it
            if (stub.responseCode() != null) {
                yaml.append("        - setHeader:\n");
                yaml.append("            name: CamelHttpResponseCode\n");
                yaml.append("            constant: ").append(stub.responseCode()).append("\n");
            }

            yaml.append("        - setBody:\n");
            yaml.append("            constant: \"TODO: implement ").append(stub.operationId()).append("\"\n");
        }

        return new ScaffoldResult(yaml.toString(), stubs.size(), filename, mode, apiTitle);
    }

    @Tool(description = "Get guidance on configuring Camel's contract-first REST missingOperation modes "
                        + "(fail, ignore, mock). For 'mock' mode, provides directory structure, mock file paths, "
                        + "and example content derived from the OpenAPI spec.")
    public MockGuidanceResult camel_openapi_mock_guidance(
            @ToolArg(description = "OpenAPI 3.x specification content (JSON or YAML string)") String spec,
            @ToolArg(description = "The missingOperation mode to get guidance for: "
                                   + "'mock' (default), 'fail', or 'ignore'") String mode) {

        OpenAPI openAPI = parseSpec(spec);

        String effectiveMode = (mode == null || mode.isBlank()) ? "mock" : mode.strip().toLowerCase();
        if (!VALID_MISSING_OPERATION_MODES.contains(effectiveMode)) {
            throw new ToolCallException(
                    "'mode' must be 'fail', 'ignore', or 'mock', got: " + mode, null);
        }

        String modeExplanation = getModeExplanation(effectiveMode);

        // Configuration YAML snippet
        StringBuilder configYaml = new StringBuilder();
        configYaml.append("- rest:\n");
        configYaml.append("    openApi:\n");
        configYaml.append("      specification: openapi.json\n");
        configYaml.append("      missingOperation: ").append(effectiveMode).append("\n");

        List<MockFileInfo> mockFiles = new ArrayList<>();
        String directoryStructure = null;

        if ("mock".equals(effectiveMode)) {
            // Build mock file info from spec
            Set<String> directories = new TreeSet<>();
            directories.add("camel-mock/");

            if (openAPI.getPaths() != null) {
                for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
                    String path = pathEntry.getKey();
                    PathItem pathItem = pathEntry.getValue();

                    if (pathItem.readOperationsMap() == null) {
                        continue;
                    }

                    for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                        String method = opEntry.getKey().name();
                        Operation op = opEntry.getValue();

                        // Determine response content type and example
                        String contentType = null;
                        String exampleContent = null;
                        String responseCode = null;

                        if (op.getResponses() != null) {
                            Map.Entry<String, ApiResponse> successResponse = findFirstSuccessResponse(op.getResponses());
                            if (successResponse != null) {
                                responseCode = successResponse.getKey();
                                ApiResponse resp = successResponse.getValue();
                                if (resp.getContent() != null && !resp.getContent().isEmpty()) {
                                    Map.Entry<String, MediaType> firstContent
                                            = resp.getContent().entrySet().iterator().next();
                                    contentType = firstContent.getKey();
                                    MediaType mediaType = firstContent.getValue();
                                    if (mediaType.getExample() != null) {
                                        exampleContent = mediaType.getExample().toString();
                                    }
                                }
                            }
                        }

                        // Determine file extension from content type
                        String ext = getFileExtension(contentType);

                        // Build mock file path: camel-mock/<path>.<ext>
                        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
                        // Replace path parameters with placeholder directory names
                        cleanPath = cleanPath.replace("{", "_").replace("}", "_");
                        String filePath = "camel-mock/" + cleanPath + "." + method.toLowerCase() + ext;

                        // Track parent directories
                        String[] parts = filePath.split("/");
                        StringBuilder dirBuilder = new StringBuilder();
                        for (int i = 0; i < parts.length - 1; i++) {
                            dirBuilder.append(parts[i]).append("/");
                            directories.add(dirBuilder.toString());
                        }

                        String operationId = op.getOperationId() != null ? op.getOperationId() : method + " " + path;

                        String note = null;
                        if ("GET".equals(method) && exampleContent == null) {
                            note = "GET without a mock file returns HTTP 204 (No Content)";
                        } else if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
                            if (exampleContent == null) {
                                note = method + " without a mock file echoes the request body back";
                            }
                        }

                        mockFiles.add(new MockFileInfo(filePath, operationId, contentType, exampleContent, note));
                    }
                }
            }

            // Build directory structure string
            StringBuilder dirStructure = new StringBuilder();
            for (String dir : directories) {
                int depth = dir.split("/").length - 1;
                dirStructure.append("  ".repeat(depth)).append(dir.substring(dir.lastIndexOf('/') == dir.length() - 1
                        ? dir.substring(0, dir.length() - 1).lastIndexOf('/') + 1
                        : dir.lastIndexOf('/') + 1));
                dirStructure.append("\n");
            }
            directoryStructure = dirStructure.toString().stripTrailing();
        }

        return new MockGuidanceResult(
                effectiveMode, modeExplanation, configYaml.toString(),
                directoryStructure, mockFiles.isEmpty() ? null : mockFiles);
    }

    // -- Shared helpers --

    OpenAPI parseSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new ToolCallException("'spec' parameter is required and must not be blank", null);
        }

        SwaggerParseResult parseResult = new OpenAPIV3Parser().readContents(spec);
        OpenAPI openAPI = parseResult.getOpenAPI();
        if (openAPI == null) {
            String errors = parseResult.getMessages() != null
                    ? String.join("; ", parseResult.getMessages())
                    : "Unknown parse error";
            throw new ToolCallException("Failed to parse OpenAPI spec: " + errors, null);
        }
        return openAPI;
    }

    private int countOperations(OpenAPI openAPI) {
        if (openAPI.getPaths() == null) {
            return 0;
        }
        int count = 0;
        for (PathItem item : openAPI.getPaths().values()) {
            if (item.readOperationsMap() != null) {
                count += item.readOperationsMap().size();
            }
        }
        return count;
    }

    private List<OperationStub> collectOperationStubs(OpenAPI openAPI) {
        List<OperationStub> stubs = new ArrayList<>();
        if (openAPI.getPaths() == null) {
            return stubs;
        }

        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem pathItem = pathEntry.getValue();

            if (pathItem.readOperationsMap() == null) {
                continue;
            }

            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : pathItem.readOperationsMap().entrySet()) {
                String method = opEntry.getKey().name().toLowerCase();
                Operation op = opEntry.getValue();

                String operationId = op.getOperationId();
                if (operationId == null || operationId.isBlank()) {
                    operationId = "GENOPID_" + method.toUpperCase()
                                  + path.replace("/", "_").replace("{", "").replace("}", "");
                }

                String responseCode = null;
                String contentType = null;
                String consumesType = null;

                // Find response info
                if (op.getResponses() != null) {
                    Map.Entry<String, ApiResponse> successResponse = findFirstSuccessResponse(op.getResponses());
                    if (successResponse != null) {
                        responseCode = successResponse.getKey();
                        ApiResponse resp = successResponse.getValue();
                        if (resp.getContent() != null && !resp.getContent().isEmpty()) {
                            contentType = resp.getContent().keySet().iterator().next();
                        }
                    }
                }

                // Find request body content type
                if (op.getRequestBody() != null && op.getRequestBody().getContent() != null
                        && !op.getRequestBody().getContent().isEmpty()) {
                    consumesType = op.getRequestBody().getContent().keySet().iterator().next();
                }

                String summary = op.getSummary();

                stubs.add(new OperationStub(operationId, method, path, responseCode, contentType, consumesType, summary));
            }
        }
        return stubs;
    }

    private Map.Entry<String, ApiResponse> findFirstSuccessResponse(ApiResponses responses) {
        // Try 2xx codes in order
        for (Map.Entry<String, ApiResponse> entry : responses.entrySet()) {
            String code = entry.getKey();
            if (code.startsWith("2")) {
                return entry;
            }
        }
        // Fall back to "default"
        if (responses.getDefault() != null) {
            return Map.entry("200", responses.getDefault());
        }
        return null;
    }

    private void checkSecurityScheme(
            String name, SecurityScheme scheme,
            List<DiagnosticMessage> warnings, List<DiagnosticMessage> info) {

        if (scheme.getType() == null) {
            return;
        }

        switch (scheme.getType()) {
            case APIKEY:
                if (scheme.getIn() == SecurityScheme.In.QUERY) {
                    info.add(new DiagnosticMessage(
                            "SECURITY_APIKEY_QUERY",
                            "Security scheme '" + name + "' (apiKey in query) is supported by Camel.",
                            null));
                } else {
                    warnings.add(new DiagnosticMessage(
                            "SECURITY_APIKEY_NOT_QUERY",
                            "Security scheme '" + name + "' (apiKey in " + scheme.getIn()
                                                         + ") is defined but not enforced by Camel's REST OpenAPI integration. "
                                                         + "You must handle authentication in your route logic.",
                            null));
                }
                break;
            case HTTP:
                warnings.add(new DiagnosticMessage(
                        "SECURITY_HTTP",
                        "Security scheme '" + name + "' (HTTP " + scheme.getScheme()
                                         + ") is defined but not enforced by Camel's REST OpenAPI integration. "
                                         + "You must handle authentication in your route logic.",
                        null));
                break;
            case OAUTH2:
                warnings.add(new DiagnosticMessage(
                        "SECURITY_OAUTH2",
                        "Security scheme '" + name
                                           + "' (OAuth2) is defined but not enforced by Camel's REST OpenAPI integration. "
                                           + "You must handle authentication in your route logic.",
                        null));
                break;
            case OPENIDCONNECT:
                warnings.add(new DiagnosticMessage(
                        "SECURITY_OPENIDCONNECT",
                        "Security scheme '" + name
                                                  + "' (OpenID Connect) is defined but not enforced by Camel's REST OpenAPI integration. "
                                                  + "You must handle authentication in your route logic.",
                        null));
                break;
            case MUTUALTLS:
                warnings.add(new DiagnosticMessage(
                        "SECURITY_MUTUALTLS",
                        "Security scheme '" + name
                                              + "' (Mutual TLS) is defined but not enforced by Camel's REST OpenAPI integration. "
                                              + "You must handle authentication in your route logic.",
                        null));
                break;
            default:
                break;
        }
    }

    private String getModeExplanation(String mode) {
        return switch (mode) {
            case "fail" -> "In 'fail' mode (the default), Camel throws an exception at startup if any operationId "
                           + "in the OpenAPI spec does not have a corresponding direct:<operationId> route. "
                           + "This ensures all API operations are explicitly implemented.";
            case "ignore" -> "In 'ignore' mode, Camel silently skips operations that do not have a corresponding "
                             + "direct:<operationId> route. Requests to those endpoints return HTTP 404. "
                             + "Useful during incremental development.";
            case "mock" -> "In 'mock' mode, Camel auto-generates mock responses for operations without a "
                           + "direct:<operationId> route. For GET requests, it looks for mock data files under "
                           + "camel-mock/ directory. For POST/PUT/DELETE, it echoes the request body. "
                           + "GET without a mock file returns HTTP 204. Useful for prototyping and testing.";
            default -> "";
        };
    }

    private String getFileExtension(String contentType) {
        if (contentType == null) {
            return ".json";
        }
        if (contentType.contains("json")) {
            return ".json";
        }
        if (contentType.contains("xml")) {
            return ".xml";
        }
        if (contentType.contains("text")) {
            return ".txt";
        }
        return ".json";
    }

    // -- Result records --

    public record DiagnosticMessage(String code, String message, String path) {
    }

    public record ValidateResult(
            boolean valid,
            List<DiagnosticMessage> errors,
            List<DiagnosticMessage> warnings,
            List<DiagnosticMessage> info,
            int operationCount) {
    }

    public record OperationStub(
            String operationId,
            String method,
            String path,
            String responseCode,
            String contentType,
            String consumesType,
            String summary) {
    }

    public record ScaffoldResult(
            String yaml,
            int operationCount,
            String specFilename,
            String missingOperation,
            String apiTitle) {
    }

    public record MockFileInfo(
            String filePath,
            String operation,
            String contentType,
            String exampleContent,
            String note) {
    }

    public record MockGuidanceResult(
            String mode,
            String modeExplanation,
            String configurationYaml,
            String directoryStructure,
            List<MockFileInfo> mockFiles) {
    }
}
