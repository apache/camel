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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * Answers which Maven dependency provides a class, and how to declare it, for the {@code camel_dependency_for_class}
 * tool (CAMEL-24810). Local first, online last:
 * <ol>
 * <li>the known dependencies camel run downloads on demand (the three mapping files of camel-kamelet-main, matched by
 * the class and then each enclosing package, see {@link BeanRefChecks#knownDependency}); a hit also means camel run
 * needs no declaration;</li>
 * <li>a Camel component, data format or language class answers its {@code camel-} artifact for each runtime;</li>
 * <li>only when asked ({@code mavenCentral=true}): Maven Central's class search, grouped by artifact, the newest
 * version fetched separately, and marked as a guess.</li>
 * </ol>
 * The declaration forms cover camel run ({@code camel.jbang.dependencies}, {@code --dep}) and the pom.xml of the three
 * runtimes: Camel Main, Spring Boot and Quarkus. A third-party library has the same coordinates in every runtime; a
 * Camel component does not.
 */
public final class DependencyLookup {

    static final String CENTRAL_SEARCH = "https://search.maven.org/solrsearch/select";
    private static final Map<String, JsonObject> CENTRAL_CACHE = new ConcurrentHashMap<>();

    private DependencyLookup() {
    }

    /**
     * Fetches a URL as text; replaced in tests. Uses the JVM proxy settings (http.proxyHost, https.proxyHost,
     * java.net.useSystemProxies) like the other CLI callers. Maven Central's search answers in under a second, but
     * throttles a client that repeats queries by holding each request for about thirty seconds before answering, so the
     * timeout is set just beyond that and the request is not repeated; answers are cached per class.
     */
    public static String fetch(String url) {
        try {
            HttpClient hc = HttpClient.newBuilder().proxy(ProxySelector.getDefault())
                    .connectTimeout(Duration.ofSeconds(10)).build();
            HttpResponse<String> res = hc.send(HttpRequest.newBuilder(new URI(url))
                    .header("User-Agent", "Apache Camel JBang").timeout(Duration.ofSeconds(40)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new ToolExecutionException("Maven Central search answered HTTP " + res.statusCode());
            }
            return res.body();
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolExecutionException(
                    "Maven Central search failed (" + e.getMessage() + "): try again later, or declare the dependency "
                                             + "yourself with camel.jbang.dependencies=<groupId>:<artifactId>:<version>",
                    e);
        }
    }

    public static JsonObject lookup(ToolContext ctx, String className, String runtime, boolean mavenCentral) {
        return lookup(ctx, className, runtime, mavenCentral, DependencyLookup::fetch);
    }

    static JsonObject lookup(
            ToolContext ctx, String className, String runtime, boolean mavenCentral, Function<String, String> fetcher) {
        String name = className.trim();
        if (name.startsWith("#class:")) {
            name = name.substring(7);
        }
        if (name.endsWith(".class")) {
            name = name.substring(0, name.length() - 6);
        }
        String rt = runtime == null || runtime.isBlank() ? null : runtime.trim().toLowerCase(Locale.ROOT);
        if (rt != null && !rt.equals("main") && !rt.equals("spring-boot") && !rt.equals("quarkus")) {
            throw new ToolExecutionException("runtime must be main, spring-boot or quarkus");
        }
        JsonObject answer = new JsonObject();
        answer.put("class", name);

        String known = BeanRefChecks.knownDependency(name);
        if (known != null) {
            if (known.startsWith("camel:")) {
                // the context carries a version only when one was selected; the catalog's is the CLI's own
                String version = ctx.camelVersion() != null ? ctx.camelVersion() : ctx.catalog().getCatalogVersion();
                return camelArtifact(answer, known.substring(6), version, rt, "camel-component");
            }
            String[] parts = known.split(":");
            if (parts.length >= 3) {
                answer.put("source", "known-dependencies");
                answer.put("autoDownload", true);
                answer.put("note", "camel run downloads this dependency when the class is used; nothing to declare "
                                   + "there. Declare it in a Maven project.");
                return declare(answer, parts[0], parts[1], parts[2], rt);
            }
        }
        if (!mavenCentral) {
            answer.put("source", "unknown");
            answer.put("autoDownload", false);
            answer.put("note", "Not in the known dependencies. Call again with mavenCentral=true to search Maven "
                               + "Central by class name, or declare the dependency yourself: "
                               + "camel.jbang.dependencies=<groupId>:<artifactId>:<version> in application.properties, "
                               + "--dep on camel run, or a pom.xml dependency.");
            return answer;
        }
        if (!name.contains(".") || Character.isLowerCase(name.charAt(name.lastIndexOf('.') + 1))) {
            throw new ToolExecutionException(
                    "Maven Central is searched by fully qualified class name; give a class, not a package");
        }
        JsonObject hit = centralSearch(name, fetcher);
        if (hit == null) {
            answer.put("source", "unknown");
            answer.put("autoDownload", false);
            answer.put("note", "Not in the known dependencies and Maven Central has no artifact with this class.");
            return answer;
        }
        answer.put("source", "maven-central");
        answer.put("autoDownload", false);
        answer.put("note", "A Maven Central search result: the artifact whose group and name best match the package, "
                           + "and its newest release (pre-releases skipped). Check it before adding it to a project; "
                           + "a shaded copy of the class may exist in other artifacts.");
        answer.put("candidates", hit.get("candidates"));
        return declare(answer, hit.getString("groupId"), hit.getString("artifactId"), hit.getString("version"), rt);
    }

    /** A Camel component, data format or language: the artifact differs per runtime, the version is the BOM's. */
    static JsonObject camelArtifact(JsonObject answer, String shortName, String camelVersion, String rt, String source) {
        answer.put("source", source);
        answer.put("autoDownload", true);
        answer.put("groupId", "org.apache.camel");
        answer.put("artifactId", "camel-" + shortName);
        answer.put("version", camelVersion);
        answer.put("note", "A Camel artifact: camel run resolves it by itself. In a Maven project use the runtime's "
                           + "artifact and BOM below; the BOM manages the version.");
        JsonObject declare = new JsonObject();
        declare.put("jbang", "camel.jbang.dependencies=camel:" + shortName);
        declare.put("cli", "--dep=camel:" + shortName);
        if (rt == null || rt.equals("main")) {
            declare.put("pomMain", pom("org.apache.camel", "camel-" + shortName, null,
                    "org.apache.camel:camel-bom:" + camelVersion));
        }
        if (rt == null || rt.equals("spring-boot")) {
            declare.put("pomSpringBoot", pom("org.apache.camel.springboot", "camel-" + shortName + "-starter", null,
                    "org.apache.camel.springboot:camel-spring-boot-bom:" + camelVersion));
        }
        if (rt == null || rt.equals("quarkus")) {
            declare.put("pomQuarkus", pom("org.apache.camel.quarkus", "camel-quarkus-" + shortName, null,
                    "org.apache.camel.quarkus:camel-quarkus-bom:<camel-quarkus version>"));
        }
        answer.put("declare", declare);
        return answer;
    }

    /** A third-party library: the same coordinates in every runtime. */
    static JsonObject declare(JsonObject answer, String groupId, String artifactId, String version, String rt) {
        answer.put("groupId", groupId);
        answer.put("artifactId", artifactId);
        answer.put("version", version);
        boolean placeholder = version.startsWith("${");
        if (placeholder) {
            answer.put("versionNote", "The version is a property of Camel's own build; camel run resolves it from "
                                      + "the camel-dependencies POM. In a project pick the version you want.");
        }
        String gav = groupId + ":" + artifactId + ":" + version;
        JsonObject declare = new JsonObject();
        declare.put("jbang", "camel.jbang.dependencies=" + gav);
        declare.put("cli", "--dep=" + gav);
        String v = placeholder ? "<version>" : version;
        if (rt == null || rt.equals("main")) {
            declare.put("pomMain", pom(groupId, artifactId, v, null));
        }
        if (rt == null || rt.equals("spring-boot")) {
            declare.put("pomSpringBoot", pom(groupId, artifactId, v, null));
        }
        if (rt == null || rt.equals("quarkus")) {
            declare.put("pomQuarkus", pom(groupId, artifactId, v, null));
        }
        answer.put("declare", declare);
        return answer;
    }

    static String pom(String groupId, String artifactId, String version, String bom) {
        StringBuilder sb = new StringBuilder();
        sb.append("<dependency>\n  <groupId>").append(groupId).append("</groupId>\n  <artifactId>").append(artifactId)
                .append("</artifactId>\n");
        if (version != null) {
            sb.append("  <version>").append(version).append("</version>\n");
        }
        sb.append("</dependency>");
        if (bom != null) {
            sb.append("\n(version managed by the BOM ").append(bom).append(" imported in dependencyManagement)");
        }
        return sb.toString();
    }

    /**
     * Maven Central's class search returns one document per version and shaded copy, newest score first, and a common
     * class has tens of thousands of them (20,737 for org.apache.commons.text.StringSubstitutor), so the first page may
     * hold only repackaged copies. The publisher's group id is almost always a prefix of the package, so the search is
     * first narrowed to each package prefix as the group ("org.apache.commons.text", then "org.apache.commons", ...)
     * and only then run unqualified. Within the hits the artifact whose name is in the package wins, shaded and uber
     * jars lose, and the version is the newest stable one seen (a pre-release latestVersion such as 5.0.0-alpha.16
     * loses to the newest release).
     */
    static JsonObject centralSearch(String className, Function<String, String> fetcher) {
        JsonObject cached = CENTRAL_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        String pkg = className.substring(0, className.lastIndexOf('.'));
        List<Object> docs = new ArrayList<>();
        String prefix = pkg;
        while (docs.isEmpty() && prefix.contains(".")) {
            docs = docs(parse(fetcher.apply(CENTRAL_SEARCH + "?q="
                                            + URLEncoder.encode("fc:\"" + className + "\" AND g:\"" + prefix + "\"",
                                                    StandardCharsets.UTF_8)
                                            + "&rows=50&wt=json")));
            prefix = prefix.substring(0, prefix.lastIndexOf('.'));
        }
        if (docs.isEmpty()) {
            docs = docs(parse(fetcher.apply(CENTRAL_SEARCH + "?q="
                                            + URLEncoder.encode("fc:\"" + className + "\"", StandardCharsets.UTF_8)
                                            + "&rows=50&wt=json")));
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, List<String>> versions = new LinkedHashMap<>();
        for (Object o : docs) {
            JsonObject d = (JsonObject) o;
            String ga = d.getString("g") + ":" + d.getString("a");
            counts.merge(ga, 1, Integer::sum);
            if (d.getString("v") != null) {
                versions.computeIfAbsent(ga, k -> new ArrayList<>()).add(d.getString("v"));
            }
        }
        if (counts.isEmpty()) {
            return null;
        }
        String lowerPkg = pkg.toLowerCase(Locale.ROOT);
        String best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            String[] ga = e.getKey().split(":");
            String g = ga[0].toLowerCase(Locale.ROOT);
            String a = ga[1].toLowerCase(Locale.ROOT);
            int score = 0;
            if (lowerPkg.startsWith(g)) {
                score += 4;
            }
            if (lowerPkg.contains(a.replace('-', '.')) || lowerPkg.contains(a) || lowerPkg.endsWith(a)) {
                score += 2;
            }
            if (a.contains("shaded") || a.contains("uber") || a.endsWith("-all") || a.contains("bundle")
                    || a.contains("-sdk")) {
                score -= 3;
            }
            if (score > bestScore) {
                bestScore = score;
                best = e.getKey();
            }
        }
        String[] ga = best.split(":");
        // the artifact's versions newest first (core=gav sorts by release date): the newest release wins over a
        // newer pre-release, and over whatever old versions the class search happened to return
        String vq = URLEncoder.encode("g:\"" + ga[0] + "\" AND a:\"" + ga[1] + "\"", StandardCharsets.UTF_8);
        List<String> newest = new ArrayList<>();
        for (Object o : docs(parse(fetcher.apply(CENTRAL_SEARCH + "?q=" + vq + "&core=gav&rows=25&wt=json")))) {
            String v = ((JsonObject) o).getString("v");
            if (v != null) {
                newest.add(v);
            }
        }
        String version = newestStable(newest, versions.getOrDefault(best, List.of()));
        JsonObject hit = new JsonObject();
        hit.put("groupId", ga[0]);
        hit.put("artifactId", ga[1]);
        hit.put("version", version);
        JsonArray candidates = new JsonArray();
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (n++ < 5) {
                candidates.add(e.getKey() + " (" + e.getValue() + " versions)");
            }
        }
        hit.put("candidates", candidates);
        CENTRAL_CACHE.put(className, hit);
        return hit;
    }

    private static final java.util.regex.Pattern PRERELEASE
            = java.util.regex.Pattern.compile("(?i)(alpha|beta|rc|snapshot|preview|milestone|-m\\d|\\.m\\d|-ea|cr\\d)");

    /**
     * The first release in the date-ordered list (newest first); a pre-release is skipped when a release follows it
     * (5.0.0-alpha.16 loses to 4.12.0). With no release in that list the newest one seen anywhere, else the newest
     * entry whatever it is.
     */
    static String newestStable(List<String> newestFirst, List<String> seen) {
        for (String v : newestFirst) {
            if (!PRERELEASE.matcher(v).find()) {
                return v;
            }
        }
        String best = null;
        for (String v : seen) {
            if (!PRERELEASE.matcher(v).find() && (best == null || compareVersions(v, best) > 0)) {
                best = v;
            }
        }
        if (best != null) {
            return best;
        }
        return newestFirst.isEmpty() ? (seen.isEmpty() ? null : seen.get(0)) : newestFirst.get(0);
    }

    static int compareVersions(String a, String b) {
        String[] x = a.split("[^0-9]+");
        String[] y = b.split("[^0-9]+");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            long p = i < x.length && !x[i].isEmpty() ? Long.parseLong(x[i]) : 0;
            long q = i < y.length && !y[i].isEmpty() ? Long.parseLong(y[i]) : 0;
            if (p != q) {
                return Long.compare(p, q);
            }
        }
        return 0;
    }

    private static JsonObject parse(String body) {
        try {
            return (JsonObject) Jsoner.deserialize(body);
        } catch (Exception e) {
            throw new ToolExecutionException("Maven Central search returned no JSON", e);
        }
    }

    private static List<Object> docs(JsonObject response) {
        JsonObject r = response == null ? null : response.getMap("response");
        JsonArray docs = r == null ? null : r.getCollection("docs");
        return docs == null ? new ArrayList<>() : docs;
    }
}
