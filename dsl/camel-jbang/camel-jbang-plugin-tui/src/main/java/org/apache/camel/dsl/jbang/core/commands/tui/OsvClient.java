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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * Lightweight HTTP client for the OSV.dev vulnerability database. Uses the batch query endpoint for efficiency and
 * maintains a global in-memory cache keyed by Maven GAV coordinates so results persist across tab visits and
 * integration switches.
 */
class OsvClient {

    private static final String BATCH_URL = "https://api.osv.dev/v1/querybatch";
    private static final String VULN_URL = "https://api.osv.dev/v1/vulns/";
    private static final ConcurrentHashMap<String, List<Vulnerability>> CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Vulnerability> VULN_DETAIL_CACHE = new ConcurrentHashMap<>();

    // why the last query got no answer from OSV.dev; null when it got one
    private volatile String lastError;

    /**
     * A vulnerability of OSV.dev. {@code fixedVersions} are the fixed versions of every package and range of it, and
     * {@code ranges} the affected ranges per package, to tell which version fixes an installed one
     * ({@link #nearestFix(String, String)}).
     */
    record Vulnerability(String id, String summary, String details, String severity, String cvssVector,
            String published, List<String> aliases, List<String> fixedVersions, List<AffectedRange> ranges) {

        Vulnerability(String id, String summary, String details, String severity, String cvssVector,
                      String published, List<String> aliases, List<String> fixedVersions) {
            this(id, summary, details, severity, cvssVector, published, aliases, fixedVersions, List.of());
        }

        /**
         * The version that fixes the given version of the package ({@code groupId:artifactId}): the end of the range
         * the version is in, else the lowest fixed version above it (of a later line, when the line of the version has
         * no fix). Null when no fixed version above it is known.
         */
        String nearestFix(String packageName, String installed) {
            if (installed == null) {
                return null;
            }
            ComparableVersion version = new ComparableVersion(installed);
            ComparableVersion lowestAbove = null;
            for (AffectedRange r : ranges) {
                if (!r.packageName().equals(packageName) || r.fixed() == null) {
                    continue;
                }
                ComparableVersion fixed = new ComparableVersion(r.fixed());
                if (version.compareTo(fixed) >= 0) {
                    continue;
                }
                if (r.introduced() == null || version.compareTo(new ComparableVersion(r.introduced())) >= 0) {
                    // the range the version is in
                    return r.fixed();
                }
                if (lowestAbove == null || fixed.compareTo(lowestAbove) < 0) {
                    lowestAbove = fixed;
                }
            }
            return lowestAbove != null ? lowestAbove.toString() : null;
        }
    }

    /**
     * An affected range of a package ({@code groupId:artifactId}): from {@code introduced} (null for all earlier
     * versions) to {@code fixed}, or to {@code lastAffected} when no version fixes it.
     */
    record AffectedRange(String packageName, String introduced, String fixed, String lastAffected) {
    }

