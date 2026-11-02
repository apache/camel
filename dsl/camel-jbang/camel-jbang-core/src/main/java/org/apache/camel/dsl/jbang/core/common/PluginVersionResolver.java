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
 * <p>
 * The releases are grouped by release line of the plugin (its major and minor version, such as {@code 1.6} for
 * {@code 1.6.0}, {@code 1.6.1} and {@code 1.6.2}), and only the POM of the newest release of each line is read, newest
 * line first, so a release for an older Camel version is found with few reads.
 */
public final class PluginVersionResolver {

    /**
     * The most POMs of the plugin that are read, from the newest release of each release line, newest line first. When
     * the POM of the newest release of a line cannot be read, the next release of the line is read.
     */
    static final int MAX_RELEASE_LINES = 10;

    private static final Logger LOG = LoggerFactory.getLogger(PluginVersionResolver.class);

    private static final GenericVersionScheme VERSION_SCHEME = new GenericVersionScheme();
    // a release without qualifier, such as 1.6.2 (no SNAPSHOT, RC, or other qualifier)
    private static final Pattern RELEASE = Pattern.compile("^\\d+(\\.\\d+)*$");

    public sealed interface Result permits Selected, Older, NoMatch, Unavailable {
    }

    /**
     * The newest release that supports the Camel CLI.
     */
    public record Selected(String version, Compat compat) implements Result {
    }

    /**
     * No release read supports the Camel CLI, which is between the Camel versions of the releases read: the newest
     * release for the nearest older Camel version.
     *
     * @param version       the release
     * @param compat        what is known about the release
     * @param newestPerLine the newest release per Camel line (such as {@code 4.22 -> 1.6.2}), newest release first,
     *                      from the releases read
     */
    public record Older(String version, Compat compat, Map<String, String> newestPerLine) implements Result {
    }

    /**
     * Why no release can be selected.
     */
    public enum Reason {
        /**
         * The Camel CLI is newer than the Camel versions of all the releases read.
         */
        CLI_NEWER,
        /**
         * The Camel CLI is older than the Camel versions of all the releases read.
         */
        CLI_OLDER,
        /**
         * No release read tells which Camel versions it supports.
         */
        UNKNOWN
    }

    /**
     * No release can be selected.
     *
     * @param reason        why
     * @param newest        what is known about the newest release
     * @param newestVersion the newest release
     * @param newestPerLine the newest release per Camel line (such as {@code 4.22 -> 1.6.2}), newest release first,
     *                      from the releases read
     */
    public record NoMatch(Reason reason, Compat newest, String newestVersion, Map<String, String> newestPerLine)
            implements
                Result {
    }

    /**
     * The releases or their POMs could not be read, such as when offline.
     */
    public record Unavailable(String reason) implements Result {
    }

    private record Read(String version, Compat compat) {
    }

    private final DependencyDownloader downloader;

    /**
     * @param downloader a started downloader, configured with the repositories to read the releases and POMs from
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
     */
    public Result resolve(String groupId, String artifactId, String cliVersion) {
        List<String> versions;
        try {
            // the releases change, so check the repositories again (the POM of a release does not change)
            versions = downloader.resolveAvailableVersions(groupId, artifactId, true);
        } catch (Exception e) {
            LOG.debug("Cannot list versions of {}:{}: {}", groupId, artifactId, e.getMessage(), e);
            return new Unavailable(
                    e.getMessage() != null
                            ? e.getMessage()
                            : "cannot list the releases of " + groupId + ":" + artifactId);
        }

        // the releases of each release line, newest line and newest release first
        Map<String, List<String>> lines = new LinkedHashMap<>();
        for (String version : newestFirst(versions)) {
            lines.computeIfAbsent(releaseLine(version), l -> new ArrayList<>()).add(version);
        }
        if (lines.isEmpty()) {
            return new Unavailable("no release of " + groupId + ":" + artifactId + " found");
        }

        String failure = null;
        List<Read> reads = new ArrayList<>();
        Map<String, String> newestPerLine = new LinkedHashMap<>();
        for (List<String> releases : lines.values()) {
            String version = null;
            Compat compat = null;
            // the newest release of the line, or the next one when its POM cannot be read
            for (String release : releases) {
                if (reads.size() >= MAX_RELEASE_LINES) {
                    break;
                }
                try {
                    MavenArtifactDescriptor descriptor
                            = downloader.resolveArtifactDescriptor(groupId, artifactId, release, null);
                    version = release;
                    compat = PluginCompatibility.of(descriptor);
                    break;
                } catch (Exception e) {
                    LOG.debug("Cannot read the POM of {}:{}:{}: {}", groupId, artifactId, release, e.getMessage(), e);
                    failure = e.getMessage();
                    // counts as a read, so an unreachable repository does not read every release
                    reads.add(new Read(release, null));
                }
            }
            if (compat == null) {
                continue;
            }
            reads.add(new Read(version, compat));
            if (compat.source() != Source.UNKNOWN && PluginCompatibility.accepts(compat.range(), cliVersion)) {
                return new Selected(version, compat);
            }
            String line = compat.builtFor() != null ? PluginCompatibility.minorLine(compat.builtFor()) : compat.range();
            if (line != null) {
                newestPerLine.putIfAbsent(line, version);
            }
        }

        List<Read> read = reads.stream().filter(r -> r.compat() != null).toList();
        if (read.isEmpty()) {
            return new Unavailable(failure != null ? failure : "cannot read the POMs of " + groupId + ":" + artifactId);
        }
        Read newest = read.get(0);
        List<Read> known = read.stream().filter(r -> r.compat().source() != Source.UNKNOWN).toList();
        if (known.isEmpty()) {
            return new NoMatch(Reason.UNKNOWN, newest.compat(), newest.version(), newestPerLine);
        }
        List<Read> older = known.stream().filter(r -> PluginCompatibility.isOlder(r.compat().range(), cliVersion)).toList();
        if (older.isEmpty()) {
            return new NoMatch(Reason.CLI_OLDER, newest.compat(), newest.version(), newestPerLine);
        }
        if (older.size() == known.size()) {
            return new NoMatch(Reason.CLI_NEWER, newest.compat(), newest.version(), newestPerLine);
        }
        // between Camel versions: the newest release for the nearest older one
        Read nearest = older.get(0);
        for (Read r : older) {
            if (PluginCompatibility.compareUpperBounds(r.compat().range(), nearest.compat().range()) > 0) {
                nearest = r;
            }
        }
        return new Older(nearest.version(), nearest.compat(), newestPerLine);
    }

    // the release line of a version: its major and minor version, such as 1.6 for 1.6.2
    static String releaseLine(String version) {
        String[] parts = version.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : version;
    }

    // the releases without qualifier, newest first
    static List<String> newestFirst(List<String> versions) {
        TreeMap<Version, String> sorted = new TreeMap<>();
        for (String v : versions) {
            if (v != null && RELEASE.matcher(v).matches()) {
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
