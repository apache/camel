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
package org.apache.camel.dsl.jbang.core.common;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.tooling.maven.MavenArtifactDescriptor;
import org.apache.camel.tooling.maven.MavenGav;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.eclipse.aether.version.VersionConstraint;
import org.eclipse.aether.version.VersionRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Camel versions a release of a plugin supports, from its resolved POM:
 * <ol>
 * <li>the property {@value #CAMEL_VERSIONS_PROPERTY}, a Maven version range such as {@code [4.23.0,4.24.0)}
 * (declared)</li>
 * <li>otherwise the version of its {@code org.apache.camel:camel-jbang-core} dependency, as its minor line:
 * {@code 4.22.2} supports {@code [4.22.0,4.23.0)} (inferred)</li>
 * <li>otherwise nothing is known (unknown)</li>
 * </ol>
 */
public final class PluginCompatibility {

    /**
     * The property a plugin declares in its POM with the Camel versions it supports, as a Maven version range.
     */
    public static final String CAMEL_VERSIONS_PROPERTY = "camel.jbang.plugin.camel-versions";

    private static final Logger LOG = LoggerFactory.getLogger(PluginCompatibility.class);

    private static final GenericVersionScheme VERSION_SCHEME = new GenericVersionScheme();
    private static final Pattern MINOR_LINE = Pattern.compile("^(\\d{1,9})\\.(\\d{1,9})(?:\\.\\d+)*(?:[-.].*)?$");

    public enum Source {
        DECLARED,
        INFERRED,
        UNKNOWN;

        /**
         * The name stored in the plugin configuration, such as {@code declared}.
         */
        public String label() {
            return name().toLowerCase();
        }
    }

    /**
     * What is known about a plugin release.
     *
     * @param source   where the range comes from
     * @param range    the supported Camel versions as a Maven version range, or null when unknown
     * @param builtFor the version of camel-jbang-core the release is built with, or null
     */
    public record Compat(Source source, String range, String builtFor) {

        /**
         * Describes the Camel versions of the release, such as {@code built for Camel 4.22.2}.
         */
        public String describe() {
            if (builtFor != null) {
                return "built for Camel " + builtFor;
            }
            return range != null ? "built for Camel " + range : "built for an unknown Camel version";
        }
    }

    private PluginCompatibility() {
    }

    /**
     * What the resolved POM of a plugin release tells about the Camel versions it supports.
     */
    public static Compat of(MavenArtifactDescriptor descriptor) {
        MavenGav core = descriptor.findDependency("org.apache.camel", "camel-jbang-core");
        String builtFor = core != null ? core.getVersion() : null;

        String declared = descriptor.properties().get(CAMEL_VERSIONS_PROPERTY);
        if (declared != null && !declared.isBlank()) {
            String range = declared.trim();
            if (isValidRange(range)) {
                return new Compat(Source.DECLARED, range, builtFor);
            }
            LOG.debug("Ignoring invalid {} in {}: {}", CAMEL_VERSIONS_PROPERTY, descriptor.gav(), declared);
            return new Compat(Source.UNKNOWN, null, builtFor);
        }

        String inferred = minorLineRange(builtFor);
        if (inferred != null) {
            return new Compat(Source.INFERRED, inferred, builtFor);
        }
        return new Compat(Source.UNKNOWN, null, builtFor);
    }

    /**
     * Whether the range includes the given Camel CLI version. A SNAPSHOT version is compared without -SNAPSHOT.
     *
     * @return true if included, false if not, or when the range or the version is invalid
     */
    public static boolean accepts(String range, String cliVersion) {
        Version cli = parseCliVersion(cliVersion);
        if (range == null || cli == null) {
            return false;
        }
        try {
            VersionConstraint constraint = VERSION_SCHEME.parseVersionConstraint(range);
            return constraint.containsVersion(cli);
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }

    /**
     * Whether the whole range is below the given Camel CLI version, that is, the range is for older Camel versions
     * only. A SNAPSHOT version is compared without -SNAPSHOT.
     *
     * @return true if the range has an upper bound below the version, false if not, or when the range or the version is
     *         invalid
     */
    public static boolean isOlder(String range, String cliVersion) {
        VersionRange.Bound upper = upperBound(range);
        Version cli = parseCliVersion(cliVersion);
        if (upper == null || cli == null) {
            return false;
        }
        int cmp = cli.compareTo(upper.getVersion());
        return cmp > 0 || cmp == 0 && !upper.isInclusive();
    }

    /**
     * Compares two ranges by their upper bound: a range for older Camel versions comes first. A range without upper
     * bound comes last.
     */
    public static int compareUpperBounds(String range1, String range2) {
        VersionRange.Bound b1 = upperBound(range1);
        VersionRange.Bound b2 = upperBound(range2);
        if (b1 == null || b2 == null) {
            return b1 == null ? (b2 == null ? 0 : 1) : -1;
        }
        int cmp = b1.getVersion().compareTo(b2.getVersion());
        if (cmp == 0 && b1.isInclusive() != b2.isInclusive()) {
            return b1.isInclusive() ? 1 : -1;
        }
        return cmp;
    }

    private static VersionRange.Bound upperBound(String range) {
        if (range == null) {
            return null;
        }
        try {
            VersionConstraint constraint = VERSION_SCHEME.parseVersionConstraint(range);
            VersionRange r = constraint.getRange();
            return r != null ? r.getUpperBound() : null;
        } catch (InvalidVersionSpecificationException e) {
            return null;
        }
    }

    private static Version parseCliVersion(String cliVersion) {
        if (cliVersion == null) {
            return null;
        }
        String v = cliVersion.endsWith("-SNAPSHOT")
                ? cliVersion.substring(0, cliVersion.length() - "-SNAPSHOT".length()) : cliVersion;
        try {
            return VERSION_SCHEME.parseVersion(v);
        } catch (InvalidVersionSpecificationException e) {
            return null;
        }
    }

    /**
     * The minor line of a version, such as {@code 4.22} for {@code 4.22.2}, or null.
     */
    public static String minorLine(String version) {
        Matcher m = version != null ? MINOR_LINE.matcher(version) : null;
        return m != null && m.matches() ? m.group(1) + "." + m.group(2) : null;
    }

    /**
     * The range of the minor line of a version, such as {@code [4.22.0,4.23.0)} for {@code 4.22.2}, or null.
     */
    static String minorLineRange(String version) {
        Matcher m = version != null ? MINOR_LINE.matcher(version) : null;
        if (m == null || !m.matches()) {
            return null;
        }
        String major = m.group(1);
        int minor = Integer.parseInt(m.group(2));
        return "[" + major + "." + minor + ".0," + major + "." + (minor + 1) + ".0)";
    }

    private static boolean isValidRange(String range) {
        try {
            VersionConstraint constraint = VERSION_SCHEME.parseVersionConstraint(range);
            // a single version is a recommendation in Maven, not a range
            return constraint.getRange() != null;
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }
}
