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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27344: the offline OSV database, matched locally, downloaded from a mirror and cached.
 */
class OsvDatabaseTest {

    // commons-text 1.5 up to 1.10.0, as OSV writes CVE-2022-42889
    private static final String TEXT
            = """
                    {"id": "GHSA-599f-7c49-w659", "aliases": ["CVE-2022-42889"], "summary": "Arbitrary code execution in Apache Commons Text",
                     "database_specific": {"severity": "CRITICAL"},
                     "affected": [{"package": {"name": "org.apache.commons:commons-text", "ecosystem": "Maven"},
                       "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "1.5"}, {"fixed": "1.10.0"}]}]}]}
                    """;
    // only the affected versions are listed
    private static final String LISTED = """
            {"id": "GHSA-list-only", "summary": "Listed versions",
             "affected": [{"package": {"name": "org.example:listed", "ecosystem": "Maven"}, "versions": ["2.0-beta1"]}]}
            """;
    // up to the last affected version, with no fix
    private static final String LAST = """
            {"id": "GHSA-last-affected", "summary": "No fix",
             "affected": [{"package": {"name": "org.example:last", "ecosystem": "Maven"},
               "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "0"}, {"last_affected": "3.2"}]}]}]}
            """;
    private static final String WITHDRAWN = """
            {"id": "GHSA-withdrawn", "withdrawn": "2024-01-01T00:00:00Z",
             "affected": [{"package": {"name": "org.example:last", "ecosystem": "Maven"},
               "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "0"}]}]}]}
            """;
    // the same name in another ecosystem
    private static final String NPM = """
            {"id": "GHSA-npm", "affected": [{"package": {"name": "org.example:last", "ecosystem": "npm"},
               "ranges": [{"type": "ECOSYSTEM", "events": [{"introduced": "0"}]}]}]}
            """;

    @TempDir
    Path dir;

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void matchesTheRangesAndTheListedVersions() throws Exception {
        OsvDatabase db = OsvDatabase.of(zip(dir.resolve("all.zip")));

        Map<String, List<OsvClient.Vulnerability>> result = db.queryBatch(List.of(
                new Dependency("org.apache.commons", "commons-text", "1.9"),
                new Dependency("org.apache.commons", "commons-text", "1.10.0"),
                new Dependency("org.apache.commons", "commons-text", "1.4"),
                new Dependency("org.example", "listed", "2.0-beta1"),
                new Dependency("org.example", "listed", "2.0"),
                new Dependency("org.example", "last", "3.2"),
                new Dependency("org.example", "last", "3.2.1"),
                new Dependency("org.example", "unknown", "1.0")));

        assertThat(ids(result, "org.apache.commons:commons-text:1.9")).containsExactly("GHSA-599f-7c49-w659");
        assertThat(result.get("org.apache.commons:commons-text:1.9").get(0).aliases()).containsExactly("CVE-2022-42889");
        assertThat(result.get("org.apache.commons:commons-text:1.9").get(0).severity()).isEqualTo("CRITICAL");
        // fixed, and before the range
        assertThat(ids(result, "org.apache.commons:commons-text:1.10.0")).isEmpty();
        assertThat(ids(result, "org.apache.commons:commons-text:1.4")).isEmpty();
        assertThat(ids(result, "org.example:listed:2.0-beta1")).containsExactly("GHSA-list-only");
        assertThat(ids(result, "org.example:listed:2.0")).isEmpty();
        // up to the last affected one; the withdrawn entry and the npm package are left out
        assertThat(ids(result, "org.example:last:3.2")).containsExactly("GHSA-last-affected");
        assertThat(ids(result, "org.example:last:3.2.1")).isEmpty();
        // checked, with no vulnerability
        assertThat(result).containsEntry("org.example:unknown:1.0", List.of());
        assertThat(db.lastError()).isNull();
    }

    @Test
    void aMissingFileIsAnError() {
        OsvDatabase db = OsvDatabase.open(dir.resolve("none.zip").toString(), dir, Duration.ofDays(1), null);

        assertThat(db.queryBatch(List.of(new Dependency("org.yaml", "snakeyaml", "1.33")))).isEmpty();
        assertThat(db.lastError()).contains("does not exist");
    }

