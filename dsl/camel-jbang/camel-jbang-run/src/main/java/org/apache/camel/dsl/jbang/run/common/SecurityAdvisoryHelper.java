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
package org.apache.camel.dsl.jbang.run.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.tooling.model.SecurityAdvisoryModel;

/**
 * Reads the Apache Camel security advisories that ship with the Camel catalog
 * ({@code CamelCatalog#camelSecurityAdvisories()}): whether a Camel version is affected, and the release to upgrade to.
 * The advisories state their versions as prose ("From 4.0.0 before 4.14.8, from 4.15.0 before 4.18.3"), which is parsed
 * best-effort. Shared by the MCP server and the CVE Audit screen of the Camel CLI.
 */
public final class SecurityAdvisoryHelper {

    /** The web page with the published advisories. */
    public static final String SECURITY_PAGE_URL = "https://camel.apache.org/security/";

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

    private SecurityAdvisoryHelper() {
    }

    /**
     * Whether the advisory names the given Camel artifact (with or without the {@code camel-} prefix). Best-effort: the
     * match is against the components named by the advisory text, and some (mostly older) advisories do not name
     * components at all.
     */
    public static boolean namesComponent(SecurityAdvisoryModel advisory, String component) {
        if (component == null || component.isBlank()) {
            return true;
        }
        String name = component.trim().toLowerCase(Locale.ROOT);
        if (!name.startsWith("camel-")) {
            name = "camel-" + name;
        }
        return advisory.getComponents() != null && advisory.getComponents().contains(name);
    }

    /**
     * Best-effort verdict on whether the given Camel version falls inside the affected ranges stated by the advisory
     * {@code affected} prose. Returns {@code null} when the prose contains no parseable version ranges: the caller must
     * then fall back to the prose itself.
     */
    public static Boolean affectsVersion(String affected, String version) {
        if (affected == null || affected.isBlank() || version == null || version.isBlank()) {
            return null;
        }
        String plainVersion = plainVersion(version);

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

    /**
     * The Camel release that fixes the advisory for a user of the given version: the fixed release of the same
     * {@code major.minor} line when there is one above it (4.18.1 upgrades to 4.18.3), else the lowest fixed release
     * above it (4.16.0 upgrades to 4.18.3). Returns {@code null} when the version is not affected, or when the advisory
     * names no fixed release above it.
     */
    public static String upgradeTo(String affected, String fixed, String version) {
        if (fixed == null || fixed.isBlank() || version == null || version.isBlank()
                || Boolean.FALSE.equals(affectsVersion(affected, version))) {
            return null;
        }
        String plainVersion = plainVersion(version);
        String line = majorMinor(plainVersion);
        List<String> above = new ArrayList<>();
        Matcher matcher = VERSION_TOKEN.matcher(fixed);
        while (matcher.find()) {
            String candidate = matcher.group();
            if (VersionHelper.compare(candidate, plainVersion) > 0
                    && !Boolean.TRUE.equals(affectsVersion(affected, candidate))) {
                above.add(candidate);
            }
        }
        String sameLine = null;
        String lowest = null;
        for (String candidate : above) {
            if (line != null && line.equals(majorMinor(candidate))
                    && (sameLine == null || VersionHelper.compare(candidate, sameLine) < 0)) {
                sameLine = candidate;
            }
            if (lowest == null || VersionHelper.compare(candidate, lowest) < 0) {
                lowest = candidate;
            }
        }
        return sameLine != null ? sameLine : lowest;
    }

    private static String majorMinor(String version) {
        String[] parts = version.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : null;
    }

    /** The version without a qualifier such as -SNAPSHOT, to compare with the versions of the advisories. */
    private static String plainVersion(String version) {
        String plain = version.trim();
        int dash = plain.indexOf('-');
        return dash > 0 ? plain.substring(0, dash) : plain;
    }
}
