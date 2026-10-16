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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
import org.apache.camel.component.rest.openapi.RestOpenApiHelper;

/**
 * MCP Tools for contract-first OpenAPI support in Apache Camel.
 *
 * Since Camel 4.6, the recommended approach is contract-first: referencing the OpenAPI spec directly at runtime via
 * rest:openApi. These tools help validate and scaffold that workflow, following the rules of the camel-rest-openapi
 * component (DefaultRestOpenapiProcessorStrategy): an operation without operationId is routed to the id of
 * {@link RestOpenApiHelper#generateOperationId(String, PathItem.HttpMethod)}, and in mock mode a mock file is served
 * when its location without extension ends with the request path.
 */
@McpSecured
@ApplicationScoped
public class OpenApiTools {

    private static final Set<String> VALID_MISSING_OPERATION_MODES = Set.of("fail", "ignore", "mock");

    /**
     * The methods that, in mock mode and without a mock file, answer with the request body.
     */
    private static final Set<String> BODY_VERBS = Set.of("DELETE", "PUT", "POST", "PATCH");

    private static final String NOT_ENFORCED
            = " is defined, but camel-rest-openapi does not enforce security schemes: requests are not authenticated"
              + " against it. Secure the HTTP layer (for a Bearer token, the rest-openapi oauthProfile option)"
              + " or check the credential in the route.";

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Validate an OpenAPI specification for use with Camel's contract-first REST support. "
                        + "Reports operations without operationId (with the id Camel routes them to), "
                        + "security schemes (which Camel does not enforce), webhooks (which Camel does not serve) "
                        + "and specs without paths.")
    public ValidateResult camel_openapi_validate(
            @ToolArg(description = "OpenAPI 3.x specification content (JSON or YAML string)") String spec) {

        OpenAPI openAPI = parseSpec(spec);

        List<DiagnosticMessage> errors = new ArrayList<>();
        List<DiagnosticMessage> warnings = new ArrayList<>();
        List<DiagnosticMessage> info = new ArrayList<>();

        if (openAPI.getOpenapi() != null && openAPI.getOpenapi().startsWith("3.1")) {
            info.add(new DiagnosticMessage(
                    "OPENAPI_31",
                    "OpenAPI 3.1 detected. camel-rest-openapi parses 3.1 specifications and serves their paths "
                                  + "like those of a 3.0 specification; webhooks are not served.",
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
                    if (opEntry.getValue().getOperationId() == null) {
                        warnings.add(new DiagnosticMessage(
                                "MISSING_OPERATION_ID",
                                "Operation " + opEntry.getKey().name() + " " + path + " has no operationId. "
                                                        + "Camel routes it to direct:"
                                                        + RestOpenApiHelper.generateOperationId(path, opEntry.getKey()),
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
                checkSecurityScheme(schemeEntry.getKey(), schemeEntry.getValue(), warnings);
            }
        }

        int operationCount = countOperations(openAPI);
        boolean valid = errors.isEmpty();

        return new ValidateResult(valid, errors, warnings, info, operationCount);
    }

    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Generate Camel YAML scaffold for contract-first OpenAPI integration. "
                        + "Produces a rest:openApi configuration block and a direct:<operationId> route stub for "
                        + "each operation defined in the spec (with the id Camel generates when the spec has none), "
                        + "and explains the missingOperation mode. With missingOperation=mock it also lists the "
                        + "camel-mock/ files Camel serves for the operations without a route. "
                        + "This is the recommended approach since Camel 4.6.")
    public ScaffoldResult camel_openapi_scaffold(
            @ToolArg(description = "OpenAPI 3.x specification content (JSON or YAML string)") String spec,
            @ToolArg(description = "Filename of the OpenAPI spec file as it will be referenced at runtime "
                                   + "(default: 'openapi.json')",
                     required = false) String specFilename,
            @ToolArg(description = "Behavior when a route is missing for an operationId: "
                                   + "'fail' (default, throw error), 'ignore' (skip silently), "
                                   + "or 'mock' (return mock responses; the answer then lists the mock files)",
                     required = false) String missingOperation) {

        OpenAPI openAPI = parseSpec(spec);

        String filename = (specFilename == null || specFilename.isBlank()) ? "openapi.json" : specFilename.strip();
        String mode = (missingOperation == null || missingOperation.isBlank())
                ? "fail" : missingOperation.strip().toLowerCase(Locale.ROOT);

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
                yaml.append("            expression:\n");
                yaml.append("              constant:\n");
                yaml.append("                expression: ").append(stub.contentType()).append("\n");
            }

            // Set response code if we know it
            if (stub.responseCode() != null) {
                yaml.append("        - setHeader:\n");
                yaml.append("            name: CamelHttpResponseCode\n");
                yaml.append("            expression:\n");
                yaml.append("              constant:\n");
                yaml.append("                expression: ").append(stub.responseCode()).append("\n");
            }

            yaml.append("        - setBody:\n");
            yaml.append("            expression:\n");
            yaml.append("              constant:\n");
            yaml.append("                expression: \"TODO: implement ").append(stub.operationId()).append("\"\n");
        }

        List<MockFileInfo> mockFiles = "mock".equals(mode) ? collectMockFiles(openAPI) : null;

        return new ScaffoldResult(
                yaml.toString(), stubs.size(), filename, mode, apiTitle, getModeExplanation(mode), mockFiles);
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

    /**
     * The id camel-rest-openapi routes the operation to: its operationId, or the generated one.
     */
    private static String operationId(String path, PathItem.HttpMethod method, Operation op) {
        return op.getOperationId() != null ? op.getOperationId() : RestOpenApiHelper.generateOperationId(path, method);
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
                String method = opEntry.getKey().name().toLowerCase(Locale.ROOT);
                Operation op = opEntry.getValue();
                String operationId = operationId(path, opEntry.getKey(), op);

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

    /**
     * One mock file per path of the spec. For an operation without a route, camel-rest-openapi serves the first
     * resource of mockIncludePattern (default classpath:camel-mock/**) whose location without extension ends with the
     * request path, whatever the method: a path parameter is matched by its value, so {@code /pets/{petId}} takes one
     * file per value, such as {@code camel-mock/pets/1.json}.
     */
    private List<MockFileInfo> collectMockFiles(OpenAPI openAPI) {
        List<MockFileInfo> files = new ArrayList<>();
        if (openAPI.getPaths() == null) {
            return files;
        }
        for (Map.Entry<String, PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            Map<PathItem.HttpMethod, Operation> operations = pathEntry.getValue().readOperationsMap();
            if (operations == null || operations.isEmpty()) {
                continue;
            }

            List<String> operationIds = new ArrayList<>();
            Map<String, String> withoutFile = new LinkedHashMap<>();
            String responseType = null;
            String example = null;
            for (Map.Entry<PathItem.HttpMethod, Operation> opEntry : operations.entrySet()) {
                String method = opEntry.getKey().name();
                Operation op = opEntry.getValue();
                operationIds.add(operationId(path, opEntry.getKey(), op));

                if (op.getResponses() != null) {
                    Map.Entry<String, ApiResponse> success = findFirstSuccessResponse(op.getResponses());
                    if (responseType == null && success != null && success.getValue().getContent() != null
                            && !success.getValue().getContent().isEmpty()) {
                        responseType = success.getValue().getContent().keySet().iterator().next();
                    }
                }
                if (BODY_VERBS.contains(method)) {
                    withoutFile.put(method, "echoes the request body with HTTP 200");
                    continue;
                }
                ApiResponse ok = op.getResponses() != null ? op.getResponses().get("200") : null;
                String opExample = firstExample(ok);
                if (opExample != null) {
                    withoutFile.put(method, "answers the example of its '200' response with HTTP 200");
                    if (example == null) {
                        example = opExample;
                    }
                } else if (ok != null || op.getResponses() == null) {
                    withoutFile.put(method, "answers HTTP 204 with an empty body");
                } else {
                    // camel-rest-openapi reads the '200' response of the operation without checking it exists
                    withoutFile.put(method, "fails with an error, as the operation has no '200' response: add the file");
                }
            }

            String ext = responseType != null && responseType.contains("xml") ? ".xml" : ".json";
            StringBuilder note = new StringBuilder("Served with HTTP 200 for any method of ").append(path)
                    .append(" without a route.");
            if (path.contains("{")) {
                String sample = path.replaceAll("\\{[^}]+}", "1");
                note.append(" Camel matches the request path, so replace each {parameter} with a value:"
                            + " one file per value (camel-mock")
                        .append(sample).append(ext).append(" answers ").append(sample).append(").");
            }
            note.append(" Without the file, ");
            withoutFile.forEach((method, answer) -> note.append(method).append(' ').append(answer).append("; "));
            note.setLength(note.length() - 2);
            note.append('.');

            // the component matches a resource whose location without extension ends with the request path without its
            // leading slash; for the root path that is the empty string, so any mock file matches
            String fileName = "/".equals(path) ? "camel-mock/index" + ext : "camel-mock" + path + ext;
            if ("/".equals(path)) {
                note.append(" Camel matches the root path to any mock file (the first one found), so with more than"
                            + " one file the one that answers / is not predictable.");
            }
            files.add(new MockFileInfo(fileName, path, operationIds, example, note.toString()));
        }
        return files;
    }

    /**
     * The example camel-rest-openapi answers in mock mode for an operation without mock file: the example, or the first
     * of the examples, of a media type of its "200" response.
     */
    private static String firstExample(ApiResponse ok) {
        if (ok == null || ok.getContent() == null) {
            return null;
        }
        for (MediaType mt : ok.getContent().values()) {
            if (mt.getExample() != null) {
                return mt.getExample().toString();
            }
            if (mt.getExamples() != null && !mt.getExamples().isEmpty()) {
                Object value = mt.getExamples().values().iterator().next().getValue();
                if (value != null) {
                    return value.toString();
                }
            }
        }
        return null;
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

    private void checkSecurityScheme(String name, SecurityScheme scheme, List<DiagnosticMessage> warnings) {
        if (scheme.getType() == null) {
            return;
        }
        String kind = switch (scheme.getType()) {
            case APIKEY -> "apiKey in " + Objects.requireNonNullElse(scheme.getIn(), "<unset>");
            case HTTP -> "HTTP " + Objects.requireNonNullElse(scheme.getScheme(), "<unset>");
            case OAUTH2 -> "OAuth2";
            case OPENIDCONNECT -> "OpenID Connect";
            case MUTUALTLS -> "Mutual TLS";
        };
        warnings.add(new DiagnosticMessage(
                "SECURITY_" + scheme.getType().name(),
                "Security scheme '" + name + "' (" + kind + ")" + NOT_ENFORCED,
                null));
    }

    private String getModeExplanation(String mode) {
        return switch (mode) {
            case "fail" -> "In 'fail' mode, Camel fails at startup, listing the unmapped operations, if an operation "
                           + "of the OpenAPI spec has no direct:<operationId> route. 'fail' is the default, except "
                           + "under the 'dev' profile (the default of camel run), where the default is 'mock'.";
            case "ignore" -> "In 'ignore' mode, Camel logs the operations without a direct:<operationId> route at "
                             + "startup and starts anyway. A request to such an operation is not routed: Camel "
                             + "answers with the request message as is. Useful during incremental development.";
            case "mock" -> "In 'mock' mode, an operation without a direct:<operationId> route is answered with "
                           + "HTTP 200 and the content of the first file of mockIncludePattern (default "
                           + "classpath:camel-mock/**: camel-mock/ next to the routes with camel run, "
                           + "src/main/resources/camel-mock/ in a Maven project) whose location without extension "
                           + "ends with the request path, whatever the method. When the request has a JSON or XML "
                           + "Content-Type, only a .json or .xml file matches. Without a file, DELETE, PUT, POST "
                           + "and PATCH echo the request body with HTTP 200; the other methods answer the example of "
                           + "the '200' response of the spec, or HTTP 204 with an empty body.";
            default -> "";
        };
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
            String apiTitle,
            String modeExplanation,
            List<MockFileInfo> mockFiles) {
    }

    public record MockFileInfo(
            String filePath,
            String requestPath,
            List<String> operationIds,
            String exampleContent,
            String note) {
    }
}
