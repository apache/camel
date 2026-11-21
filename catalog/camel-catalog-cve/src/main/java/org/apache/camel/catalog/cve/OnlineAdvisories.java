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

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.apache.camel.tooling.model.SecurityAdvisoryParser;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The Apache Camel security advisories, checked online (CAMEL-27348): the advisories published in the camel-website
 * repository ({@code content/security/CVE-*.md}, the source of camel.apache.org/security) merged with those of the
 * catalog, so an older CLI or plugin knows the advisories published after its release. One GitHub API call lists the
 * advisories, and only the new and changed files (by their sha) are downloaded. The result is cached and refreshed once
 * a day; when the refresh fails, the cached copy is used, else the advisories of the catalog only, with a warning.
 * <p>
 * The location can also be a mirror: a URL or a file of a JSON array in the format of the catalog
 * ({@code camel-security-advisories.json}), for machines without access to GitHub.
 */
public final class OnlineAdvisories {

    /** The advisories of camel-website, listed by the GitHub contents API. */
    public static final String DEFAULT_URL = "https://api.github.com/repos/apache/camel-website/contents/content/security";

    /** How long the advisories checked online are used before they are checked again. */
    public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(1);

    private static final String GITHUB_API = "https://api.github.com/";
    private static final Pattern CVE_ID = Pattern.compile("CVE-(\\d{4})-(\\d+)");
    // downloads at the same time, to be gentle with raw.githubusercontent.com
    private static final int PARALLEL = 8;

    private OnlineAdvisories() {
    }

    /**
     * The advisories to check: those of the catalog merged with those checked online, by CVE id (the online one wins).
     *
     * @param online  how many advisories came from the online copy; 0 when only those of the catalog are used
     * @param updated when the online copy was checked; null when there is none
     * @param warning why the online copy is older, or missing; null when it is up to date
     */
    public record Result(List<SecurityAdvisoryModel> advisories, int online, Instant updated, String warning) {
    }

    /**
     * The advisories of the catalog merged with those of the location.
     *
     * @param location the GitHub contents API of the advisories ({@link #DEFAULT_URL} when null), or the URL or file of
     *                 a JSON array in the format of the catalog
     * @param cacheDir where the advisories checked online are kept
     * @param maxAge   how long they are used before they are checked again
     * @param proxy    the proxy to reach the location through; null for the proxy settings of the JVM
     * @param catalog  the advisories of the catalog
     */
    public static Result load(
            String location, Path cacheDir, Duration maxAge, HttpProxy proxy, List<SecurityAdvisoryModel> catalog) {
        String where = location != null && !location.isBlank() ? location.trim() : DEFAULT_URL;
        Path cached = cacheDir.resolve(cacheName(where) + ".json");
        Path shas = cacheDir.resolve(cacheName(where) + ".sha");
        String warning = null;
        try {
            if (!where.startsWith("http://") && !where.startsWith("https://")) {
                Path file = where.startsWith("file:") ? Path.of(URI.create(where)) : Path.of(where);
                return merged(catalog, readJson(Files.readString(file)), modified(file), null);
            }
            if (!Files.isRegularFile(cached) || age(cached).compareTo(maxAge) >= 0) {
                refresh(where, cached, shas, proxy);
            }
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            warning = "The Apache Camel security advisories could not be checked online at " + where
                      + (proxy != null ? " through the proxy " + proxy : "") + ": "
                      + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
        try {
            if (Files.isRegularFile(cached)) {
                return merged(catalog, readJson(Files.readString(cached)), modified(cached),
                        warning != null ? warning + "; the copy of " + modified(cached) + " is used" : null);
            }
        } catch (Exception e) {
            warning = "The Apache Camel security advisories checked online could not be read: " + e.getMessage();
        }
        return new Result(
                catalog, 0, null,
                (warning != null ? warning : "No Apache Camel security advisories checked online")
                                  + "; those of the catalog are used");
    }

    private static Result merged(
            List<SecurityAdvisoryModel> catalog, List<SecurityAdvisoryModel> online, Instant updated, String warning) {
        Map<String, SecurityAdvisoryModel> byCve = new LinkedHashMap<>();
        for (SecurityAdvisoryModel a : catalog) {
            byCve.put(a.getCve(), a);
        }
        for (SecurityAdvisoryModel a : online) {
            byCve.put(a.getCve(), a);
        }
        List<SecurityAdvisoryModel> answer = new ArrayList<>(byCve.values());
        answer.sort(Comparator.comparingLong(OnlineAdvisories::cveOrdinal));
        return new Result(answer, online.size(), updated, warning);
    }

    /** Checks the location again, and writes the advisories into the cache. */
    private static void refresh(String url, Path cached, Path shas, HttpProxy proxy) throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(proxy != null ? proxy.selector() : ProxySelector.getDefault());
        if (proxy != null && proxy.authenticator() != null) {
            HttpProxy.allowBasicTunneling();
            builder.authenticator(proxy.authenticator());
        }
        try (HttpClient client = builder.build()) {
            String json = get(client, url);
            JsonArray array = (JsonArray) Jsoner.deserialize(json);
            if (!array.isEmpty() && array.get(0) instanceof JsonObject first && first.containsKey("download_url")) {
                // the listing of a folder of the GitHub contents API, such as the one of camel-website
                refreshFromListing(client, url, array, cached, shas);
            } else {
                // a mirror in the format of the catalog
                if (readJson(json).isEmpty()) {
                    throw new IOException("no advisories at " + url);
                }
                write(cached, json);
            }
        }
    }

