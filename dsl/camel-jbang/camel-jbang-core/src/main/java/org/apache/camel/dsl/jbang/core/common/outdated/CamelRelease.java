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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.tooling.model.ReleaseModel;
import org.apache.camel.util.json.JsonObject;

/**
 * Where the Camel version of an integration stands, from the known releases of the catalog: LTS or not, its end of
 * life, the latest patch release of the same line, the latest LTS release and the latest release.
 *
 * @param version    the Camel version in use
 * @param released   whether the version is a known release (a SNAPSHOT is not)
 * @param lts        whether the line of the version is an LTS line
 * @param date       the release date of the version, null when not known
 * @param eol        the end of life of the line, null when not known
 * @param eolPassed  whether the end of life is in the past
 * @param jdk        the JDK versions of the line, such as {@code 17,21}
 * @param newerPatch the latest release of the same line, when newer than the version in use
 * @param latestLts  the latest LTS release, when newer than the version in use
 * @param latest     the latest release, when newer than the version in use
 */
public record CamelRelease(
        String version, boolean released, boolean lts, String date, String eol, boolean eolPassed, String jdk,
        String newerPatch, String latestLts, String latest) {

    /** Where the given Camel version stands among the releases; null when the version is not known. */
    public static CamelRelease of(String version, List<ReleaseModel> releases, LocalDate today) {
        if (version == null || version.isBlank()) {
            return null;
        }
        List<ReleaseModel> all = releases != null ? releases : List.of();
        String line = Versions.lineOf(version);
        ReleaseModel exact = null;
        ReleaseModel lineLatest = null;
        String latestLts = null;
        String latest = null;
        List<String> lineVersions = new ArrayList<>();
        for (ReleaseModel r : all) {
            String v = r.getVersion();
            if (v == null || !Versions.isStable(v)) {
                continue;
            }
            if (v.equals(version)) {
                exact = r;
            }
            if (line != null && line.equals(Versions.lineOf(v))) {
                lineVersions.add(v);
                if (lineLatest == null || Versions.compare(v, lineLatest.getVersion()) > 0) {
                    lineLatest = r;
                }
            }
            if ("lts".equalsIgnoreCase(r.getKind()) && (latestLts == null || Versions.compare(v, latestLts) > 0)) {
                latestLts = v;
            }
            if (latest == null || Versions.compare(v, latest) > 0) {
                latest = v;
            }
        }
        ReleaseModel ref = exact != null ? exact : lineLatest;
        boolean lts = ref != null && "lts".equalsIgnoreCase(ref.getKind());
        // the end of life of the line is the one of its latest release
        String eol = lineLatest != null && lineLatest.getEol() != null ? lineLatest.getEol()
                : ref != null ? ref.getEol() : null;
        boolean eolPassed = false;
        if (eol != null && today != null) {
            try {
                eolPassed = LocalDate.parse(eol).isBefore(today);
            } catch (Exception e) {
                // not a date: not known
            }
        }
        return new CamelRelease(
                version, exact != null, lts, exact != null ? exact.getDate() : null, eol, eolPassed,
                ref != null ? ref.getJdk() : null,
                Versions.newerPatch(version, lineVersions),
                newer(latestLts, version), newer(latest, version));
    }

    private static String newer(String candidate, String version) {
        return candidate != null && Versions.compare(candidate, version) > 0 ? candidate : null;
    }

    /**
     * One line, such as {@code Camel 4.18.1 LTS, end of life 2027-03-14 · newer on this line: 4.18.3 · latest LTS:
     * 4.22.2 · latest: 4.24.0}.
     */
    public String summary() {
        StringBuilder sb = new StringBuilder("Camel ").append(version);
        if (!released) {
            sb.append(Versions.isStable(version) ? " (not a known release)" : " (not released)");
        }
        if (lts) {
            sb.append(" LTS");
        }
        if (eol != null) {
            sb.append(eolPassed ? ", end of life since " : ", end of life ").append(eol);
        }
        if (newerPatch != null) {
            sb.append(" · newer on this line: ").append(newerPatch);
        }
        if (latestLts != null && !latestLts.equals(newerPatch)) {
            sb.append(" · latest LTS: ").append(latestLts);
        }
        if (latest != null && !latest.equals(latestLts) && !latest.equals(newerPatch)) {
            sb.append(" · latest: ").append(latest);
        }
        if (newerPatch == null && latestLts == null && latest == null && released) {
            sb.append(" · the latest release");
        }
        return sb.toString();
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.put("version", version);
        o.put("released", released);
        o.put("lts", lts);
        putIfSet(o, "date", date);
        putIfSet(o, "eol", eol);
        if (eol != null) {
            o.put("eolPassed", eolPassed);
        }
        putIfSet(o, "jdk", jdk);
        putIfSet(o, "newerPatch", newerPatch);
        putIfSet(o, "latestLts", latestLts);
        putIfSet(o, "latest", latest);
        return o;
    }

    static void putIfSet(JsonObject o, String key, Object value) {
        if (value != null) {
            o.put(key, value);
        }
    }
}
