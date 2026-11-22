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
package org.apache.camel.dsl.jbang.core.common.outdated;

import java.util.Collection;
import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Compares Maven versions, and picks the newer ones of a list of available versions: the latest patch of the same
 * major.minor line, and the latest release.
 */
public final class Versions {

    // alpha, beta, milestone, release candidate, snapshot and the like: not offered as an upgrade
    private static final Pattern UNSTABLE = Pattern.compile(
            "(?i)(.*(snapshot|alpha|beta|preview|milestone).*"
                                                            + "|.*[.\\-\\d](rc|cr|ea|dev)\\d*([.\\-].*)?"
                                                            + "|.*[.\\-]m\\d+([.\\-].*)?)");

    private Versions() {
    }

    /** Compares two versions as Maven does. */
    public static int compare(String a, String b) {
        return new ComparableVersion(a).compareTo(new ComparableVersion(b));
    }

    /** Whether a version is a release, not a snapshot, alpha, beta, milestone or release candidate. */
    public static boolean isStable(String version) {
        return version != null && !UNSTABLE.matcher(version).matches();
    }

    /** The major.minor line of a version, such as {@code 2.13} of {@code 2.13.4.1}; null when it has no number. */
    public static String lineOf(String version) {
        if (version == null) {
            return null;
        }
        String[] parts = version.split("[.\\-]");
        if (parts.length == 0 || !isNumber(parts[0])) {
            return null;
        }
        if (parts.length > 1 && isNumber(parts[1])) {
            return parts[0] + "." + parts[1];
        }
        return parts[0];
    }

    /** The major version, such as {@code 2} of {@code 2.13.4}; null when it has no number. */
    public static String majorOf(String version) {
        String line = lineOf(version);
        if (line == null) {
            return null;
        }
        int dot = line.indexOf('.');
        return dot > 0 ? line.substring(0, dot) : line;
    }

    /** Whether the two versions have another major version. */
    public static boolean majorStep(String from, String to) {
        String a = majorOf(from);
        String b = majorOf(to);
        return a != null && b != null && !a.equals(b);
    }

    /**
     * The latest release of the same major.minor line that is newer than the current version, such as 2.13.5 for
     * 2.13.2; null when there is none.
     */
    public static String newerPatch(String current, Collection<String> available) {
        String line = lineOf(current);
        if (line == null || available == null) {
            return null;
        }
        String best = null;
        for (String v : available) {
            if (candidate(current, v) && line.equals(lineOf(v)) && (best == null || compare(v, best) > 0)) {
                best = v;
            }
        }
        return best;
    }

    /** The latest release that is newer than the current version; null when there is none. */
    public static String latest(String current, Collection<String> available) {
        if (current == null || available == null) {
            return null;
        }
        String best = null;
        for (String v : available) {
            if (candidate(current, v) && (best == null || compare(v, best) > 0)) {
                best = v;
            }
        }
        return best;
    }

    /**
     * Whether a version is an upgrade of the current one: a newer release with the same qualifier, such as
     * {@code 4.2.21.Final} for {@code 4.2.20.Final} and {@code 33.5.0-jre} for {@code 33.4.0-jre}, but not
     * {@code 1.18.17-jdk5} for {@code 1.18.16}. A snapshot, milestone or release candidate gets the releases too.
     */
    static boolean candidate(String current, String version) {
        if (!isStable(version) || compare(version, current) <= 0) {
            return false;
        }
        String q = qualifierOf(version);
        return q.equals(qualifierOf(current)) || (!isStable(current) && q.isEmpty());
    }

    /** What follows the numbers of a version, in lower case, such as {@code final} of {@code 4.2.20.Final}. */
    static String qualifierOf(String version) {
        String[] parts = version.split("[.\\-]");
        int i = 0;
        while (i < parts.length && isNumber(parts[i])) {
            i++;
        }
        StringBuilder sb = new StringBuilder();
        for (int j = i; j < parts.length; j++) {
            if (j > i) {
                sb.append('-');
            }
            sb.append(parts[j].toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    /** Whether a Camel artifact: the groupId is org.apache.camel or one of its subgroups (springboot, quarkus...). */
    public static boolean isCamel(String groupId) {
        return groupId != null
                && (groupId.equals("org.apache.camel") || groupId.startsWith("org.apache.camel."));
    }

    private static boolean isNumber(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}
