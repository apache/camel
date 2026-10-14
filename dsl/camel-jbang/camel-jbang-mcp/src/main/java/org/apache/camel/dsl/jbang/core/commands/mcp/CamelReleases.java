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
import java.util.List;
import java.util.regex.Pattern;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.ReleaseModel;

/**
 * Reads the Camel releases known to the catalog ({@link CamelCatalog#camelReleases()}, the data behind
 * {@code camel_version_list}): the latest release and the JDK versions a release supports.
 */
final class CamelReleases {

    private static final Pattern RELEASE_VERSION = Pattern.compile("\\d+\\.\\d+\\.\\d+");

    private CamelReleases() {
    }

    /**
     * The newest Camel release in the catalog, or null when the catalog has no release data. A SNAPSHOT catalog version
     * is not a release, so this is what a project should be compared with.
     */
    static String latest(CamelCatalog catalog) {
        String latest = null;
        for (ReleaseModel release : releases(catalog)) {
            if (latest == null || DependencyCheckTools.compareVersions(release.getVersion(), latest) > 0) {
                latest = release.getVersion();
            }
        }
        return latest;
    }

    /**
     * The release that tells the requirements of a version: the release itself, else the newest release of the same
     * major.minor, else (a version newer than every release, such as the one in development) the latest release.
     * Returns null for a version the catalog does not know otherwise.
     */
    static ReleaseModel forVersion(CamelCatalog catalog, String version) {
        if (version == null || version.isBlank()) {
            return null;
        }
        String plain = version.trim().replace("-SNAPSHOT", "");
        String[] parts = plain.split("\\.");
        String minor = parts.length >= 2 ? parts[0] + "." + parts[1] + "." : null;
        ReleaseModel exact = null;
        ReleaseModel sameMinor = null;
        ReleaseModel newest = null;
        for (ReleaseModel release : releases(catalog)) {
            String v = release.getVersion();
            if (v.equals(plain)) {
                exact = release;
            }
            if (minor != null && v.startsWith(minor)
                    && (sameMinor == null || DependencyCheckTools.compareVersions(v, sameMinor.getVersion()) > 0)) {
                sameMinor = release;
            }
            if (newest == null || DependencyCheckTools.compareVersions(v, newest.getVersion()) > 0) {
                newest = release;
            }
        }
        if (exact != null) {
            return exact;
        }
        if (sameMinor != null) {
            return sameMinor;
        }
        if (newest != null && DependencyCheckTools.compareVersions(plain, newest.getVersion()) > 0) {
            return newest;
        }
        return null;
    }

    /** The JDK versions a release supports, oldest first (the catalog writes the old ones as 1.x). */
    static List<Integer> jdks(ReleaseModel release) {
        List<Integer> answer = new ArrayList<>();
        if (release == null || release.getJdk() == null) {
            return answer;
        }
        for (String jdk : release.getJdk().split(",")) {
            int v = javaVersion(jdk);
            if (v > 0) {
                answer.add(v);
            }
        }
        answer.sort(Integer::compare);
        return answer;
    }

    /** A Java version as its feature number: 1.8 and 8 are 8, 21.0.2 is 21; 0 when it cannot be read. */
    static int javaVersion(String version) {
        if (version == null || version.isBlank()) {
            return 0;
        }
        try {
            String v = version.trim();
            if (v.startsWith("1.")) {
                v = v.substring(2);
            }
            return Integer.parseInt(v.split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static List<ReleaseModel> releases(CamelCatalog catalog) {
        List<ReleaseModel> answer = new ArrayList<>();
        List<ReleaseModel> all = catalog.camelReleases();
        if (all != null) {
            for (ReleaseModel release : all) {
                if (release.getVersion() != null && RELEASE_VERSION.matcher(release.getVersion()).matches()) {
                    answer.add(release);
                }
            }
        }
        return answer;
    }
}