    private static void refreshFromListing(HttpClient client, String url, JsonArray listing, Path cached, Path shas)
            throws Exception {
        // the advisories downloaded before, by file name, with the sha of the file
        Properties knownShas = new Properties();
        Map<String, SecurityAdvisoryModel> known = new LinkedHashMap<>();
        if (Files.isRegularFile(shas) && Files.isRegularFile(cached)) {
            try (Reader r = Files.newBufferedReader(shas)) {
                knownShas.load(r);
            }
            for (SecurityAdvisoryModel a : readJson(Files.readString(cached))) {
                known.put(a.getCve() + ".md", a);
            }
        }
        Properties newShas = new Properties();
        Map<String, SecurityAdvisoryModel> result = new LinkedHashMap<>();
        Map<String, String> toDownload = new LinkedHashMap<>();
        for (Object o : listing) {
            JsonObject file = (JsonObject) o;
            String name = file.getString("name");
            String sha = file.getString("sha");
            String download = file.getString("download_url");
            if (name == null || download == null || !SecurityAdvisoryParser.ADVISORY_FILE.matcher(name).matches()) {
                continue;
            }
            if (sha != null) {
                newShas.setProperty(name, sha);
            }
            if (sha != null && sha.equals(knownShas.getProperty(name))) {
                // unchanged: as downloaded before (a draft has no advisory)
                SecurityAdvisoryModel before = known.get(name);
                if (before != null) {
                    result.put(name, before);
                }
            } else {
                toDownload.put(name, download);
            }
        }
        if (newShas.isEmpty()) {
            throw new IOException("no advisories listed at " + url);
        }
        List<String> names = new ArrayList<>(toDownload.keySet());
        for (int i = 0; i < names.size(); i += PARALLEL) {
            List<String> batch = names.subList(i, Math.min(names.size(), i + PARALLEL));
            List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
            for (String name : batch) {
                futures.add(client.sendAsync(request(toDownload.get(name)), HttpResponse.BodyHandlers.ofString()));
            }
            for (int j = 0; j < batch.size(); j++) {
                HttpResponse<String> res = futures.get(j).get();
                if (res.statusCode() != 200) {
                    throw new IOException("HTTP status " + res.statusCode() + " downloading " + toDownload.get(batch.get(j)));
                }
                SecurityAdvisoryModel model = SecurityAdvisoryParser.parse(res.body());
                if (model != null) {
                    result.put(batch.get(j), model);
                }
            }
        }
        JsonArray arr = new JsonArray();
        result.values().stream().sorted(Comparator.comparingLong(OnlineAdvisories::cveOrdinal))
                .forEach(a -> arr.add(JsonMapper.asJsonObject(a)));
        write(cached, Jsoner.prettyPrint(Jsoner.serialize(arr), 4));
        Path tmp = Files.createTempFile(shas.getParent(), shas.getFileName().toString(), ".tmp");
        try (Writer w = Files.newBufferedWriter(tmp)) {
            newShas.store(w, "the sha of the advisories of " + url);
        }
        move(tmp, shas);
    }

    private static String get(HttpClient client, String url) throws IOException, InterruptedException {
        HttpResponse<String> res = client.send(request(url), HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 403 || res.statusCode() == 429) {
            throw new IOException(
                    "HTTP status " + res.statusCode() + " (rate limited: set the GITHUB_TOKEN environment"
                                  + " variable)");
        }
        if (res.statusCode() == 407) {
            throw new IOException("the proxy needs a username and password (HTTP status 407)");
        }
        if (res.statusCode() != 200) {
            throw new IOException("HTTP status " + res.statusCode());
        }
        return res.body();
    }

    private static HttpRequest request(String url) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30));
        String token = System.getenv("GITHUB_TOKEN");
        if (url.startsWith(GITHUB_API) && token != null && !token.isBlank()) {
            builder.header("Authorization", "Bearer " + token.trim());
        }
        return builder.build();
    }

    /** The advisories of a JSON array in the format of the catalog. */
    static List<SecurityAdvisoryModel> readJson(String json) throws Exception {
        List<SecurityAdvisoryModel> answer = new ArrayList<>();
        for (Object o : (JsonArray) Jsoner.deserialize(json)) {
            SecurityAdvisoryModel model = JsonMapper.generateSecurityAdvisoryModel((JsonObject) o);
            if (model.getCve() != null) {
                answer.add(model);
            }
        }
        return answer;
    }

    private static void write(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            Files.writeString(tmp, text);
            move(tmp, file);
        } finally {
            // a failed write or move leaves no temporary file in the cache
            Files.deleteIfExists(tmp);
        }
    }

    private static void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // one cached copy per location
    static String cacheName(String location) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(location.getBytes(StandardCharsets.UTF_8));
            return "advisories-" + HexFormat.of().formatHex(hash, 0, 8);
        } catch (Exception e) {
            return "advisories-" + Integer.toHexString(location.hashCode());
        }
    }

    private static long cveOrdinal(SecurityAdvisoryModel advisory) {
        Matcher matcher = CVE_ID.matcher(advisory.getCve() == null ? "" : advisory.getCve());
        if (matcher.find()) {
            return Long.parseLong(matcher.group(1)) * 1_000_000L + Long.parseLong(matcher.group(2));
        }
        return 0;
    }

    private static Instant modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException e) {
            return null;
        }
    }

    private static Duration age(Path file) {
        Instant m = modified(file);
        return m != null ? Duration.between(m, Instant.now()) : Duration.ofDays(36500);
    }
}
