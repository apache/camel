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
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.artifact.versioning.ComparableVersion;

/**
 * The offline OSV database (CAMEL-27344): the export of all the Maven vulnerabilities of OSV.dev, one ZIP file updated
 * every day, matched locally, so no coordinates of the project leave the machine. The ZIP comes from
 * {@link #DEFAULT_URL}, from a mirror of it (such as a raw repository of Nexus or a generic remote of Artifactory), or
 * from a file; a downloaded one is cached and refreshed once a day.
 */
public final class OsvDatabase implements VulnerabilitySource {

    /** The export of the Maven vulnerabilities of OSV.dev. */
    public static final String DEFAULT_URL = "https://osv-vulnerabilities.storage.googleapis.com/Maven/all.zip";

    /** How long a downloaded database is used before it is downloaded again. */
    public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(1);

    // an entry is a few KB; a larger one is not an OSV entry
    private static final long MAX_ENTRY_SIZE = 16 * 1024 * 1024;

    private final String location;
    private final Path file;
    private final Instant updated;
    private final String warning;
    private volatile String lastError;

    private OsvDatabase(String location, Path file, Instant updated, String warning, String error) {
        this.location = location;
        this.file = file;
        this.updated = updated;
        this.warning = warning;
        this.lastError = error;
    }

    /** The database of a ZIP file on disk. */
    public static OsvDatabase of(Path zip) {
        return new OsvDatabase(zip.toString(), zip, modified(zip), null, null);
    }

    /**
     * The database of a URL or a file: a URL is downloaded into the cache directory, and downloaded again when the
     * cached copy is older than {@code maxAge} (with {@code If-Modified-Since}, so an unchanged one is not); a stale
     * copy is kept when the download fails. Never fails: without a database, {@link #lastError()} says why and no JAR
     * is checked.
     *
     * @param location the URL of the ZIP ({@link #DEFAULT_URL} when null), a {@code file:} URL or a path
     * @param cacheDir where a downloaded ZIP is kept
     * @param maxAge   how long a downloaded ZIP is used
     * @param proxy    the proxy to download through; null for the proxy settings of the JVM
     */
    public static OsvDatabase open(String location, Path cacheDir, Duration maxAge, HttpProxy proxy) {
        String where = location != null && !location.isBlank() ? location.trim() : DEFAULT_URL;
        if (!where.startsWith("http://") && !where.startsWith("https://")) {
            Path zip = where.startsWith("file:") ? Path.of(URI.create(where)) : Path.of(where);
            if (!Files.isRegularFile(zip)) {
                return new OsvDatabase(where, null, null, null, "The OSV database " + zip + " does not exist");
            }
            return new OsvDatabase(where, zip, modified(zip), null, null);
        }
        Path cached = cacheDir.resolve(cacheName(where));
        if (Files.isRegularFile(cached) && age(cached).compareTo(maxAge) < 0) {
            return new OsvDatabase(where, cached, modified(cached), null, null);
        }
        try {
            download(where, cached, proxy);
            return new OsvDatabase(where, cached, modified(cached), null, null);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            String reason = "The OSV database could not be downloaded from " + where
                            + (proxy != null ? " through the proxy " + proxy : "") + ": "
                            + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            if (Files.isRegularFile(cached)) {
                return new OsvDatabase(
                        where, cached, modified(cached), reason + "; the copy of " + modified(cached)
                                                         + " is used",
                        null);
            }
            return new OsvDatabase(where, null, null, null, reason);
        }
    }

    /** Where the database comes from: its URL or file. */
    public String location() {
        return location;
    }

    /** The ZIP file of the database; null when there is none. */
    public Path file() {
        return file;
    }

    /** When the ZIP file was last updated; null when there is none. */
    public Instant updated() {
        return updated;
    }

    /** Why an older copy of the database is used, as the download failed; null when it is up to date. */
    public String warning() {
        return warning;
    }

    @Override
    public String lastError() {
        return lastError;
    }

    /**
     * Matches the JARs against the database: a JAR is affected when its version is one of the affected versions of an
     * entry for its {@code groupId:artifactId}, or in one of its affected ranges. Withdrawn entries are left out.
     */
    @Override
    public Map<String, List<OsvClient.Vulnerability>> queryBatch(List<Dependency> entries) {
        Map<String, List<Dependency>> byPackage = new HashMap<>();
        for (Dependency d : entries) {
            if (d.groupId() != null && d.version() != null) {
                byPackage.computeIfAbsent(d.groupId() + ":" + d.artifactId(), k -> new ArrayList<>()).add(d);
            }
        }
        Map<String, List<OsvClient.Vulnerability>> result = new LinkedHashMap<>();
        if (file == null) {
            return result;
        }
        try {
            Map<String, List<Affected>> affected = read(file, byPackage.keySet());
            for (Map.Entry<String, List<Dependency>> e : byPackage.entrySet()) {
                List<Affected> candidates = affected.getOrDefault(e.getKey(), List.of());
                for (Dependency d : e.getValue()) {
                    List<OsvClient.Vulnerability> vulns = new ArrayList<>();
                    for (Affected a : candidates) {
                        if (a.affects(e.getKey(), d.version()) && !vulns.contains(a.vulnerability())) {
                            vulns.add(a.vulnerability());
                        }
                    }
                    result.put(d.gav(), vulns);
                }
            }
            lastError = null;
        } catch (IOException e) {
            lastError = "The OSV database " + file + " could not be read: " + e.getMessage();
            result.clear();
        }
        return result;
    }

