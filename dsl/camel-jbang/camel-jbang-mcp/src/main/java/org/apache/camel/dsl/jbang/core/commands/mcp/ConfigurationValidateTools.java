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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;

/**
 * MCP Tool for validating Camel configuration properties (e.g. {@code application.properties} for camel-main, Spring
 * Boot or Quarkus). Wraps {@link CamelCatalog#validateConfigurationProperty(String)} so that misspelled option names,
 * invalid values and suggested corrections can be surfaced to LLMs.
 */
@ApplicationScoped
public class ConfigurationValidateTools {

    @Inject
    CatalogService catalogService;

    /**
     * Tool to validate one or more Camel configuration property lines.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Validate one or more Camel configuration property lines (e.g. lines from application.properties). "
                        +
                        "Detects misspelled option names, invalid values (boolean/integer/number/duration/enum) and "
                        +
                        "returns suggestions when available. The input can be a single line or a multi-line string with "
                        +
                        "one property per line; blank lines and comments (starting with # or !) are skipped.")
    public ConfigurationValidateResult camel_configuration_validate(
            @ToolArg(description = "Configuration property lines to validate. Can be a single line "
                                   + "(e.g. \"camel.main.streamCaching=true\") or multiple lines separated by newlines.") String properties,
            @ToolArg(description = "Runtime type: main, spring-boot, or quarkus (default: main)") String runtime,
            @ToolArg(description = "Camel version to query. If not specified, uses the default catalog version.") String camelVersion,
            @ToolArg(description = "Platform BOM coordinates in GAV format (groupId:artifactId:version). "
                                   + "When provided, overrides camelVersion.") String platformBom) {

        if (properties == null || properties.isBlank()) {
            throw new ToolCallException("properties argument is required", null);
        }

        try {
            CamelCatalog catalog = catalogService.loadCatalog(runtime, camelVersion, platformBom);

            List<PropertyValidationInfo> results = new ArrayList<>();
            int totalErrors = 0;
            int totalWarnings = 0;
            int validCount = 0;

            String[] lines = properties.split("\\r?\\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
                    continue;
                }

                ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(line);
                PropertyValidationInfo info = toPropertyValidationInfo(i + 1, line, result);
                results.add(info);
                totalErrors += info.errors();
                totalWarnings += info.warnings();
                if (info.valid()) {
                    validCount++;
                }
            }

            ConfigurationValidateSummary summary
                    = new ConfigurationValidateSummary(results.size(), validCount, totalErrors, totalWarnings);

            return new ConfigurationValidateResult(catalog.getCatalogVersion(), summary, results);
        } catch (ToolCallException e) {
            throw e;
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to validate configuration properties (" + e.getClass().getName() + "): " + e.getMessage(),
                    null);
        }
    }

    private PropertyValidationInfo toPropertyValidationInfo(
            int lineNumber, String text, ConfigurationPropertiesValidationResult result) {
        boolean valid = result.isSuccess();
        boolean accepted = result.isAccepted();
        int errors = result.getNumberOfErrors();
        int warnings = result.getNumberOfWarnings();

        List<PropertyIssue> issues = collectIssues(result);
        String summary = result.summaryErrorMessage(false, true, true);

        return new PropertyValidationInfo(lineNumber, text, accepted, valid, errors, warnings, summary, issues);
    }

    private List<PropertyIssue> collectIssues(ConfigurationPropertiesValidationResult result) {
        List<PropertyIssue> issues = new ArrayList<>();

        if (result.getSyntaxError() != null) {
            issues.add(new PropertyIssue(null, "syntax", result.getSyntaxError(), Collections.emptyList()));
        }
        if (result.getUnknownComponent() != null) {
            issues.add(new PropertyIssue(
                    result.getUnknownComponent(), "unknownComponent",
                    "Unknown component: " + result.getUnknownComponent(),
                    Collections.emptyList()));
        }
        if (result.getIncapable() != null) {
            issues.add(new PropertyIssue(null, "incapable", result.getIncapable(), Collections.emptyList()));
        }

        if (result.getUnknown() != null) {
            Map<String, String[]> unknownSuggestions = result.getUnknownSuggestions();
            for (String name : result.getUnknown()) {
                String[] suggestions = unknownSuggestions != null ? unknownSuggestions.get(name) : null;
                issues.add(new PropertyIssue(name, "unknown", "Unknown option", asSuggestionList(suggestions)));
            }
        }
        if (result.getRequired() != null) {
            for (String name : result.getRequired()) {
                issues.add(new PropertyIssue(name, "required", "Missing required option", Collections.emptyList()));
            }
        }
        if (result.getDeprecated() != null) {
            for (String name : result.getDeprecated()) {
                issues.add(new PropertyIssue(name, "deprecated", "Deprecated option", Collections.emptyList()));
            }
        }

        addInvalidValueIssues(issues, result.getInvalidEnum(), "invalidEnum", "Invalid enum value",
                result.getInvalidEnumChoices(), result.getInvalidEnumSuggestions());
        addInvalidValueIssues(issues, result.getInvalidReference(), "invalidReference", "Invalid reference value", null,
                null);
        addInvalidValueIssues(issues, result.getInvalidBoolean(), "invalidBoolean", "Invalid boolean value", null, null);
        addInvalidValueIssues(issues, result.getInvalidInteger(), "invalidInteger", "Invalid integer value", null, null);
        addInvalidValueIssues(issues, result.getInvalidNumber(), "invalidNumber", "Invalid number value", null, null);
        addInvalidValueIssues(issues, result.getInvalidDuration(), "invalidDuration", "Invalid duration value", null,
                null);
        addInvalidValueIssues(issues, result.getInvalidMap(), "invalidMap", "Invalid map key/value", null, null);
        addInvalidValueIssues(issues, result.getInvalidArray(), "invalidArray", "Invalid array index/value", null, null);

        return issues;
    }

    private void addInvalidValueIssues(
            List<PropertyIssue> issues, Map<String, String> values, String kind, String baseMessage,
            Map<String, String[]> choicesMap, Map<String, String[]> suggestionsMap) {
        if (values == null) {
            return;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            StringBuilder msg = new StringBuilder(baseMessage);
            if (value != null && !value.isBlank()) {
                msg.append(": ").append(value);
            }
            if (choicesMap != null) {
                String[] choices = choicesMap.get(name);
                if (choices != null && choices.length > 0) {
                    msg.append(". Possible values: ").append(Arrays.asList(choices));
                }
            }
            String[] suggestions = suggestionsMap != null ? suggestionsMap.get(name) : null;
            issues.add(new PropertyIssue(name, kind, msg.toString(), asSuggestionList(suggestions)));
        }
    }

    private static List<String> asSuggestionList(String[] suggestions) {
        if (suggestions == null || suggestions.length == 0) {
            return Collections.emptyList();
        }
        return new ArrayList<>(Arrays.asList(suggestions));
    }

    // Result record classes for Jackson serialization

    public record ConfigurationValidateResult(String camelVersion, ConfigurationValidateSummary summary,
            List<PropertyValidationInfo> properties) {
    }

    public record ConfigurationValidateSummary(int total, int valid, int errors, int warnings) {
    }

    public record PropertyValidationInfo(int lineNumber, String text, boolean accepted, boolean valid, int errors,
            int warnings, String summary, List<PropertyIssue> issues) {
    }

    public record PropertyIssue(String name, String kind, String message, List<String> suggestions) {
    }
}
