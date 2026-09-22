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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.EndpointValidationResult;
import org.apache.camel.catalog.RuntimeProvider;

import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.YAML_URI_PATTERN;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.countLeadingSpaces;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.extractEipFromLine;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.findParentEip;
import static org.apache.camel.dsl.jbang.core.commands.ai.YamlLines.stripComment;
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
                    if (hasParams) {
                        // to: {uri: "file:inbox?fileExist=Override", parameters: {fileName: x}}: the YAML DSL refuses
                        // options in both places at startup ("Uri should not contains query parameters"), the schema
                        // does not see it (CAMEL-24842); say it here, with what to write
                        String query = uri.substring(uri.indexOf('?') + 1);
                        String first = query.contains("&") ? query.substring(0, query.indexOf('&')) : query;
                        String asYaml = first.contains("=")
                                ? first.substring(0, first.indexOf('=')) + ": " + first.substring(first.indexOf('=') + 1)
                                : first;
                        errors.add(linePrefix(i) + "the uri has query options (" + query + ") and the step also has"
                                   + " parameters: put every option under parameters: (" + asYaml + ") or all of them"
                                   + " in the uri, not both (the runtime refuses the mix with 'Uri should not contains"
                                   + " query parameters')");
                    }
                    int paramBlockIndent = nextIndent;
                    int blockScalarIndent = -1;
                    String mapKey = null;
                    int mapIndent = -1;
                    for (int k = j + 1; k < lines.length; k++) {
                        String paramLine = lines[k];
                        if (paramLine.isBlank()) {
                            continue;
                        }
                        int paramIndent = countLeadingSpaces(paramLine);
                        if (paramIndent <= paramBlockIndent) {
                            break;
                        }
                        if (blockScalarIndent >= 0 && paramIndent > blockScalarIndent) {
                            // the lines of a block scalar (argSchema: | followed by JSON) are its value, not options
                            continue;
                        }
                        blockScalarIndent = -1;
                        if (mapKey != null && paramIndent <= mapIndent) {
                            mapKey = null;
                        }
                        String paramTrimmed = paramLine.trim();
                        int colonPos = paramTrimmed.indexOf(':');
                        if (colonPos > 0) {
                            String key = unquote(paramTrimmed.substring(0, colonPos).trim());
                            String val = unquote(stripComment(paramTrimmed.substring(colonPos + 1).trim()));
                            if (mapKey != null) {
                                // headers: with foo: bar under it is the entry foo of the headers map option
                                key = mapKey + "." + key;
                            } else if (val.isEmpty() && k + 1 < lines.length
                                    && countLeadingSpaces(lines[k + 1]) > paramIndent) {
                                mapKey = key;
                                mapIndent = paramIndent;
                                continue;
                            }
                            if (YamlLines.isBlockScalarIndicator(val)) {
                                // the option is checked by name; its value is the block that follows
                                blockScalarIndent = paramIndent;
                                val = "";
                            }
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
                String scheme = fullUri.contains(":") ? fullUri.substring(0, fullUri.indexOf(':')) : fullUri;
                if (result.getUnknownComponent() != null) {
                    // a warning to the catalog, an error when the runtime is what lacks the component
                    String missing = missingInRuntime(catalog, scheme);
                    if (missing != null) {
                        errors.add(linePrefix(i) + missing);
                    }
                }
                if (!result.isSuccess()) {
                    collectEndpointErrors(errors, result, scheme, i, optionLineMap);
                }
                checkRegexOptions(errors, fullUri, i, optionLineMap);
                checkDynamicDirectory(errors, fullUri, i, eipName);
                checkSimplePlaceholders(errors, fullUri, i, optionLineMap, eipName);
                checkRequiredPathOptions(errors, fullUri, catalog, i, eipName);
                checkSqlNamedParameters(errors, fullUri, line, i, optionLineMap);
            } catch (Exception e) {
                // ignore validation errors
            }
        }
        return errors;
    }

    private static volatile CamelCatalog defaultCatalog;

    /**
     * The message for a component the catalog of a runtime (Camel Quarkus, Camel Spring Boot) does not have while Camel
     * has it: the runtime has no extension or starter for it. Null for the default catalog, whose unknown components
     * are not reported: a project can register a component of its own (CAMEL-24711).
     */
    static String missingInRuntime(CamelCatalog catalog, String scheme) {
        RuntimeProvider provider = catalog.getRuntimeProvider();
        String name = provider != null ? provider.getProviderName() : null;
        if (name == null || "default".equals(name)) {
            return null;
        }
        CamelCatalog plain = defaultCatalog;
        if (plain == null) {
            synchronized (EndpointChecks.class) {
                plain = defaultCatalog;
                if (plain == null) {
                    plain = new DefaultCamelCatalog();
                    defaultCatalog = plain;
                }
            }
        }
        if (plain.componentModel(scheme) == null) {
            return null;
        }
        return switch (name) {
            case "quarkus" -> scheme + ": Camel Quarkus has no extension for this component (no camel-quarkus-" + scheme
                              + "); pick a component that has one, camel_catalog_find lists them";
            case "springboot" -> scheme + ": Camel Spring Boot has no starter for this component (no camel-" + scheme
                                 + "-starter)";
            default -> scheme + ": the " + name + " runtime has no support for this component";
        };
    }

    /** Options models write that the component does not have, and what the component does instead. */
    static final Map<String, String> INVENTED_OPTIONS = Map.ofEntries(
            // CAMEL-24888: the path parameters of an OpenAPI operation are headers of the same name
            Map.entry("rest-openapi:path",
                    "a path parameter of the operation, {sku} in /stock/{sku}, comes from a header of the same name: add"
                                           + " setHeader: {name: sku, ...} before the call, the operation's path is in the contract"),
            Map.entry("rest-openapi:pathParameters",
                    "a path parameter of the operation comes from a header of the same name: add setHeader: {name: sku,"
                                                     + " ...} before the call"),
            Map.entry("rest-openapi:queryParameters",
                    "a query parameter of the operation comes from a header of the same name: add setHeader: {name: page,"
                                                      + " ...} before the call"),
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
     * A doubled backslash before a character that a single backslash would escape in a regex (\\. \\d \\( ...): the
     * user meant the escape. A doubled backslash before any other character (\\myfile) is left alone: \myfile is not a
     * regex escape, so a literal backslash is the only thing it can mean.
     */
    static final Pattern DOUBLED_BACKSLASH_ESCAPE = Pattern.compile("\\\\\\\\[.dswDSWbB()\\[\\]{}+*?|^$]");

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
            if (DOUBLED_BACKSLASH_ESCAPE.matcher(value).find()) {
                // '.*\\.json$' in single quotes: YAML keeps both backslashes, and in a regex \\ is one literal
                // backslash, so the pattern matches a file name with a backslash in it: no file matches and the route
                // runs in silence (CAMEL-24854)
                errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx)) + fullUri.substring(0, colon) + ": "
                           + name + "=" + value + " matches a literal backslash in the file name (in a regex \\\\ is one"
                           + " backslash and \\. is a dot): write " + name + "='" + value.replace("\\\\", "\\") + "'");
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

    /**
     * file:archived/${header.monthDir} on a to: fails at startup: the directory of a file endpoint cannot be dynamic
     * (the runtime says "Dynamic expressions with ${ } placeholders is not allowed. Use the fileName option"). Says to
     * keep the directory fixed and put the dynamic part in fileName, or to use toD: (which evaluates the uri first).
     * toD, wireTap, enrich and pollEnrich evaluate the expression before the endpoint is created and are left alone.
     */
    static void checkDynamicDirectory(List<String> errors, String fullUri, int uriLineIdx, String eipName) {
        int colon = fullUri.indexOf(':');
        if (colon < 0 || !FILE_SCHEMES.contains(fullUri.substring(0, colon))) {
            return;
        }
        if (eipName == null || !eipName.equals("to") && !eipName.equals("from")) {
            // an unresolved parent may be a toD: or wireTap:, which evaluate the uri first: leave it alone
            return;
        }
        int q = fullUri.indexOf('?');
        String dir = q >= 0 ? fullUri.substring(colon + 1, q) : fullUri.substring(colon + 1);
        if (dir.startsWith("//")) {
            dir = dir.substring(2);
        }
        if (!dir.contains("${")) {
            return;
        }
        String scheme = fullUri.substring(0, colon);
        String fixed = dir.substring(0, dir.indexOf("${"));
        if (fixed.endsWith("/")) {
            fixed = fixed.substring(0, fixed.length() - 1);
        }
        errors.add(linePrefix(uriLineIdx) + scheme + ": the directory " + dir + " cannot be dynamic (the runtime"
                   + " says 'Dynamic expressions with ${ } placeholders is not allowed. Use the fileName option'):"
                   + " keep the directory fixed and put the dynamic part in fileName (" + scheme + ":" + fixed
                   + "?fileName=${...}), or use toD: with the whole uri, which evaluates it per message");
    }

    /**
     * A :name in a sql query that is not a camel-sql named parameter: camel-sql has :#name (a header, or a key of a Map
     * body) and :#${simple}; a bare :name (the Spring or JPA form) goes to the JDBC driver as written and fails at
     * runtime with a syntax error, after the consumer retried it (CAMEL-24869). ::type casts, :?name stored procedure
     * parameters and times such as 10:30 are left alone.
     */
    private static final Pattern BARE_NAMED_PARAMETER = Pattern.compile("(?<![\\w:#?$'\"]):([A-Za-z_][\\w.\\[\\]]*)");

    static void checkSqlNamedParameters(
            List<String> errors, String fullUri, String rawLine, int uriLineIdx, Map<String, Integer> optionLineMap) {
        int colon = fullUri.indexOf(':');
        int q = fullUri.indexOf('?');
        String scheme = colon < 0 ? (q < 0 ? fullUri : fullUri.substring(0, q)) : fullUri.substring(0, colon);
        if (q >= 0 && colon > q) {
            scheme = fullUri.substring(0, q);
        }
        if (!"sql".equals(scheme)) {
            return;
        }
        // the query is the path (sql:SELECT ...) or the query option (uri: sql with parameters: query: ...)
        String query = null;
        int line = uriLineIdx;
        if (colon >= 0 && (q < 0 || colon < q)) {
            // the uri pattern stops at the first space, so take the statement from the line itself
            int at = rawLine.indexOf("sql:");
            String path = at >= 0 ? rawLine.substring(at + 4).trim() : fullUri.substring(colon + 1);
            if (path.endsWith("\"") || path.endsWith("'")) {
                path = path.substring(0, path.length() - 1);
            }
            Matcher options = Pattern.compile("\\?\\w+=").matcher(path);
            query = options.find() ? path.substring(0, options.start()) : path;
        }
        if ((query == null || query.isBlank()) && q >= 0) {
            for (String option : fullUri.substring(q + 1).split("&")) {
                if (option.startsWith("query=")) {
                    query = option.substring("query=".length());
                    line = optionLineMap.getOrDefault("query", uriLineIdx);
                }
            }
        }
        if (query == null || query.isBlank()) {
            return;
        }
        Matcher m = BARE_NAMED_PARAMETER.matcher(query);
        List<String> bare = new ArrayList<>();
        while (m.find()) {
            String name = m.group(1);
            if (!bare.contains(name)) {
                bare.add(name);
            }
        }
        if (bare.isEmpty()) {
            return;
        }
        String first = bare.get(0);
        // :customer -> :#customer (a header or a Map body key); :body[customer] -> :#${body[customer]} (a Simple expression)
        String fix = first.matches("\\w+")
                ? ":#" + first + " for a header or a key of a Map body, or :#${body[" + first + "]} with a Simple expression"
                : ":#${" + first + "} (a Simple expression) or :#name for a header or a key of a Map body";
        errors.add(linePrefix(line) + "sql: :" + first + " is not a camel-sql named parameter (the JDBC driver gets it as"
                   + " written and fails with a syntax error): write " + fix
                   + (bare.size() > 1 ? " (also :" + String.join(", :", bare.subList(1, bare.size())) + ")" : ""));
    }

    /** The EIPs whose uri is a Simple expression evaluated per message: ${...} is right there. */
    private static final Set<String> DYNAMIC_URI_EIPS = Set.of("toD", "to-d", "wireTap", "wire-tap", "enrich", "pollEnrich",
            "poll-enrich", "recipientList", "recipient-list", "routingSlip", "routing-slip", "dynamicRouter", "dynamic-router");

    /**
     * period=${welcome.period} or period=${properties:welcome.period} on a to: or from:: a Simple expression, which an
     * endpoint option is not; the property placeholder is {{welcome.period}}. The runtime fails to bind the option at
     * startup or on the reload, and camel validate said nothing (CAMEL-24857). toD and the other dynamic EIPs evaluate
     * the uri as Simple first and are left alone.
     */
    static void checkSimplePlaceholders(
            List<String> errors, String fullUri, int uriLineIdx, Map<String, Integer> optionLineMap, String eipName) {
        if (eipName != null && DYNAMIC_URI_EIPS.contains(eipName)) {
            return;
        }
        int q = fullUri.indexOf('?');
        if (q < 0) {
            return;
        }
        String scheme = fullUri.substring(0, Math.max(0, fullUri.indexOf(':')));
        for (String pair : fullUri.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String name = pair.substring(0, eq);
            String value = pair.substring(eq + 1);
            if (!YamlLines.isPropertyKeyInSimpleSyntax(value)) {
                continue;
            }
            String key = YamlLines.propertyKeyOf(value);
            errors.add(linePrefix(optionLineMap.getOrDefault(name, uriLineIdx)) + scheme + ": " + name + "=" + value
                       + " is a Simple expression, which an endpoint option is not evaluated as: a property placeholder"
                       + " is written {{key}}, so " + name + ": \"{{" + key + "}}\"");
        }
    }

    /**
     * uri: cron with only a schedule under parameters: the required path option name is neither in the uri nor among
     * the parameters; camel run fails with "Option name is required when creating endpoint uri with syntax cron:name"
     * (CAMEL-24858). Says both places it can go.
     */
    static void checkRequiredPathOptions(
            List<String> errors, String fullUri, CamelCatalog catalog, int uriLineIdx, String eipName) {
        int colon = fullUri.indexOf(':');
        if (colon < 0 || fullUri.contains("{{") || !"from".equals(eipName) && !"to".equals(eipName)) {
            return; // only an endpoint that is created: an intercept pattern such as jms* names no destination
        }
        String scheme = fullUri.substring(0, colon);
        int q = fullUri.indexOf('?');
        String path = q >= 0 ? fullUri.substring(colon + 1, q) : fullUri.substring(colon + 1);
        if (path.startsWith("//") || !path.isEmpty()) {
            // a path is given: which path option it fills is the component's business; an explicit empty authority
            // (infinispan:// with a custom listener) is a choice, a bare scheme with the options under parameters is
            // the slip this catches
            return;
        }
        Set<String> given = new HashSet<>();
        if (q >= 0) {
            for (String pair : fullUri.substring(q + 1).split("&")) {
                given.add(pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair);
            }
        }
        try {
            var model = catalog.componentModel(scheme);
            if (model == null) {
                return;
            }
            for (var option : model.getEndpointOptions()) {
                if (option.isRequired() && "path".equals(option.getKind()) && !given.contains(option.getName())) {
                    errors.add(linePrefix(uriLineIdx) + scheme + ": the required option '" + option.getName()
                               + "' is missing (the runtime says 'Option " + option.getName() + " is required'): write"
                               + " it in the uri, uri: " + scheme + ":<" + option.getName() + ">, or under parameters"
                               + " as " + option.getName() + ": <value>");
                }
            }
        } catch (Exception e) {
            // ignore: a component the catalog does not know is reported elsewhere
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