    @Test
    void downloadsOnceADayFromAMirror() throws Exception {
        byte[] zip = Files.readAllBytes(zip(dir.resolve("served.zip")));
        String url = serve(zip, false);
        Path cache = dir.resolve("cache");

        OsvDatabase first = OsvDatabase.open(url, cache, Duration.ofDays(1), null);
        assertThat(first.lastError()).isNull();
        assertThat(first.file()).exists().hasParent(cache);
        assertThat(ids(first.queryBatch(List.of(new Dependency("org.apache.commons", "commons-text", "1.9"))),
                "org.apache.commons:commons-text:1.9")).containsExactly("GHSA-599f-7c49-w659");

        // the cached copy, without a request
        OsvDatabase second = OsvDatabase.open(url, cache, Duration.ofDays(1), null);
        assertThat(second.file()).isEqualTo(first.file());
        assertThat(requests).hasSize(1);

        // a day later: asked again with If-Modified-Since
        Files.setLastModifiedTime(first.file(), FileTime.from(Instant.now().minus(Duration.ofDays(2))));
        OsvDatabase third = OsvDatabase.open(url, cache, Duration.ofDays(1), null);
        assertThat(third.lastError()).isNull();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(1)).isNotEmpty();
    }

    @Test
    void keepsTheOldCopyWhenTheDownloadFails() throws Exception {
        byte[] zip = Files.readAllBytes(zip(dir.resolve("served.zip")));
        String url = serve(zip, false);
        Path cache = dir.resolve("cache");
        Path cached = OsvDatabase.open(url, cache, Duration.ofDays(1), null).file();
        server.stop(0);
        // a broken mirror: not a ZIP
        url = serve("<html>maintenance</html>".getBytes(StandardCharsets.UTF_8), true);
        Path stale = cache.resolve(OsvDatabase.cacheName(url));
        Files.copy(cached, stale);
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minus(Duration.ofDays(2))));

        OsvDatabase db = OsvDatabase.open(url, cache, Duration.ofDays(1), null);

        assertThat(db.lastError()).isNull();
        assertThat(db.warning()).contains("could not be downloaded").contains("not a ZIP file").contains("is used");
        assertThat(db.file()).isEqualTo(stale);
        assertThat(ids(db.queryBatch(List.of(new Dependency("org.apache.commons", "commons-text", "1.9"))),
                "org.apache.commons:commons-text:1.9")).containsExactly("GHSA-599f-7c49-w659");
    }

    @Test
    void noDatabaseWithoutDownload() throws Exception {
        String url = serve("<html>maintenance</html>".getBytes(StandardCharsets.UTF_8), true);

        OsvDatabase db = OsvDatabase.open(url, dir.resolve("cache"), Duration.ofDays(1), null);

        assertThat(db.file()).isNull();
        assertThat(db.lastError()).contains("could not be downloaded from " + url);
        assertThat(db.queryBatch(List.of(new Dependency("org.yaml", "snakeyaml", "1.33")))).isEmpty();
    }

    private String serve(byte[] body, boolean always) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/osv/Maven/all.zip", exchange -> {
            String since = exchange.getRequestHeaders().getFirst("If-Modified-Since");
            requests.add(since != null ? since : "");
            if (since != null && !always) {
                exchange.sendResponseHeaders(304, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/osv/Maven/all.zip";
    }

    private static Path zip(Path file) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream out = new ZipOutputStream(bytes)) {
            String[] entries = { TEXT, LISTED, LAST, WITHDRAWN, NPM };
            for (int i = 0; i < entries.length; i++) {
                out.putNextEntry(new ZipEntry("entry-" + i + ".json"));
                out.write(entries[i].getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return Files.write(file, bytes.toByteArray());
    }

    private static List<String> ids(Map<String, List<OsvClient.Vulnerability>> result, String gav) {
        return result.getOrDefault(gav, List.of()).stream().map(OsvClient.Vulnerability::id).toList();
    }
}
