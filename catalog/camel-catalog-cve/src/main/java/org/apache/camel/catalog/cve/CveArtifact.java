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
package org.apache.camel.catalog.cve;

import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * A vulnerable JAR with its CVEs: what to upgrade. It has the worst severity and highest score of its CVEs, and the
 * version that fixes all of them.
 */
public final class CveArtifact {

    private final String gav;
    private final List<CveFinding> cves;
    String severity = "UNKNOWN";
    Double score;
    String fix;
    boolean camelRelease;
    String via;
    boolean declared;
    CveUsage usage;

    CveArtifact(String gav, List<CveFinding> cves) {
        this.gav = gav;
        this.cves = cves;
    }

    /** {@code groupId:artifactId:version}. */
    public String gav() {
        return gav;
    }

    /** Its CVEs, the worst first. */
    public List<CveFinding> cves() {
        return cves;
    }

    public String severity() {
        return severity;
    }

    public Double score() {
        return score;
    }

    /** The version that fixes all its CVEs; null when one has no known fix. */
    public String fix() {
        return fix;
    }

    /** Whether the fix is a Camel release: a Camel JAR with Camel advisories only. */
    public boolean camelRelease() {
        return camelRelease;
    }

    /** The dependency chain that brings it in; null when the project declares it, or it is not known. */
    public String via() {
        return via;
    }

    /** Whether the project declares it itself. */
    public boolean declared() {
        return declared;
    }

    /** The component that brings it in, and whether the routes use it; null when no component does. */
    public CveUsage usage() {
        return usage;
    }

    /** Whether the term (in lower case) is in its coordinates, chain, severity, or the id or summary of a CVE. */
    public boolean matches(String term) {
        if (CveFinding.contains(gav, term) || CveFinding.contains(via, term) || CveFinding.contains(severity, term)) {
            return true;
        }
        for (CveFinding g : cves) {
            if (CveFinding.contains(g.canonicalId(), term) || CveFinding.contains(g.summary(), term)) {
                return true;
            }
        }
        return false;
    }

    public JsonObject toJson() {
        JsonObject row = new JsonObject();
        row.put("artifact", gav);
        row.put("severity", severity);
        if (score != null) {
            row.put("score", score);
        }
        row.put("cveCount", cves.size());
        row.put("fixesAll", fix);
        row.put("fixIsCamelRelease", camelRelease);
        row.put("via", via);
        row.put("declared", declared);
        if (usage != null) {
            row.put("component", usage.component());
            row.put("usedByRoutes", usage.used());
        }
        row.put("cves", new JsonArray(cves.stream().map(CveFinding::canonicalId).toList()));
        return row;
    }
}
