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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * A vulnerability (a CVE, or a GHSA without one), with the JARs it affects: the version that fixes each, the chain that
 * brings each in, and whether the routes use it.
 */
public final class CveFinding {

    private final String canonicalId;
    private final String severity;
    private final String cvssVector;
    private final String summary;
    private final String details;
    private final String published;
    private final List<String> affectedGavs;
    private final Map<String, String> gavVia;
    private final List<String> aliases;
    private final List<String> fixedVersions;
    private final Map<String, String> fixFor = new HashMap<>();
    private final Map<String, CveUsage> usedBy = new HashMap<>();
    private final Set<String> declared = new HashSet<>();
    private CamelAdvisory camelAdvisory;
    private Boolean usedByRoutes;

    public CveFinding(String canonicalId, String severity, String cvssVector, String summary, String details,
                      String published, List<String> affectedGavs, Map<String, String> gavVia,
                      List<String> aliases, List<String> fixedVersions) {
        this.canonicalId = canonicalId;
        this.severity = severity;
        this.cvssVector = cvssVector;
        this.summary = summary;
        this.details = details;
        this.published = published;
        this.affectedGavs = affectedGavs;
        this.gavVia = gavVia;
        this.aliases = aliases;
        this.fixedVersions = fixedVersions;
    }

    /** The CVE id, or the id of the record when it has no CVE alias. */
    public String canonicalId() {
        return canonicalId;
    }

    /** CRITICAL, HIGH, MEDIUM, LOW or UNKNOWN. */
    public String severity() {
        return severity;
    }

    public String cvssVector() {
        return cvssVector;
    }

    public String summary() {
        return summary;
    }

    public String details() {
        return details;
    }

    public String published() {
        return published;
    }

    /** The JARs it affects, as {@code groupId:artifactId:version}. */
    public List<String> affectedGavs() {
        return affectedGavs;
    }

    /** The dependency chain that brings each JAR in, as it is shown (camel-kafka > kafka-clients). */
    public Map<String, String> gavVia() {
        return gavVia;
    }

    public List<String> aliases() {
        return aliases;
    }

    /** The fixed versions of every package and range of the record. */
    public List<String> fixedVersions() {
        return fixedVersions;
    }

    /** The version that fixes each JAR: the end of the range its version is in. */
    public Map<String, String> fixFor() {
        return fixFor;
    }

    /** The component that brings each JAR in, and whether the routes use it. */
    public Map<String, CveUsage> usedBy() {
        return usedBy;
    }

    /** The JARs the project declares itself. */
    public Set<String> declared() {
        return declared;
    }

    /** The Apache Camel advisory of the CVE; null when it has none. */
    public CamelAdvisory camelAdvisory() {
        return camelAdvisory;
    }

    public void setCamelAdvisory(CamelAdvisory camelAdvisory) {
        this.camelAdvisory = camelAdvisory;
    }

    /** Whether the routes use a JAR of it: null when no component brings one in. */
    public Boolean usedByRoutes() {
        return usedByRoutes;
    }

    public void setUsedByRoutes(Boolean usedByRoutes) {
        this.usedByRoutes = usedByRoutes;
    }

    /** The CVSS 3 base score; null when there is no CVSS 3 vector. */
    public Double score() {
        return CvssScore.baseScore(cvssVector);
    }

    public String firstGav() {
        return affectedGavs.isEmpty() ? null : affectedGavs.get(0);
    }

    /** The chain of the first JAR; empty when it has none. */
    public String firstVia() {
        String gav = firstGav();
        String via = gav != null ? gavVia.get(gav) : null;
        return via != null ? via : "";
    }

    /** Whether the term (in lower case) is in its id, severity, summary, aliases, JARs or chains. */
    public boolean matches(String term) {
        if (contains(canonicalId, term) || contains(severity, term) || contains(summary, term)) {
            return true;
        }
        for (String s : aliases) {
            if (contains(s, term)) {
                return true;
            }
        }
        for (String gav : affectedGavs) {
            if (contains(gav, term) || contains(gavVia.get(gav), term)) {
                return true;
            }
        }
        return false;
    }

    public JsonObject toJson() {
        JsonObject row = new JsonObject();
        row.put("cveId", canonicalId);
        row.put("severity", severity);
        Double score = score();
        if (score != null) {
            row.put("score", score);
        }
        row.put("via", firstVia());
        row.put("summary", summary);
        row.put("published", published);
        JsonArray arts = new JsonArray();
        for (String gav : affectedGavs) {
            JsonObject art = new JsonObject();
            art.put("artifact", gav);
            art.put("fixedIn", fixFor.get(gav));
            art.put("via", gavVia.get(gav));
            art.put("declared", declared.contains(gav));
            CveUsage usage = usedBy.get(gav);
            if (usage != null) {
                art.put("component", usage.component());
                art.put("usedByRoutes", usage.used());
            }
            arts.add(art);
        }
        row.put("affectedArtifacts", arts);
        if (usedByRoutes != null) {
            row.put("usedByRoutes", usedByRoutes);
        }
        if (!fixedVersions.isEmpty()) {
            row.put("fixedVersions", new JsonArray(fixedVersions));
        }
        if (camelAdvisory != null) {
            JsonObject adv = new JsonObject();
            adv.put("upgradeTo", camelAdvisory.upgradeTo());
            adv.put("fixed", camelAdvisory.fixed());
            adv.put("mitigation", camelAdvisory.mitigation());
            adv.put("url", camelAdvisory.url());
            row.put("camelAdvisory", adv);
        }
        row.put("aliases", new JsonArray(aliases));
        return row;
    }

    static boolean contains(String text, String lowerTerm) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(lowerTerm);
    }
}
