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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;

/**
 * Answers queries against the published Apache Camel CVE security advisories (the data behind
 * <a href="https://camel.apache.org/security/">camel.apache.org/security</a>).
 * <p>
 * The advisory data ships with the Camel catalog ({@link CamelCatalog#camelSecurityAdvisories()}), where it is synced
 * from the camel-website sources by the {@code update-security-advisories} goal of the camel-package-maven-plugin, the
 * same way the known releases are synced. Lookups are therefore fully offline; the data is as fresh as the catalog
 * bundled with this MCP server.
 * <p>
 * When the catalog contains no advisory data at all (which cannot happen for a correctly built catalog), the service
 * fails with an explicit {@link AdvisoriesUnavailableException} rather than returning an empty result — an empty result
 * must never be mistaken for "no known CVEs".
 */
@ApplicationScoped
public class AdvisoryService {

    static final String SECURITY_PAGE_URL = "https://camel.apache.org/security/";

    private static final Pattern CVE_ID = Pattern.compile("CVE-(\\d{4})-(\\d+)");

    // Version range grammars used by the advisory "affected" prose, in the order they are consumed:
    // "4.10.0 before 4.10.2" / "4.10.0 up to but not including 4.10.2" -> affected range [from, to)
    private static final Pattern RANGE_EXCLUSIVE = Pattern.compile(
            "(\\d+(?:\\.\\d+){1,3})\\s+(?:before|up to but not including)\\s+(\\d+(?:\\.\\d+){1,3})");
    // "2.9.0 up to 2.9.7" / "2.9.0 through 2.9.7" / "2.9.0 to 2.9.7" -> affected range [from, to]
    private static final Pattern RANGE_INCLUSIVE = Pattern.compile(
            "(\\d+(?:\\.\\d+){1,3})\\s+(?:up to|through|to)\\s+(\\d+(?:\\.\\d+){1,3})");
    // "prior to 2.24.0" / "before 2.24.0" without a starting version -> everything below is affected
    private static final Pattern BELOW = Pattern.compile(
            "(?:prior to|before|until)\\s+(\\d+(?:\\.\\d+){1,3})");
    // a remaining bare version is an exactly affected release, e.g. the trailing "2.12.0" in CVE-2013-4330
    private static final Pattern VERSION_TOKEN = Pattern.compile("\\d+(?:\\.\\d+){1,3}");

    private final CamelCatalog catalog;

    public AdvisoryService() {
        this(new DefaultCamelCatalog());
    }

