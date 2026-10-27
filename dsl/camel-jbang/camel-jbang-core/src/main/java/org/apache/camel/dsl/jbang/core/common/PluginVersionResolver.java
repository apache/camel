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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Compat;
import org.apache.camel.dsl.jbang.core.common.PluginCompatibility.Source;
import org.apache.camel.main.download.DependencyDownloader;
import org.apache.camel.tooling.maven.MavenArtifactDescriptor;
import org.eclipse.aether.util.version.GenericVersionScheme;
import org.eclipse.aether.version.InvalidVersionSpecificationException;
import org.eclipse.aether.version.Version;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the newest release of a plugin that supports the version of the Camel CLI, from the resolved POMs of its
 * releases (see {@link PluginCompatibility}).
 */
public final class PluginVersionResolver {

    /**
     * The most releases whose POM is read, newest first.
     */
    static final int MAX_DESCRIPTORS = 10;

    private static final Logger LOG = LoggerFactory.getLogger(PluginVersionResolver.class);

    private static final GenericVersionScheme VERSION_SCHEME = new GenericVersionScheme();
    // a release without qualifier, such as 1.6.2 (no SNAPSHOT, RC, or other qualifier)
    private static final Pattern RELEASE = Pattern.compile("^\\d+(\\.\\d+)*$");

    public sealed interface Result permits Selected, NoMatch, Unavailable {
    }

    /**
     * The newest release that supports the Camel CLI.
     */
    public record Selected(String version, Compat compat) implements Result {
    }

    /**
     * No release among the ones read supports the Camel CLI.
     *
     * @param newest        what is known about the newest release
     * @param newestVersion the newest release
     * @param newestPerLine the newest release per Camel line (such as {@code 4.22 -> 1.6.2}), newest line first, from
     *                      the releases read
     */
    public record NoMatch(Compat newest, String newestVersion, Map<String, String> newestPerLine) implements Result {
    }

    /**
     * The releases or their POMs could not be read, such as when offline.
     */
    public record Unavailable(String reason) implements Result {
    }

    private final DependencyDownloader downloader;

    /**
     * @param downloader a started downloader, configured with the repositories to read the POMs from
     */
    public PluginVersionResolver(DependencyDownloader downloader) {
        this.downloader = downloader;
    }

    /**
     * Selects the newest release of the plugin that supports the Camel CLI.
     *
     * @param groupId    the groupId of the plugin
     * @param artifactId the artifactId of the plugin
     * @param cliVersion the version of the Camel CLI
     * @param repos      optional comma separated list of additional repositories to list the releases from (Maven
     *                   Central is always used)
     */
    public Result resolve(String groupId, String artifactId, String cliVersion, String repos) {
        List<String> versions = new ArrayList<>();
        String failure = null;
        boolean anyListed = false;
        for (String repo : repositories(repos)) {
            try {
                for (String[] v : downloader.resolveAvailableVersions(groupId, artifactId, null, repo)) {
                    if (v[0] != null && !versions.contains(v[0])) {
                        versions.add(v[0]);
                    }
                }
                anyListed = true;
            } catch (Exception e) {
                // skip a repository that cannot be reached, or that does not have the plugin
                LOG.debug("Cannot list versions of {}:{} in {}: {}", groupId, artifactId, repo, e.getMessage(), e);
                failure = e.getMessage();
            }
        }
        if (!anyListed) {
            return new Unavailable(failure != null ? failure : "cannot list the releases of " + groupId + ":" + artifactId);
        }

        List<String> releases = newestFirst(versions);
        if (releases.isEmpty()) {
            return new Unavailable("no release of " + groupId + ":" + artifactId + " found");
        }

        Compat newest = null;
        String newestVersion = null;
        Map<String, String> newestPerLine = new LinkedHashMap<>();
        int read = 0;
        for (String version : releases) {
            if (read >= MAX_DESCRIPTORS) {
                break;
            }
            read++;
            Compat compat;
            try {
                MavenArtifactDescriptor descriptor
                        = downloader.resolveArtifactDescriptor(groupId, artifactId, version, null);
                compat = PluginCompatibility.of(descriptor);
            } catch (Exception e) {
                LOG.debug("Cannot read the POM of {}:{}:{}: {}", groupId, artifactId, version, e.getMessage(), e);
                failure = e.getMessage();
                continue;
            }
            if (newest == null) {
                newest = compat;
                newestVersion = version;
            }
            if (compat.source() != Source.UNKNOWN && PluginCompatibility.accepts(compat.range(), cliVersion)) {
                return new Selected(version, compat);
            }
            String line = compat.builtFor() != null ? PluginCompatibility.minorLine(compat.builtFor()) : compat.range();
            if (line != null) {
                newestPerLine.putIfAbsent(line, version);
            }
        }
        if (newest == null) {
            return new Unavailable(failure != null ? failure : "cannot read the POMs of " + groupId + ":" + artifactId);
        }
        return new NoMatch(newest, newestVersion, newestPerLine);
    }

    // Maven Central (null) first, then the additional repositories
    private static List<String> repositories(String repos) {
        List<String> answer = new ArrayList<>();
        answer.add(null);
        if (repos != null) {
            for (String repo : repos.split("\\s*,\\s*")) {
                if (repo.isBlank()) {
                    continue;
                }
                // a repository can be given as id=url
                int eq = repo.indexOf('=');
                if (eq > 0 && !repo.startsWith("http")) {
                    repo = repo.substring(eq + 1);
                }
                if (!answer.contains(repo)) {
                    answer.add(repo);
                }
            }
        }
        return answer;
    }

    // the releases without qualifier, newest first
    static List<String> newestFirst(List<String> versions) {
        TreeMap<Version, String> sorted = new TreeMap<>();
        for (String v : versions) {
            if (RELEASE.matcher(v).matches()) {
                try {
                    sorted.putIfAbsent(VERSION_SCHEME.parseVersion(v), v);
                } catch (InvalidVersionSpecificationException e) {
                    // not a version
                }
            }
        }
        return new ArrayList<>(sorted.descendingMap().values());
    }
}
