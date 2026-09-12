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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.networknt.schema.Error;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.ConfigurationPropertiesValidationResult;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.LanguageValidationResult;
import org.apache.camel.dsl.yaml.validator.YamlValidator;

/**
 * Validates integration source files the way the Camel TUI's editor does on save, before an AI agent writes them: Camel
 * YAML DSL against the YAML DSL schema (unknown or misspelled options, wrong structure), then the endpoint URIs and
 * simple expressions in it against the catalog; a .properties file line by line against the catalog of {@code camel.*}
 * options. Every message names the line so the agent can fix the file.
 */
public final class SourceValidator {

    private static final Set<String> PREDICATE_EIPS = Set.of(
            "filter", "when", "validate", "onWhen", "on-when",
            "handled", "continued", "retryWhile", "retry-while",
            "completionPredicate", "completion-predicate",
            "completion", "loopDoWhile", "loop-do-while");

    private static final Set<String> CONSUMER_EIPS
            = Set.of("from", "pollEnrich", "poll-enrich", "poll", "interceptFrom", "intercept-from");
    private static final Set<String> PRODUCER_EIPS
            = Set.of("to", "toD", "to-d", "wireTap", "wire-tap", "enrich",
                    "interceptSendToEndpoint", "intercept-send-to-endpoint");

    private static final Pattern YAML_URI_PATTERN = Pattern.compile(
            "^\\s*-?\\s*(?:uri|from|to|toD|wireTap|enrich|pollEnrich|deadLetterChannel):\\s*\"?([a-zA-Z][a-zA-Z0-9+.-]*(?::[^\"\\s]*)?)");

    private static volatile YamlValidator yamlValidator;

    private SourceValidator() {
    }

