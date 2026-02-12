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

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import com.networknt.schema.ValidationMessage;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.dsl.yaml.validator.YamlValidator;

/**
 * MCP Tools for validating and transforming Camel routes using Quarkus MCP Server.
 */
@ApplicationScoped
public class TransformTools {

    private final CamelCatalog catalog;
    private YamlValidator yamlValidator;

    public TransformTools() {
        this.catalog = new DefaultCamelCatalog(true);
    }

    /**
     * Tool to validate a Camel route or endpoint URI.
     */
    @Tool(description = "Validate a Camel endpoint URI or route definition. " +
                        "Checks syntax, required options, and valid parameter names.")
    public ValidationResult camel_validate_route(
            @ToolArg(description = "Camel endpoint URI to validate (e.g., 'kafka:myTopic?brokers=localhost:9092')") String uri,
            @ToolArg(description = "YAML route definition to validate") String route) {

        if (uri == null && route == null) {
            throw new ToolCallException("Either 'uri' or 'route' is required", null);
        }

        ValidationResult result = new ValidationResult();

        if (uri != null) {
            result.uri = uri;
            EndpointValidationResult validation = catalog.validateEndpointProperties(uri);
            result.valid = validation.isSuccess();

            if (!validation.isSuccess()) {
                ValidationErrors errors = new ValidationErrors();
                if (validation.getUnknown() != null && !validation.getUnknown().isEmpty()) {
                    errors.unknownOptions = String.join(", ", validation.getUnknown());
                }
                if (validation.getRequired() != null && !validation.getRequired().isEmpty()) {
                    errors.missingRequired = String.join(", ", validation.getRequired());
                }
                if (validation.getInvalidEnum() != null && !validation.getInvalidEnum().isEmpty()) {
                    errors.invalidEnumValues = validation.getInvalidEnum().toString();
                }
                if (validation.getInvalidInteger() != null && !validation.getInvalidInteger().isEmpty()) {
                    errors.invalidIntegers = validation.getInvalidInteger().toString();
                }
                if (validation.getInvalidBoolean() != null && !validation.getInvalidBoolean().isEmpty()) {
                    errors.invalidBooleans = validation.getInvalidBoolean().toString();
                }
                if (validation.getSyntaxError() != null) {
                    errors.syntaxError = validation.getSyntaxError();
                }
                result.errors = errors;

                if (validation.getUnknown() != null && validation.getUnknownSuggestions() != null) {
                    Map<String, String> suggestions = new HashMap<>();
                    for (String unknown : validation.getUnknown()) {
                        String[] suggestionArr = validation.getUnknownSuggestions().get(unknown);
                        if (suggestionArr != null && suggestionArr.length > 0) {
                            suggestions.put(unknown, String.join(", ", suggestionArr));
                        }
                    }
                    if (!suggestions.isEmpty()) {
                        result.suggestions = suggestions;
                    }
                }
            }
        }

        if (route != null) {
            result.routeProvided = true;
            result.note = "Full route validation requires loading the route into a CamelContext. " +
                          "Use 'camel run --validate' for complete validation.";

            List<String> uris = extractUrisFromRoute(route);
            if (!uris.isEmpty()) {
                Map<String, Boolean> uriValidations = new HashMap<>();
                boolean allValid = true;
                for (String extractedUri : uris) {
                    EndpointValidationResult validation = catalog.validateEndpointProperties(extractedUri);
                    uriValidations.put(extractedUri, validation.isSuccess());
                    if (!validation.isSuccess()) {
                        allValid = false;
                    }
                }
                result.uriValidations = uriValidations;
                result.valid = allValid;
            } else {
                result.valid = true;
            }
        }

        return result;
    }

