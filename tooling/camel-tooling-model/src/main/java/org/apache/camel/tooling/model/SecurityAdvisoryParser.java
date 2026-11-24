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
package org.apache.camel.tooling.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

/**
 * Parses a published Apache Camel security advisory: a Markdown file with YAML front matter in the camel-website
 * repository ({@code content/security/CVE-*.md}), the source of <a href="https://camel.apache.org/security/">
 * camel.apache.org/security</a>. Used by the {@code update-security-advisories} goal that syncs the advisories of the
 * catalog, and by the CVE audit that checks them online. Needs {@code org.snakeyaml:snakeyaml-engine}, an optional
 * dependency of this module.
 */
public final class SecurityAdvisoryParser {

    /** The website the relative URL of an advisory is on. */
    public static final String WEBSITE_BASE_URL = "https://camel.apache.org";

    /** The name of an advisory file, such as {@code CVE-2025-27636.md}. */
    public static final Pattern ADVISORY_FILE = Pattern.compile("CVE-\\d{4}-\\d{4,}\\.md");

    // the first segment must start with a letter so that lowercased JIRA ids ("camel-12444") never match
    private static final Pattern COMPONENT_TOKEN = Pattern.compile("camel-[a-z][a-z0-9]+(?:-[a-z0-9]+)*");

    /**
     * English prose that follows "Camel-" in advisory texts (e.g. "Camel-internal headers", "non-Camel-prefixed names")
     * and therefore must not be mistaken for component artifact ids. Deliberately not validated against the current
     * catalog instead: old advisories legitimately name components that have since been removed or renamed
     * (camel-xstream, camel-hessian, camel-castor, camel-cxfrs, ...), which must remain filterable.
     */
    private static final Set<String> COMPONENT_TOKEN_DENYLIST = Set.of(
            "camel-internal", "camel-prefixed", "camel-specific", "camel-side", "camel-namespace",
            "camel-case", "camel-cased", "camel-based", "camel-related", "camel-style");

    private SecurityAdvisoryParser() {
    }

    /**
     * Parse one advisory Markdown file (YAML front matter). Returns {@code null} for drafts, non-advisory pages and
     * files without parseable front matter, so only published advisories are included.
     */
    public static SecurityAdvisoryModel parse(String content) {
        Map<String, Object> frontMatter = frontMatter(content);
        if (frontMatter == null) {
            return null;
        }
        if (!"security-advisory".equals(str(frontMatter.get("type")))) {
            return null;
        }
        Object draft = frontMatter.get("draft");
        if (Boolean.TRUE.equals(draft) || "true".equalsIgnoreCase(str(draft))) {
            return null;
        }
        String cve = str(frontMatter.get("cve"));
        if (cve == null || cve.isBlank()) {
            return null;
        }

        SecurityAdvisoryModel model = new SecurityAdvisoryModel();
        model.setCve(cve.trim());
        model.setDate(trimmed(frontMatter.get("date")));
        String severity = str(frontMatter.get("severity"));
        if (severity != null) {
            model.setSeverity(severity.trim().toUpperCase(Locale.ROOT));
        }
        model.setSummary(trimmed(frontMatter.get("summary")));
        model.setAffected(trimmed(frontMatter.get("affected")));
        model.setFixed(trimmed(frontMatter.get("fixed")));
        model.setMitigation(trimmed(frontMatter.get("mitigation")));

        String url = str(frontMatter.get("url"));
        if (url == null || url.isBlank()) {
            url = "/security/" + model.getCve() + ".html";
        }
        if (url.startsWith("/")) {
            url = WEBSITE_BASE_URL + url;
        }
        model.setUrl(url.trim());

        model.setComponents(extractComponents(str(frontMatter.get("title")), model.getSummary(),
                str(frontMatter.get("description")), model.getMitigation()));
        return model;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> frontMatter(String content) {
        if (content == null) {
            return null;
        }
        String trimmedContent = content.stripLeading();
        if (!trimmedContent.startsWith("---")) {
            return null;
        }
        int start = trimmedContent.indexOf('\n');
        if (start < 0) {
            return null;
        }
        int end = trimmedContent.indexOf("\n---", start);
        if (end < 0) {
            return null;
        }
        String yaml = trimmedContent.substring(start + 1, end);
        try {
            Object parsed = new Load(LoadSettings.builder().build()).loadFromString(yaml);
            return parsed instanceof Map ? (Map<String, Object>) parsed : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The Camel components named by the advisory text, as {@code camel-*} tokens (best-effort: some older advisories do
     * not name components at all). Lowercased JIRA ids and common English prose such as "Camel-internal" are filtered
     * out.
     */
    public static List<String> extractComponents(String... texts) {
        TreeSet<String> found = new TreeSet<>();
        for (String text : texts) {
            if (text == null) {
                continue;
            }
            Matcher matcher = COMPONENT_TOKEN.matcher(text.toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                if (!COMPONENT_TOKEN_DENYLIST.contains(matcher.group())) {
                    found.add(matcher.group());
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String trimmed(Object value) {
        String text = str(value);
        return text == null ? null : text.trim();
    }
}