    /** Whether the file has a validator: YAML routes and .properties files do, other files have none. */
    public static boolean isValidatableFile(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".properties");
    }

    /**
     * Validates source by file type: Camel YAML DSL for .yaml/.yml files, Camel options for .properties files. Other
     * file types have no validation and yield no messages.
     *
     * @param  fileName          the file name; its extension picks the checks
     * @param  content           the source
     * @param  catalog           the catalog of the Camel version the source is for
     * @param  extraPropertyLine an extra check for a properties line the catalog does not know (Spring Boot
     *                           properties), returning the message or null; may be null
     * @return                   the messages, empty when the source is valid
     */
    public static List<String> validate(
            String fileName, String content, CamelCatalog catalog, Function<String, String> extraPropertyLine) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            return validateCamelYaml(content, catalog);
        }
        if (name.endsWith(".properties")) {
            return validateProperties(content, catalog, extraPropertyLine);
        }
        return List.of();
    }

    /**
     * Validates Camel YAML DSL source: the YAML DSL schema first, then endpoint URIs and simple expressions against the
     * catalog. Returns the messages, empty when the source is valid.
     */
    public static List<String> validateCamelYaml(String content, CamelCatalog catalog) {
        List<String> msgs = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return msgs;
        }
        try {
            msgs.addAll(formatSchemaErrors(yamlValidator().validate(content)));
        } catch (Exception e) {
            msgs.add("Invalid YAML: " + e.getMessage());
            return msgs;
        }
        if (catalog != null) {
            msgs.addAll(validateYamlEndpoints(content, catalog));
            msgs.addAll(validateYamlSimple(content, catalog));
        }
        return msgs;
    }

    private static YamlValidator yamlValidator() throws Exception {
        YamlValidator v = yamlValidator;
        if (v == null) {
            synchronized (SourceValidator.class) {
                v = yamlValidator;
                if (v == null) {
                    v = new YamlValidator();
                    v.init();
                    yamlValidator = v;
                }
            }
        }
        return v;
    }

    /**
     * Validates a properties file line by line: {@code camel.*} keys against the catalog, the rest with the extra
     * check.
     */
    public static List<String> validateProperties(
            String content, CamelCatalog catalog, Function<String, String> extraPropertyLine) {
        return validatePropertiesLines(content, line -> validatePropertyLine(line, catalog, extraPropertyLine));
    }

    /** Validates one properties line: a {@code camel.*} key against the catalog, any other with the extra check. */
    public static String validatePropertyLine(String line, CamelCatalog catalog, Function<String, String> extra) {
        if (catalog != null) {
            try {
                ConfigurationPropertiesValidationResult result = catalog.validateConfigurationProperty(line);
                if (result.isAccepted()) {
                    if (!result.isSuccess()) {
                        String msg = result.summaryErrorMessage(false);
                        if (msg != null) {
                            return msg.trim();
                        }
                    }
                    return null;
                }
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        return extra != null ? extra.apply(line) : null;
    }

    /** Runs a line validator over the key=value lines of a properties file, prefixing each message with its line. */
    public static List<String> validatePropertiesLines(String content, Function<String, String> lineValidator) {
        List<String> msgs = new ArrayList<>();
        if (content == null) {
            return msgs;
        }
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!") || !line.contains("=")) {
                continue;
            }
            String error = lineValidator.apply(lines[i]);
            if (error != null) {
                msgs.add("Line " + (i + 1) + ": " + error);
            }
        }
        return msgs;
    }

    /** The YAML DSL schema errors in words: the node they are about and the message without parser noise. */
    public static List<String> formatSchemaErrors(List<Error> errors) {
        List<String> msgs = new ArrayList<>();
        if (errors == null) {
            return msgs;
        }
        for (Error error : errors) {
            String msg = error.getMessage();
            if (msg == null) {
                continue;
            }
            String loc = error.getInstanceLocation() != null ? error.getInstanceLocation().toString() : null;
            String node = extractNodeName(loc);
            String clean = cleanValidationMessage(msg);
            msgs.add(node != null ? node + ": " + clean : clean);
        }
        return msgs;
    }

    public static String cleanValidationMessage(String msg) {
        // strip FQCN prefix like "com.fasterxml...MarkedYAMLException: "
        int colonSpace = msg.indexOf(": ");
        if (colonSpace > 0) {
            String prefix = msg.substring(0, colonSpace);
            if (prefix.contains(".") && !prefix.contains(" ")) {
                msg = msg.substring(colonSpace + 2);
            }
        }
        // strip "at [Source: (StringReader); line: N, column: N]"
        int atSource = msg.indexOf("at [Source:");
        if (atSource > 0) {
            msg = msg.substring(0, atSource).stripTrailing();
        }
        // strip "in 'reader', " prefix from snakeyaml messages
        msg = msg.replace("in 'reader', ", "");
        return msg;
    }

    public static String extractNodeName(String instanceLocation) {
        if (instanceLocation == null || instanceLocation.isEmpty()) {
            return null;
        }
        int slash = instanceLocation.lastIndexOf('/');
        String last = slash >= 0 ? instanceLocation.substring(slash + 1) : instanceLocation;
        if (last.isEmpty()) {
            return null;
        }
        // skip pure numeric segments (array indices)
        try {
            Integer.parseInt(last);
            return null;
        } catch (NumberFormatException e) {
            return last;
        }
    }

    /** The simple expressions of a YAML route checked against the catalog, as predicate where the EIP expects one. */
    public static List<String> validateYamlSimple(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            String simpleText = null;
            int lineNum = i + 1;
            int lineIndent = countLeadingSpaces(line);
            boolean isLogMessage = false;

            // Strip YAML list prefix for matching
            String key = trimmed.startsWith("- ") ? trimmed.substring(2) : trimmed;

            // Match "simple: <value>" (inline shorthand)
            if (key.startsWith("simple:") && !key.equals("simple:")) {
                simpleText = extractYamlValue(key, "simple");
            }
            // Match "simple:" followed by "expression: <value>" on next line
            else if (key.equals("simple:")) {
                for (int j = i + 1; j < lines.length; j++) {
                    String next = lines[j].trim();
                    if (next.isBlank()) {
                        continue;
                    }
                    if (next.startsWith("expression:")) {
                        simpleText = extractYamlValue(next, "expression");
                        lineNum = j + 1;
                    }
                    break;
                }
            }
            // Match "message: <value>" under log: EIP
            else if (key.startsWith("message:") && !key.equals("message:")) {
                String parentEip = findParentEip(lines, i, lineIndent);
                if ("log".equals(parentEip)) {
                    simpleText = extractYamlValue(key, "message");
                    isLogMessage = true;
                }
            }

            if (simpleText == null || simpleText.isEmpty()) {
                continue;
            }
            // Skip placeholder-only expressions
            if (simpleText.startsWith("{{") && simpleText.endsWith("}}")) {
                continue;
            }

            // Determine predicate vs expression context
            boolean predicate = false;
            if (!isLogMessage) {
                String parentEip = findParentEip(lines, i, lineIndent);
                predicate = parentEip != null && PREDICATE_EIPS.contains(parentEip);
            }

            try {
                LanguageValidationResult result = predicate
                        ? catalog.validateLanguagePredicate(null, "simple", simpleText)
                        : catalog.validateLanguageExpression(null, "simple", simpleText);
                if (!result.isSuccess()) {
                    String error = result.getShortError() != null ? result.getShortError() : result.getError();
                    if (error != null) {
                        errors.add("Line " + lineNum + ": Simple syntax error: " + error);
                    }
                }
            } catch (Exception e) {
                // best effort
            }
        }
        return errors;
    }

    /** The endpoint URIs of a YAML route (uri plus a parameters: map) checked against the catalog. */
    public static List<String> validateYamlEndpoints(String content, CamelCatalog catalog) {
        List<String> errors = new ArrayList<>();
        String[] lines = content.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                continue;
            }

            Matcher m = YAML_URI_PATTERN.matcher(line);
            if (!m.find()) {
                continue;
            }

            String uri = m.group(1);
            if (uri.endsWith("\"")) {
                uri = uri.substring(0, uri.length() - 1);
            }
            if (uri.startsWith("{{")) {
                continue;
            }
            // scheme-only URI (e.g., "uri: timer") needs a colon for catalog parsing
            if (!uri.contains(":")) {
                uri = uri + ":";
            }

            String eipName = extractEipFromLine(trimmed);
            int lineIndent = countLeadingSpaces(line);

            // for "uri:" lines, walk backwards to find the parent EIP (from, to, etc.)
            if ("uri".equals(eipName)) {
                for (int j = i - 1; j >= 0; j--) {
                    String prev = lines[j];
                    if (prev.isBlank()) {
                        continue;
                    }
                    int prevIndent = countLeadingSpaces(prev);
                    if (prevIndent < lineIndent) {
                        eipName = extractEipFromLine(prev.trim());
                        break;
                    }
                }
            }

            boolean consumerOnly = eipName != null && CONSUMER_EIPS.contains(eipName);
            boolean producerOnly = eipName != null && PRODUCER_EIPS.contains(eipName);

            // look ahead for a parameters: block at the same indent level as uri
            StringBuilder uriBuilder = new StringBuilder(uri);
            boolean hasParams = uri.contains("?");
            Map<String, Integer> optionLineMap = new LinkedHashMap<>();
            for (int j = i + 1; j < lines.length; j++) {
                String next = lines[j];
                if (next.isBlank()) {
                    continue;
                }
                int nextIndent = countLeadingSpaces(next);
                if (nextIndent < lineIndent) {
                    break;
                }
                String nextTrimmed = next.trim();
                if (nextIndent == lineIndent && nextTrimmed.startsWith("parameters:")) {
                    int paramBlockIndent = nextIndent;
                    for (int k = j + 1; k < lines.length; k++) {
                        String paramLine = lines[k];
                        if (paramLine.isBlank()) {
                            continue;
                        }
                        int paramIndent = countLeadingSpaces(paramLine);
                        if (paramIndent <= paramBlockIndent) {
                            break;
                        }
                        String paramTrimmed = paramLine.trim();
                        int colonPos = paramTrimmed.indexOf(':');
                        if (colonPos > 0) {
                            String key = paramTrimmed.substring(0, colonPos).trim();
                            String val = unquote(paramTrimmed.substring(colonPos + 1).trim());
                            char sep = hasParams ? '&' : '?';
                            uriBuilder.append(sep).append(key).append('=').append(val);
                            hasParams = true;
                            optionLineMap.put(key, k);
                        }
                    }
                    break;
                }
                if (nextIndent == lineIndent) {
                    break;
                }
            }

            String fullUri = uriBuilder.toString();
            try {
                EndpointValidationResult result
                        = catalog.validateEndpointProperties(fullUri, false, consumerOnly, producerOnly);
                if (!result.isSuccess()) {
                    String scheme = fullUri.contains(":") ? fullUri.substring(0, fullUri.indexOf(':')) : fullUri;
                    collectEndpointErrors(errors, result, scheme, i, optionLineMap);
                }
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        return errors;
    }

    static void collectEndpointErrors(
            List<String> errors, EndpointValidationResult result, String scheme,
            int uriLineIdx, Map<String, Integer> optionLineMap) {
        if (result.getUnknown() != null) {
            for (String name : result.getUnknown()) {
                StringBuilder sb = new StringBuilder(scheme).append(": Unknown option '").append(name).append("'");
                if (result.getUnknownSuggestions() != null) {
                    String[] suggestions = result.getUnknownSuggestions().get(name);
                    if (suggestions != null && suggestions.length > 0) {
                        sb.append(". Did you mean: ").append(Arrays.asList(suggestions));
                    }
                }
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx)) + sb);
            }
        }
        addInvalid(errors, result.getInvalidBoolean(), "boolean", scheme, uriLineIdx, optionLineMap);
        addInvalid(errors, result.getInvalidInteger(), "integer", scheme, uriLineIdx, optionLineMap);
        addInvalid(errors, result.getInvalidNumber(), "number", scheme, uriLineIdx, optionLineMap);
        if (result.getInvalidEnum() != null) {
            for (Map.Entry<String, String> entry : result.getInvalidEnum().entrySet()) {
                StringBuilder sb = new StringBuilder(scheme)
                        .append(": Invalid enum value '").append(entry.getValue())
                        .append("' for option '").append(entry.getKey()).append("'");
                if (result.getInvalidEnumChoices() != null) {
                    String[] choices = result.getInvalidEnumChoices().get(entry.getKey());
                    if (choices != null) {
                        sb.append(". Possible values: ").append(Arrays.asList(choices));
                    }
                }
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx)) + sb);
            }
        }
        if (result.getNotConsumerOnly() != null) {
            for (String name : result.getNotConsumerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in consumer only mode");
            }
        }
        if (result.getNotProducerOnly() != null) {
            for (String name : result.getNotProducerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in producer only mode");
            }
        }
    }

    private static void addInvalid(
            List<String> errors, Map<String, String> invalid, String type, String scheme, int uriLineIdx,
            Map<String, Integer> optionLineMap) {
        if (invalid != null) {
            for (Map.Entry<String, String> entry : invalid.entrySet()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(entry.getKey(), uriLineIdx))
                           + scheme + ": Invalid " + type + " value '" + entry.getValue() + "' for option '"
                           + entry.getKey() + "'");
            }
        }
    }

    static String linePrefix(int lineIdx) {
        return "Line " + (lineIdx + 1) + ": ";
    }

    static String findParentEip(String[] lines, int lineIdx, int lineIndent) {
        for (int j = lineIdx - 1; j >= 0; j--) {
            String prev = lines[j];
            if (prev.isBlank()) {
                continue;
            }
            int prevIndent = countLeadingSpaces(prev);
            if (prevIndent < lineIndent) {
                return extractEipFromLine(prev.trim());
            }
        }
        return null;
    }

    static String extractEipFromLine(String trimmed) {
        if (trimmed.startsWith("- ")) {
            trimmed = trimmed.substring(2).trim();
        }
        int colon = trimmed.indexOf(':');
        if (colon > 0) {
            return trimmed.substring(0, colon).trim();
        }
        return null;
    }

    static String extractYamlValue(String trimmed, String key) {
        String prefix = key + ":";
        if (!trimmed.startsWith(prefix)) {
            return null;
        }
        return unquote(trimmed.substring(prefix.length()).trim());
    }

    static String unquote(String val) {
        if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
            return val.substring(1, val.length() - 1);
        }
        if (val.length() >= 2 && val.startsWith("'") && val.endsWith("'")) {
            return val.substring(1, val.length() - 1);
        }
        return val;
    }

    static int countLeadingSpaces(String line) {
        int count = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ' ') {
                count++;
            } else {
                break;
            }
        }
        return count;
    }
}