    AdvisoryService(CamelCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * All published advisories from the catalog bundled with this MCP server.
     *
     * @throws AdvisoriesUnavailableException when the catalog carries no advisory data
     */
    public List<SecurityAdvisoryModel> advisories() {
        List<SecurityAdvisoryModel> advisories = catalog.camelSecurityAdvisories();
        if (advisories == null || advisories.isEmpty()) {
            throw new AdvisoriesUnavailableException(
                    "Security advisory data is not available in the Camel catalog used by this MCP server. "
                                                     + "See " + SECURITY_PAGE_URL
                                                     + " for the published advisories.");
        }
        return advisories;
    }

    /**
     * Filter advisories and flatten them to {@link AdvisoryView}, newest first. When {@code camelVersion} is given,
     * advisories whose parsed affected ranges exclude that version are dropped; advisories whose ranges could not be
     * parsed are kept with {@code affectsGivenVersion} unset so the caller can judge from the {@code affected} prose.
     */
    static List<AdvisoryView> query(
            List<SecurityAdvisoryModel> advisories, String camelVersion, String component, String severity) {
        List<AdvisoryView> result = new ArrayList<>();
        for (SecurityAdvisoryModel advisory : advisories) {
            if (!matchesComponent(advisory, component) || !matchesSeverity(advisory, severity)) {
                continue;
            }
            AdvisoryView view = AdvisoryView.of(advisory, camelVersion);
            if (Boolean.FALSE.equals(view.affectsGivenVersion())) {
                continue;
            }
            result.add(view);
        }
        result.sort(Comparator.comparingLong((AdvisoryView view) -> cveOrdinal(view.cve())).reversed());
        return result;
    }

    /**
     * Whether the advisory names the given component (with or without the {@code camel-} prefix). Best-effort: the
     * match is against the components named by the advisory text, and some (mostly older) advisories do not name
     * components at all.
     */
    static boolean matchesComponent(SecurityAdvisoryModel advisory, String component) {
        if (component == null || component.isBlank()) {
            return true;
        }
        String name = component.trim().toLowerCase(Locale.ROOT);
        if (!name.startsWith("camel-")) {
            name = "camel-" + name;
        }
        return advisory.getComponents() != null && advisory.getComponents().contains(name);
    }

    static boolean matchesSeverity(SecurityAdvisoryModel advisory, String severity) {
        if (severity == null || severity.isBlank()) {
            return true;
        }
        return advisory.getSeverity() != null && advisory.getSeverity().equalsIgnoreCase(severity.trim());
    }

    /**
     * Best-effort verdict on whether the given Camel version falls inside the affected ranges stated by the advisory
     * {@code affected} prose. Returns {@code null} when the prose contains no parseable version ranges — the caller
     * must then fall back to the prose itself.
     */
    static Boolean affectsVersion(String affected, String version) {
        if (affected == null || affected.isBlank() || version == null || version.isBlank()) {
            return null;
        }
        // strip qualifiers such as -SNAPSHOT or vendor suffixes before comparing
        String plainVersion = version.trim();
        int dash = plainVersion.indexOf('-');
        if (dash > 0) {
            plainVersion = plainVersion.substring(0, dash);
        }

        boolean parsedAny = false;
        boolean affectedHit = false;
        String text = affected;

        Matcher matcher = RANGE_EXCLUSIVE.matcher(text);
        while (matcher.find()) {
            parsedAny = true;
            affectedHit |= VersionHelper.isBetween(plainVersion, matcher.group(1), matcher.group(2));
        }
        text = matcher.replaceAll(" ");

        matcher = RANGE_INCLUSIVE.matcher(text);
        while (matcher.find()) {
            parsedAny = true;
            affectedHit |= VersionHelper.isGE(plainVersion, matcher.group(1))
                    && VersionHelper.isLE(plainVersion, matcher.group(2));
        }
        text = matcher.replaceAll(" ");

        matcher = BELOW.matcher(text);
        while (matcher.find()) {
            parsedAny = true;
            affectedHit |= VersionHelper.compare(plainVersion, matcher.group(1)) < 0;
        }
        text = matcher.replaceAll(" ");

        matcher = VERSION_TOKEN.matcher(text);
        while (matcher.find()) {
            parsedAny = true;
            affectedHit |= VersionHelper.compare(plainVersion, matcher.group()) == 0
                    && plainVersion.length() == matcher.group().length();
        }

        return parsedAny ? affectedHit : null;
    }

    private static long cveOrdinal(String cve) {
        Matcher matcher = CVE_ID.matcher(cve == null ? "" : cve);
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1)) * 1_000_000L + Long.parseLong(matcher.group(2));
        }
        return 0;
    }

    /** Flattened advisory returned by MCP tools, with the optional per-version verdict. */
    public record AdvisoryView(
            String cve, String severity, String summary, String affected, String fixed,
            String mitigation, String url, List<String> components, Boolean affectsGivenVersion) {

        static AdvisoryView of(SecurityAdvisoryModel advisory, String camelVersion) {
            Boolean affects = camelVersion == null || camelVersion.isBlank()
                    ? null : affectsVersion(advisory.getAffected(), camelVersion);
            return new AdvisoryView(
                    advisory.getCve(), advisory.getSeverity(), advisory.getSummary(), advisory.getAffected(),
                    advisory.getFixed(), advisory.getMitigation(), advisory.getUrl(), advisory.getComponents(),
                    affects);
        }
    }

    /** Signals that the catalog used by this MCP server carries no advisory data. */
    public static class AdvisoriesUnavailableException extends RuntimeException {
        public AdvisoriesUnavailableException(String message) {
            super(message);
        }
    }
}
