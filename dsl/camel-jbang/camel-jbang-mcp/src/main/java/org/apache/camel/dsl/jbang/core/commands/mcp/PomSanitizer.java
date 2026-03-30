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
 * Scans for common credential patterns (passwords, tokens, API keys, secrets) and optionally strips or masks them. Also
 * removes {@code <servers>} and {@code <distributionManagement>} sections which may contain private repository
 * credentials and URLs.
 */
final class PomSanitizer {

    private static final Logger LOG = Logger.getLogger(PomSanitizer.class);

    private static final String SENSITIVE_KEYWORDS
            = "password|passwd|token|apikey|api-key|api_key|secret|secretkey|secret-key|secret_key"
              + "|accesskey|access-key|access_key|passphrase|privatekey|private-key|private_key|credentials";

    /**
     * Pattern matching XML elements whose tag names contain sensitive keywords. Captures: group(1) = element name,
     * group(2) = element value.
     */
    private static final Pattern SENSITIVE_ELEMENT_PATTERN = Pattern.compile(
            "<([a-zA-Z0-9_.:-]*(?:" + SENSITIVE_KEYWORDS + ")[a-zA-Z0-9_.:-]*)>"
                                                                             + "\\s*([^<]+?)\\s*"
                                                                             + "</\\1>",
            Pattern.CASE_INSENSITIVE);

    /** Pattern matching {@code <servers>...</servers>} sections. */
    private static final Pattern SERVERS_SECTION_PATTERN = Pattern.compile(
            "<servers>.*?</servers>", Pattern.DOTALL);

    /** Pattern matching {@code <distributionManagement>...</distributionManagement>} sections. */
    private static final Pattern DIST_MGMT_SECTION_PATTERN = Pattern.compile(
            "<distributionManagement>.*?</distributionManagement>", Pattern.DOTALL);

    private PomSanitizer() {
    }

    /**
     * Detect sensitive content patterns in POM content.
     *
     * @return list of descriptions of detected sensitive patterns
     */
    static List<String> detectSensitiveContent(String pomContent) {
        Set<String> findings = new LinkedHashSet<>();

        Matcher matcher = SENSITIVE_ELEMENT_PATTERN.matcher(pomContent);
        while (matcher.find()) {
            String value = matcher.group(2).trim();
            // Property placeholders like ${my.password} are not actual secrets
            if (!value.startsWith("${")) {
                findings.add(matcher.group(1));
            }
        }

        if (SERVERS_SECTION_PATTERN.matcher(pomContent).find()) {
            findings.add("<servers> section (may contain repository credentials)");
        }

        if (DIST_MGMT_SECTION_PATTERN.matcher(pomContent).find()) {
            findings.add("<distributionManagement> section (may contain private repository URLs)");
        }

        return new ArrayList<>(findings);
    }

    /**
     * Sanitize POM content by masking sensitive element values and stripping credential sections ({@code <servers>} and
     * {@code <distributionManagement>}).
     * <p>
     * Property placeholders (e.g., {@code ${db.password}}) are preserved since they do not contain actual secret
     * values.
     *
     * @return sanitization result with the processed POM content and detected patterns
     */
    static SanitizationResult sanitize(String pomContent) {
        List<String> detected = detectSensitiveContent(pomContent);

        String sanitized = pomContent;

        // Mask sensitive element values (preserve property placeholders)
        sanitized = SENSITIVE_ELEMENT_PATTERN.matcher(sanitized).replaceAll(mr -> {
            String value = mr.group(2).trim();
            if (value.startsWith("${")) {
                return Matcher.quoteReplacement(mr.group());
            }
            return Matcher.quoteReplacement(
                    "<" + mr.group(1) + ">***MASKED***</" + mr.group(1) + ">");
        });

        // Strip servers section
        sanitized = SERVERS_SECTION_PATTERN.matcher(sanitized).replaceAll("");

        // Strip distributionManagement section
        sanitized = DIST_MGMT_SECTION_PATTERN.matcher(sanitized).replaceAll("");

        boolean wasSanitized = !sanitized.equals(pomContent);

        if (!detected.isEmpty()) {
            LOG.warnf("Sensitive data detected in pomContent: %s. Content was sanitized before processing.", detected);
        }

        return new SanitizationResult(sanitized, detected, wasSanitized);
    }

    record SanitizationResult(
            String pomContent,
            List<String> detectedPatterns,
            boolean wasSanitized) {
    }
}
