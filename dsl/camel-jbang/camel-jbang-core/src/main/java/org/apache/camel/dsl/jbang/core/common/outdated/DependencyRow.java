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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * One dependency of an integration, with how its version relates to the one Camel brings, the versions loaded, and the
 * newer versions available.
 */
public final class DependencyRow {

    /** How the version of a dependency relates to Camel. */
    public enum Status {
        /** Loaded in more than one version, or in another version than the one declared. */
        CONFLICT,
        /** A Camel JAR of another Camel version than the one of the integration. */
        MIXED,
        /** Declared by the project in another version than the one the Camel dependencies bring. */
        OVERRIDE,
        /** The version the Camel dependencies bring. */
        MANAGED,
        /** A Camel JAR of the Camel version of the integration. */
        CAMEL,
        /** Camel does not bring this library. */
        NONE;

        /** The text of the STATUS column, empty for NONE. */
        public String label() {
            return this == NONE ? "" : name().toLowerCase(Locale.ROOT);
        }

        /** Whether the status is one to act on. */
        public boolean attention() {
            return this == CONFLICT || this == MIXED || this == OVERRIDE;
        }
    }

    private final String groupId;
    private final String artifactId;
    private final String version;
    private final boolean transitive;
    private final String via;
    private Status status = Status.NONE;
    private String camelVersion;
    private final List<String> loadedVersions = new ArrayList<>();
    private String newer;
    private String latest;

    public DependencyRow(String groupId, String artifactId, String version, boolean transitive, String via) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.transitive = transitive;
        this.via = via;
    }

    public String groupId() {
        return groupId;
    }

    public String artifactId() {
        return artifactId;
    }

    public String version() {
        return version;
    }

    /** {@code groupId:artifactId}. */
    public String ga() {
        return groupId + ":" + artifactId;
    }

    /** {@code groupId:artifactId:version}, or {@code groupId:artifactId} without a version. */
    public String gav() {
        return version != null ? ga() + ":" + version : ga();
    }

    public boolean transitive() {
        return transitive;
    }

    /** The dependency of the project that brings a transitive one in, null for a declared one. */
    public String via() {
        return via;
    }

    public boolean camel() {
        return Versions.isCamel(groupId);
    }

    public Status status() {
        return status;
    }

    void setStatus(Status status) {
        this.status = status;
    }

    /** The version the Camel dependencies bring of this library; null when they do not bring it. */
    public String camelVersion() {
        return camelVersion;
    }

    void setCamelVersion(String camelVersion) {
        this.camelVersion = camelVersion;
    }

    /** The versions of this artifact on the classpath of the running integration; empty when not known. */
    public List<String> loadedVersions() {
        return Collections.unmodifiableList(loadedVersions);
    }

    void setLoadedVersions(Collection<String> versions) {
        loadedVersions.clear();
        if (versions != null) {
            loadedVersions.addAll(new TreeSet<>(versions));
        }
    }

    /** The latest release of the same major.minor line, when newer; null when there is none or not checked. */
    public String newer() {
        return newer;
    }

    /** The latest release, when newer; null when there is none or not checked. */
    public String latest() {
        return latest;
    }

    /** Whether the latest release is another major version. */
    public boolean latestIsMajor() {
        return latest != null && Versions.majorStep(version, latest);
    }

    void setNewer(String newer, String latest) {
        this.newer = newer;
        this.latest = latest;
    }

    /**
     * Whether something should be done about the dependency: it conflicts, overrides Camel's version, or has a newer
     * release (with {@code anyNewer}), or a newer release of the same line (without).
     */
    public boolean outdated(boolean anyNewer) {
        return status.attention() || newer != null || (anyNewer && latest != null);
    }

    /** Whether the row matches a filter term (lower case): in the coordinates, the status or the versions. */
    public boolean matches(String term) {
        return gav().toLowerCase(Locale.ROOT).contains(term)
                || status.label().contains(term)
                || (via != null && via.toLowerCase(Locale.ROOT).contains(term));
    }

    /** What the STATUS says, in words, for the details or an AI prompt. */
    public String explain() {
        return switch (status) {
            case CONFLICT -> "loaded in " + String.join(" and ", loadedVersions)
                             + (version != null && !loadedVersions.contains(version) ? ", declared " + version : "");
            case MIXED -> "a Camel JAR of version " + version + ", the integration uses Camel " + camelVersion;
            case OVERRIDE -> "declared as " + version + ", the Camel dependencies bring " + camelVersion;
            case MANAGED -> "the version the Camel dependencies bring";
            case CAMEL -> sameVersionAsCamel(groupId)
                    ? "part of Camel " + version
                    : "an Apache Camel JAR with its own versions (" + version + ")";
            case NONE -> "Camel does not bring this library";
        };
    }

    /** Whether the JARs of the group have the Camel version: camel-quarkus and the kamelets have their own. */
    static boolean sameVersionAsCamel(String groupId) {
        return "org.apache.camel".equals(groupId) || "org.apache.camel.springboot".equals(groupId);
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("groupId", groupId);
        o.put("artifactId", artifactId);
        CamelRelease.putIfSet(o, "version", version);
        if (transitive) {
            o.put("transitive", true);
            CamelRelease.putIfSet(o, "via", via);
        }
        if (status != Status.NONE) {
            o.put("status", status.label());
        }
        CamelRelease.putIfSet(o, "camelVersion", camelVersion);
        if (!loadedVersions.isEmpty()) {
            o.put("loadedVersions", new JsonArray(loadedVersions));
        }
        CamelRelease.putIfSet(o, "newer", newer);
        CamelRelease.putIfSet(o, "latest", latest);
        if (latestIsMajor()) {
            o.put("latestIsMajor", true);
        }
        return o;
    }

    @Override
    public String toString() {
        return gav() + (status != Status.NONE ? " [" + status.label() + "]" : "");
    }
}