    /** An entry of the database for one package: the vulnerability, and the versions it lists as affected. */
    record Affected(OsvClient.Vulnerability vulnerability, List<String> versions) {

        boolean affects(String packageName, String version) {
            if (versions.contains(version)) {
                return true;
            }
            ComparableVersion v = new ComparableVersion(version);
            for (OsvClient.AffectedRange r : vulnerability.ranges()) {
                if (!r.packageName().equals(packageName)) {
                    continue;
                }
                if (r.introduced() != null && v.compareTo(new ComparableVersion(r.introduced())) < 0) {
                    continue;
                }
                if (r.fixed() != null) {
                    if (v.compareTo(new ComparableVersion(r.fixed())) < 0) {
                        return true;
                    }
                } else if (r.lastAffected() != null) {
                    if (v.compareTo(new ComparableVersion(r.lastAffected())) <= 0) {
                        return true;
                    }
                } else {
                    // no fix yet
                    return true;
                }
            }
            return false;
        }
    }

    /** The entries of the ZIP for the given packages, by package. */
    static Map<String, List<Affected>> read(Path zip, Set<String> packages) throws IOException {
        Map<String, List<Affected>> answer = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
                    continue;
                }
                byte[] data = in.readNBytes((int) MAX_ENTRY_SIZE + 1);
                if (data.length > MAX_ENTRY_SIZE) {
                    continue;
                }
                JsonObject json;
                try {
                    json = (JsonObject) Jsoner.deserialize(new String(data, StandardCharsets.UTF_8));
                } catch (Exception e) {
                    // not an OSV entry
                    continue;
                }
                if (json.get("withdrawn") != null) {
                    continue;
                }
                index(json, packages, answer);
            }
        }
        return answer;
    }

    private static void index(JsonObject json, Set<String> packages, Map<String, List<Affected>> answer) {
        JsonArray affected = (JsonArray) json.get("affected");
        if (affected == null) {
            return;
        }
        OsvClient.Vulnerability vuln = null;
        for (Object o : affected) {
            JsonObject aff = (JsonObject) o;
            JsonObject pkg = (JsonObject) aff.get("package");
            Object name = pkg != null ? pkg.get("name") : null;
            if (name == null || !packages.contains(name.toString()) || !"Maven".equals(pkg.get("ecosystem"))) {
                continue;
            }
            if (vuln == null) {
                vuln = OsvClient.parseVuln(json);
                if (vuln == null) {
                    return;
                }
            }
            List<String> versions = new ArrayList<>();
            JsonArray list = (JsonArray) aff.get("versions");
            if (list != null) {
                list.forEach(v -> versions.add(String.valueOf(v)));
            }
            answer.computeIfAbsent(name.toString(), k -> new ArrayList<>()).add(new Affected(vuln, versions));
        }
    }

    private static void download(String url, Path target, HttpProxy proxy) throws IOException, InterruptedException {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .proxy(proxy != null ? proxy.selector() : ProxySelector.getDefault());
        if (proxy != null && proxy.authenticator() != null) {
            HttpProxy.allowBasicTunneling();
            builder.authenticator(proxy.authenticator());
        }
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(5))
                .GET();
        if (Files.isRegularFile(target)) {
            request.header("If-Modified-Since",
                    DateTimeFormatter.RFC_1123_DATE_TIME.format(modified(target).atOffset(ZoneOffset.UTC)));
        }
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(), target.getFileName().toString(), ".tmp");
        try {
            HttpResponse<Path> response = builder.build().send(request.build(),
                    HttpResponse.BodyHandlers.ofFile(tmp));
            if (response.statusCode() == 304) {
                // unchanged: used for another day
                Files.setLastModifiedTime(target, FileTime.from(Instant.now()));
                return;
            }
            if (response.statusCode() == 407) {
                throw new IOException("the proxy needs a username and password (HTTP status 407)");
            }
            if (response.statusCode() != 200) {
                throw new IOException("HTTP status " + response.statusCode());
            }
            // a broken download does not replace a good copy
            try (ZipFile ignored = new ZipFile(tmp.toFile())) {
                // a ZIP
            } catch (IOException e) {
                throw new IOException("not a ZIP file", e);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // one cached file per URL
    static String cacheName(String url) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8));
            return "osv-" + HexFormat.of().formatHex(hash, 0, 8) + ".zip";
        } catch (Exception e) {
            return "osv-" + Integer.toHexString(url.hashCode()) + ".zip";
        }
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
