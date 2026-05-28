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

import org.jboss.logging.Logger;

/**
 * Utility to detect and sanitize sensitive data in POM content before processing.
 * <p>
 * Scans for common credential patterns (passwords, tokens, API keys, secrets) in XML element values and masks them.
 * Also detects credentials embedded in URLs ({@code ://user:password@host}). Property placeholders (e.g.,
 * {@code ${db.password}}, {@code {{vault:password}}}) are preserved since they reference external values and do not
 * contain actual secrets. CDATA-wrapped values are inspected and masked when appropriate.
 * <p>
 * <b>Limitations:</b> Detection is tag-name-based using keyword matching. This means:
 * <ul>
 * <li><b>False positives</b> — non-secret values in elements whose names happen to contain a keyword (e.g.,
 * {@code <password-policy>strict</password-policy>},
 * {@code <token-refresh-interval>300</token-refresh-interval>}).</li>
 * <li><b>False negatives</b> — actual secrets in elements with non-obvious names (e.g., elements named
 * {@code <my.credential>} where the singular form is not in the keyword list). URL credential detection is limited to
 * the {@code ://user:password@host} pattern.</li>
 * </ul>
 * This heuristic is a best-effort safety net, not a guarantee. Users should still avoid passing sensitive data.
 */
final class PomSanitizer {

    private static final Logger LOG = Logger.getLogger(PomSanitizer.class);

    private static final String SENSITIVE_KEYWORDS
            = "password|passwd|token|apikey|api-key|api_key|secret|secretkey|secret-key|secret_key"
              + "|accesskey|access-key|access_key|passphrase|privatekey|private-key|private_key|credentials"
              + "|connection-string|connectionstring|connection_string";

    /**
     * Pattern matching XML elements whose tag names contain sensitive keywords. Captures: group(1) = element name,
     * group(2) = full content between tags (including whitespace and optional CDATA wrapper).
     */
    private static final Pattern SENSITIVE_ELEMENT_PATTERN = Pattern.compile(
            "<([a-zA-Z0-9_.:-]*(?:" + SENSITIVE_KEYWORDS + ")[a-zA-Z0-9_.:-]*)>"
                                                                             + "(\\s*(?:<!\\[CDATA\\[.*?\\]\\]>|[^<]+?)\\s*)"
                                                                             + "</\\1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Pattern matching URL-embedded credentials ({@code ://user:password@host}). Captures: group(1) = scheme through
     * username ({@code ://user}), group(2) = password.
     */
    private static final Pattern URL_CREDENTIAL_PATTERN = Pattern.compile(
            "(://[^/@\\s:]+):([^/@\\s]+)@");

    private PomSanitizer() {
    }

    /**
     * Detect sensitive content patterns in POM content.
     *
     * @return list of element names that contain sensitive values
     */
    static List<String> detectSensitiveContent(String pomContent) {
        return sanitize(pomContent).detectedPatterns();
    }

    /**
     * Sanitize POM content by masking sensitive element values and URL-embedded credentials.
     * <p>
     * Property placeholders (e.g., {@code ${db.password}}, {@code {{vault:password}}}) are preserved since they do not
     * contain actual secret values. CDATA-wrapped values are inspected and masked when they contain plain-text secrets.
     *
     * @return sanitization result with the processed POM content and detected patterns
     */
    static SanitizationResult sanitize(String pomContent) {
        Set<String> detected = new LinkedHashSet<>();

        String sanitized = SENSITIVE_ELEMENT_PATTERN.matcher(pomContent).replaceAll(mr -> {
            String elementName = mr.group(1);
            String value = extractValue(mr.group(2));
            if (isPlaceholder(value)) {
                return Matcher.quoteReplacement(mr.group());
            }
            detected.add(elementName);
            return Matcher.quoteReplacement(
                    "<" + elementName + ">***MASKED***</" + elementName + ">");
        });

        sanitized = URL_CREDENTIAL_PATTERN.matcher(sanitized).replaceAll(mr -> {
            detected.add("(URL credential)");
            return Matcher.quoteReplacement(mr.group(1) + ":***MASKED***@");
        });

        if (!detected.isEmpty()) {
            LOG.warnf("Sensitive data detected in pomContent: %s. Content was sanitized before processing.", detected);
        }

        return new SanitizationResult(sanitized, new ArrayList<>(detected));
    }

    /**
     * Process POM content with optional sanitization. This is the entry point for tool methods.
     *
     * @param  pomContent the raw POM content
     * @param  sanitize   if {@code null} or {@code true}, sanitize; if {@code false}, skip sanitization
     * @return            processed result with content and any warnings
     */
    static ProcessedPom process(String pomContent, Boolean sanitize) {
        if (sanitize != null && !sanitize) {
            return new ProcessedPom(pomContent, List.of());
        }
        SanitizationResult sr = sanitize(pomContent);
        List<String> warnings = new ArrayList<>();
        if (!sr.detectedPatterns().isEmpty()) {
            warnings.add("Sensitive data detected and masked: "
                         + String.join(", ", sr.detectedPatterns()));
        }
        return new ProcessedPom(sr.pomContent(), warnings);
    }

    private static String extractValue(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("<![CDATA[") && trimmed.endsWith("]]>")) {
            return trimmed.substring(9, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private static boolean isPlaceholder(String value) {
        return value.startsWith("${")
                || (value.startsWith("{{") && value.endsWith("}}"));
    }

    record SanitizationResult(
            String pomContent,
            List<String> detectedPatterns) {
    }

    record ProcessedPom(
            String content,
            List<String> warnings) {
    }
}