    /**
     * Tool to transform routes between DSL formats.
     */
    @Tool(description = "Transform a Camel route between different DSL formats (YAML, XML). " +
                        "Note: Java to YAML/XML transformation has limitations.")
    public TransformResult camel_transform_route(
            @ToolArg(description = "Route definition to transform") String route,
            @ToolArg(description = "Source format (yaml, xml, java)") String fromFormat,
            @ToolArg(description = "Target format (yaml, xml)") String toFormat) {

        if (route == null || fromFormat == null || toFormat == null) {
            throw new ToolCallException("route, fromFormat, and toFormat are required", null);
        }

        TransformResult result = new TransformResult();
        result.fromFormat = fromFormat;
        result.toFormat = toFormat;
        result.note = "Route transformation requires the full Camel route parser. " +
                      "Use 'camel transform route' CLI command for complete transformation.";
        result.supported = true;
        result.recommendation = "Use 'camel transform route --format=" + toFormat + " <file>' for DSL transformation";

        return result;
    }

    /**
     * Tool to validate a YAML DSL route definition against the Camel YAML DSL JSON schema.
     */
    @Tool(description = "Validate a YAML DSL route definition against the Camel YAML DSL JSON schema. "
                        + "Checks for valid DSL elements, correct route structure, and returns detailed schema validation errors.")
    public YamlDslValidationResult camel_validate_yaml_dsl(
            @ToolArg(description = "YAML DSL route definition to validate") String route) {

        if (route == null || route.isBlank()) {
            throw new ToolCallException("'route' parameter is required", null);
        }

        try {
            if (yamlValidator == null) {
                yamlValidator = new YamlValidator();
                yamlValidator.init();
            }

            File tempFile = File.createTempFile("camel-validate-", ".yaml");
            try {
                Files.writeString(tempFile.toPath(), route);
                List<ValidationMessage> messages = yamlValidator.validate(tempFile);

                List<YamlDslError> errors = null;
                if (!messages.isEmpty()) {
                    errors = messages.stream()
                            .map(m -> new YamlDslError(
                                    m.getMessage(),
                                    m.getInstanceLocation() != null ? m.getInstanceLocation().toString() : null,
                                    m.getType(),
                                    m.getSchemaLocation() != null ? m.getSchemaLocation().toString() : null))
                            .toList();
                }

                return new YamlDslValidationResult(messages.isEmpty(), messages.size(), errors);
            } finally {
                tempFile.delete();
            }
        } catch (Exception e) {
            throw new ToolCallException("Failed to validate YAML DSL: " + e.getMessage(), e);
        }
    }

    /**
     * Extract endpoint URIs from a YAML route definition.
     */
    private List<String> extractUrisFromRoute(String route) {
        List<String> uris = new ArrayList<>();

        String[] lines = route.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.contains(":") && !line.startsWith("#")) {
                int colonPos = line.indexOf(":");
                if (colonPos > 0 && colonPos < line.length() - 1) {
                    String key = line.substring(0, colonPos).trim();
                    String value = line.substring(colonPos + 1).trim();

                    if (value.startsWith("\"") && value.endsWith("\"")) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'")) {
                        value = value.substring(1, value.length() - 1);
                    }

                    if ((key.equals("uri") || key.equals("from") || key.equals("to"))
                            && value.contains(":") && !value.startsWith("$")) {
                        String scheme = value.split(":")[0];
                        if (catalog.findComponentNames().contains(scheme)) {
                            uris.add(value);
                        }
                    }
                }
            }
        }

        return uris;
    }

    // Result classes for Jackson serialization

    public static class ValidationResult {
        public String uri;
        public boolean valid;
        public boolean routeProvided;
        public String note;
        public ValidationErrors errors;
        public Map<String, String> suggestions;
        public Map<String, Boolean> uriValidations;
    }

    public static class ValidationErrors {
        public String unknownOptions;
        public String missingRequired;
        public String invalidEnumValues;
        public String invalidIntegers;
        public String invalidBooleans;
        public String syntaxError;
    }

    public static class TransformResult {
        public String fromFormat;
        public String toFormat;
        public String note;
        public boolean supported;
        public String recommendation;
    }

    public record YamlDslValidationResult(boolean valid, int numberOfErrors, List<YamlDslError> errors) {
    }

    public record YamlDslError(String error, String instancePath, String type, String schemaPath) {
    }
}