    /**
     * Query OSV.dev for vulnerabilities in the given JARs. Cached results are returned immediately; only uncached JARs
     * are sent to the API.
     *
     * @return map from GAV string (groupId:artifactId:version) to list of vulnerabilities
     */
    Map<String, List<Vulnerability>> queryBatch(List<DependencyLoader.DepEntry> entries) {
        Map<String, List<Vulnerability>> result = new HashMap<>();
        List<DependencyLoader.DepEntry> uncached = new ArrayList<>();

        for (DependencyLoader.DepEntry entry : entries) {
            if (entry.groupId() == null || entry.version() == null) {
                continue;
            }
            String gav = entry.display();
            List<Vulnerability> cached = CACHE.get(gav);
            if (cached != null) {
                result.put(gav, cached);
            } else {
                uncached.add(entry);
            }
        }

        lastError = null;
        if (uncached.isEmpty()) {
            return result;
        }

        JsonArray queries = new JsonArray();
        for (DependencyLoader.DepEntry entry : uncached) {
            JsonObject pkg = new JsonObject();
            pkg.put("name", entry.groupId() + ":" + entry.artifactId());
            pkg.put("ecosystem", "Maven");

            JsonObject query = new JsonObject();
            query.put("version", entry.version());
            query.put("package", pkg);
            queries.add(query);
        }

        JsonObject requestBody = new JsonObject();
        requestBody.put("queries", queries);

        boolean success = false;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .proxy(TuiSettings.proxySelector())
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BATCH_URL))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toJson()))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                lastError = "OSV.dev answered with HTTP status " + response.statusCode();
            } else {
                success = true;
                JsonObject body = (JsonObject) Jsoner.deserialize(response.body());
                JsonArray results = (JsonArray) body.get("results");
                if (results != null) {
                    // batch response returns abbreviated vulns (id + modified only)
                    // collect vuln IDs per GAV, then fetch full details
                    Map<String, List<String>> gavToVulnIds = new HashMap<>();
                    for (int i = 0; i < results.size() && i < uncached.size(); i++) {
                        DependencyLoader.DepEntry entry = uncached.get(i);
                        String gav = entry.display();
                        JsonObject queryResult = (JsonObject) results.get(i);
                        JsonArray vulns = (JsonArray) queryResult.get("vulns");
                        List<String> ids = new ArrayList<>();
                        if (vulns != null) {
                            for (Object v : vulns) {
                                JsonObject vuln = (JsonObject) v;
                                String id = getString(vuln, "id");
                                if (id != null) {
                                    ids.add(id);
                                }
                            }
                        }
                        gavToVulnIds.put(gav, ids);
                    }

                    // fetch full details for each unique vuln ID
                    fetchVulnDetails(client, gavToVulnIds);

                    // build result from detail cache
                    for (Map.Entry<String, List<String>> e : gavToVulnIds.entrySet()) {
                        String gav = e.getKey();
                        List<Vulnerability> vulns = new ArrayList<>();
                        for (String id : e.getValue()) {
                            Vulnerability v = VULN_DETAIL_CACHE.get(id);
                            if (v != null) {
                                vulns.add(v);
                            }
                        }
                        CACHE.put(gav, vulns);
                        result.put(gav, vulns);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lastError = "The query to OSV.dev was interrupted";
        } catch (Exception e) {
            // network errors: return whatever we have from cache; leave entries uncached for retry
            lastError = "OSV.dev could not be reached: "
                        + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }

        if (success) {
            // cache empty results only for entries the API actually processed
            for (DependencyLoader.DepEntry entry : uncached) {
                String gav = entry.display();
                result.putIfAbsent(gav, Collections.emptyList());
                CACHE.putIfAbsent(gav, Collections.emptyList());
            }
        }

        return result;
    }

    private void fetchVulnDetails(HttpClient client, Map<String, List<String>> gavToVulnIds) {
        // collect unique IDs not already in detail cache
        List<String> toFetch = gavToVulnIds.values().stream()
                .flatMap(List::stream)
                .distinct()
                .filter(id -> !VULN_DETAIL_CACHE.containsKey(id))
                .toList();

        for (String id : toFetch) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(VULN_URL + id))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject vuln = (JsonObject) Jsoner.deserialize(resp.body());
                    Vulnerability v = parseVuln(vuln);
                    if (v != null) {
                        VULN_DETAIL_CACHE.put(id, v);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // skip this vuln on error
            }
        }
    }

    /** Why the last query got no answer from OSV.dev, so its JARs are not checked; null when it got one. */
    String lastError() {
        return lastError;
    }

    void clearCache() {
        CACHE.clear();
        VULN_DETAIL_CACHE.clear();
    }

    void clearCache(List<DependencyLoader.DepEntry> entries) {
        for (DependencyLoader.DepEntry entry : entries) {
            if (entry.groupId() != null) {
                CACHE.remove(entry.display());
            }
        }
    }

    private Vulnerability parseVuln(JsonObject vuln) {
        String id = getString(vuln, "id");
        if (id == null) {
            return null;
        }
        String summary = getString(vuln, "summary");
        String details = getString(vuln, "details");
        String published = getString(vuln, "published");
        String severity = extractSeverity(vuln);
        String cvssVector = extractCvssVector(vuln);
        List<String> aliases = extractAliases(vuln);
        List<String> fixedVersions = extractFixedVersions(vuln);
        List<AffectedRange> ranges = extractRanges(vuln);
        return new Vulnerability(
                id, summary, details, severity, cvssVector, published, aliases, fixedVersions, ranges);
    }

    /** The affected ranges of the Maven packages, from the ordered events (introduced, then fixed or last_affected). */
    static List<AffectedRange> extractRanges(JsonObject vuln) {
        JsonArray affected = (JsonArray) vuln.get("affected");
        if (affected == null) {
            return Collections.emptyList();
        }
        List<AffectedRange> result = new ArrayList<>();
        for (Object a : affected) {
            JsonObject aff = (JsonObject) a;
            JsonObject pkg = (JsonObject) aff.get("package");
            String name = pkg != null ? getString(pkg, "name") : null;
            JsonArray ranges = (JsonArray) aff.get("ranges");
            if (name == null || ranges == null) {
                continue;
            }
            for (Object r : ranges) {
                JsonArray events = (JsonArray) ((JsonObject) r).get("events");
                if (events == null) {
                    continue;
                }
                String introduced = null;
                boolean open = false;
                for (Object e : events) {
                    JsonObject event = (JsonObject) e;
                    if (event.containsKey("introduced")) {
                        String v = getString(event, "introduced");
                        introduced = "0".equals(v) ? null : v;
                        open = true;
                    } else if (event.containsKey("fixed") && open) {
                        result.add(new AffectedRange(name, introduced, getString(event, "fixed"), null));
                        open = false;
                    } else if (event.containsKey("last_affected") && open) {
                        result.add(new AffectedRange(name, introduced, null, getString(event, "last_affected")));
                        open = false;
                    }
                }
                if (open) {
                    // affected from introduced on, with no fix yet
                    result.add(new AffectedRange(name, introduced, null, null));
                }
            }
        }
        return result;
    }

    private List<String> extractFixedVersions(JsonObject vuln) {
        JsonArray affected = (JsonArray) vuln.get("affected");
        if (affected == null) {
            return Collections.emptyList();
        }
        List<String> fixed = new ArrayList<>();
        for (Object a : affected) {
            JsonObject aff = (JsonObject) a;
            JsonArray ranges = (JsonArray) aff.get("ranges");
            if (ranges == null) {
                continue;
            }
            for (Object r : ranges) {
                JsonObject range = (JsonObject) r;
                JsonArray events = (JsonArray) range.get("events");
                if (events == null) {
                    continue;
                }
                for (Object e : events) {
                    JsonObject event = (JsonObject) e;
                    String fixedVer = getString(event, "fixed");
                    if (fixedVer != null && !fixed.contains(fixedVer)) {
                        fixed.add(fixedVer);
                    }
                }
            }
        }
        return fixed;
    }

    /** The CVSS vector: a CVSS 3 one when there is one (it can be scored), else the first. */
    static String extractCvssVector(JsonObject vuln) {
        JsonArray severityArr = (JsonArray) vuln.get("severity");
        if (severityArr == null) {
            return null;
        }
        String first = null;
        for (Object o : severityArr) {
            String score = getString((JsonObject) o, "score");
            if (score != null && score.startsWith("CVSS:3")) {
                return score;
            }
            if (first == null && score != null && score.startsWith("CVSS:")) {
                first = score;
            }
        }
        return first;
    }

    static String extractSeverity(JsonObject vuln) {
        // first try database_specific.severity
        JsonObject dbSpecific = (JsonObject) vuln.get("database_specific");
        if (dbSpecific != null) {
            String sev = getString(dbSpecific, "severity");
            if (sev != null) {
                return sev.toUpperCase();
            }
        }
        // fall back to the CVSS vector: the score of a CVSS 3 vector, else a guess from the vector
        String vector = extractCvssVector(vuln);
        if (vector != null) {
            Double score = CvssScore.baseScore(vector);
            return score != null ? CvssScore.severity(score) : cvssToSeverity(vector);
        }
        return "UNKNOWN";
    }

    static String cvssToSeverity(String cvssVector) {
        if (cvssVector == null) {
            return "UNKNOWN";
        }
        // extract numeric score from CVSS vector if present (e.g., "CVSS:3.1/AV:N/AC:L/...")
        // parse from the vector heuristically
        boolean network = cvssVector.contains("AV:N");
        boolean lowComplexity = cvssVector.contains("AC:L");
        int highCount = 0;
        if (cvssVector.contains("/C:H")) {
            highCount++;
        }
        if (cvssVector.contains("/I:H")) {
            highCount++;
        }
        if (cvssVector.contains("/A:H")) {
            highCount++;
        }

        if (network && lowComplexity && highCount == 3) {
            return "CRITICAL";
        }
        if (network && highCount >= 2) {
            return "HIGH";
        }
        if (highCount >= 1) {
            return "MEDIUM";
        }
        boolean allNone = cvssVector.contains("/C:N") && cvssVector.contains("/I:N") && cvssVector.contains("/A:N");
        if (allNone) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private List<String> extractAliases(JsonObject vuln) {
        JsonArray aliases = (JsonArray) vuln.get("aliases");
        if (aliases == null || aliases.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (Object a : aliases) {
            list.add(String.valueOf(a));
        }
        return list;
    }

    private static String getString(JsonObject obj, String key) {
        Object val = obj.get(key);
        return val != null ? val.toString() : null;
    }
}
