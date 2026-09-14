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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.YAML_URI_PATTERN;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.extractEipFromLine;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.findParentEip;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.unquote;

/**
 * The endpoint URI checks of {@link SourceValidator}: every uri against the catalog, invented options with the option
 * meant, a regular expression that does not compile, a producer-only component as consumer, several endpoints in one
 * to:.
 */
final class EndpointChecks {

    private EndpointChecks() {
    }

    static final Set<String> CONSUMER_EIPS
            = Set.of("from", "pollEnrich", "poll-enrich", "poll", "interceptFrom", "intercept-from");

    static final Set<String> PRODUCER_EIPS
            = Set.of("to", "toD", "to-d", "wireTap", "wire-tap", "enrich",
                    "interceptSendToEndpoint", "intercept-send-to-endpoint");

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
            Matcher several = SEVERAL_ENDPOINTS_PATTERN.matcher(uri);
            if (several.find()) {
                // to: direct:a,direct:b : one endpoint per to:, several go through multicast or recipientList
                errors.add(linePrefix(i) + "a to: takes one endpoint; \"" + uri + "\" names several: send to each with"
                           + " multicast: {to: [...]} (all of them) or recipientList: {expression: {constant: {expression: \""
                           + uri + "\"}}}"
                           + " (a list evaluated at runtime), or write one - to: step per endpoint");
                continue;
            }
            // scheme-only URI (e.g., "uri: timer") needs a colon for catalog parsing
            if (!uri.contains(":")) {
                uri = uri + ":";
            }

            String eipName = extractEipFromLine(trimmed);
            {
                // from: mock:result, from: log:x : a producer-only component cannot be consumed from
                String scheme0 = uri.substring(0, uri.indexOf(':'));
                boolean isFrom = "from".equals(eipName)
                        || "uri".equals(eipName) && "from".equals(findParentEip(lines, i, countLeadingSpaces(line)));
                if (isFrom) {
                    try {
                        var cm = catalog.componentModel(scheme0);
                        if (cm != null && cm.isProducerOnly()) {
                            errors.add(linePrefix(i) + scheme0 + " is a producer-only component: it cannot be a from:"
                                       + " (the runtime says 'You cannot consume from this endpoint'); to pass messages"
                                       + " between routes send with to: direct:name and consume with from: direct:name"
                                       + " (or seda: for a queue)");
                            continue;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            }
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
                checkRegexOptions(errors, fullUri, i, optionLineMap);
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        return errors;
    }

    /** Options models write that the component does not have, and what the component does instead. */
    static final Map<String, String> INVENTED_OPTIONS = Map.ofEntries(
            Map.entry("file:mkdir", "directories are created by default (autoCreate=true); remove the option"),
            Map.entry("file:createDirectory", "directories are created by default (autoCreate=true); remove the option"),
            Map.entry("file:overwrite", "an existing file is overridden by default (fileExist=Override); remove the option"),
            Map.entry("file:append", "write fileExist=Append"),
            Map.entry("file:name", "write fileName=<name>"),
            Map.entry("file:filename", "write fileName=<name>"),
            Map.entry("file:body",
                    "the file content is the message body: set it with a setBody step before the to: file: step"),
            Map.entry("file:content",
                    "the file content is the message body: set it with a setBody step before the to: file: step"),
            Map.entry("timer:interval", "write period=<millis>"),
            Map.entry("timer:delayMs", "write delay=<millis>"),
            Map.entry("timer:repeat", "write repeatCount=<n>"),
            Map.entry("timer:body", "a timer message has no body: set it with a setBody step"
                                    + " (setBody: {expression: {constant: {expression: \"...\"}}})"),
            Map.entry("timer:message", "a timer message has no body: set it with a setBody step"
                                       + " (setBody: {expression: {constant: {expression: \"...\"}}})"),
            Map.entry("timer:cron", "a cron expression is the cron or quartz component: cron:tick?schedule=0/5+*+*+*+*+?"),
            Map.entry("timer:schedule", "a cron expression is the cron or quartz component: cron:tick?schedule=0/5+*+*+*+*+?"),
            Map.entry("log:message", "the message is the body; a text is set with a setBody step or the log EIP"),
            Map.entry("log:name", "the logger name is the path: log:com.example"));

    /** direct:a,direct:b or log:a,mock:b: a comma followed by another scheme inside one uri. */
    static final Pattern SEVERAL_ENDPOINTS_PATTERN
            = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:[^?]*,[a-zA-Z][a-zA-Z0-9+.-]*:");

    static final Set<String> FILE_SCHEMES = Set.of("file", "ftp", "ftps", "sftp", "file-watch", "smb");

    /**
     * include and exclude on the file components are regular expressions: include=*.txt fails at startup with a
     * PatternSyntaxException wrapped in a binding error. Says to write .*\\.txt or use antInclude.
     */
    static void checkRegexOptions(List<String> errors, String fullUri, int uriLineIdx, Map<String, Integer> optionLineMap) {
        int colon = fullUri.indexOf(':');
        int q = fullUri.indexOf('?');
        if (colon < 0 || q < 0 || !FILE_SCHEMES.contains(fullUri.substring(0, colon))) {
            return;
        }
        for (String pair : fullUri.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String name = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (!name.equals("include") && !name.equals("exclude") || value.startsWith("{{")) {
                continue;
            }
            try {
                Pattern.compile(value);
            } catch (java.util.regex.PatternSyntaxException e) {
                String ant = name.equals("include") ? "antInclude" : "antExclude";
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx)) + fullUri.substring(0, colon) + ": "
                           + name + "=" + value + " is not a regular expression (" + e.getDescription() + "): " + name
                           + " is a regex, write " + name + "=" + toRegex(value) + ", or use the wildcard option " + ant
                           + "=" + value);
            }
        }
    }

    /** A wildcard such as *.txt as the regex .*\\.txt. */
    static String toRegex(String wildcard) {
        StringBuilder sb = new StringBuilder();
        for (char ch : wildcard.toCharArray()) {
            if (ch == '*') {
                sb.append(".*");
            } else if (ch == '?') {
                sb.append('.');
            } else if (".\\+()[]{}^$|".indexOf(ch) >= 0) {
                sb.append('\\').append(ch);
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
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
                String known = INVENTED_OPTIONS.get(scheme + ":" + name);
                if (known != null) {
                    sb.append(" (").append(known).append(")");
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
                           + scheme + ": Option '" + name + "' is not applicable in consumer only mode (from: consumes;"
                           + " a producer option belongs on a to:)");
            }
        }
        if (result.getNotProducerOnly() != null) {
            for (String name : result.getNotProducerOnly()) {
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx))
                           + scheme + ": Option '" + name + "' is not applicable in producer only mode (to: sends to the"
                           + " endpoint, for " + scheme + ": it writes the body; to read from an endpoint in the middle of a"
                           + " route use the poll EIP, or pollEnrich, see camel_catalog_sample poll)");
            }
        }
    }

    static void addInvalid(
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

}
