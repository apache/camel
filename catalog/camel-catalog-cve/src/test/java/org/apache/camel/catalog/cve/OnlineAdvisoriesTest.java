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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.tooling.model.SecurityAdvisoryModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-27348: the Apache Camel security advisories checked online, merged with those of the catalog, cached, and
 * refreshed with only the changed files downloaded.
 */
class OnlineAdvisoriesTest {

    private static final String PUBLISHED = """
            ---
            title: "Apache Camel Security Advisory - CVE-2026-10001"
            url: /security/CVE-2026-10001.html
            draft: false
            type: security-advisory
            cve: CVE-2026-10001
            severity: HIGH
            summary: "Camel-Kafka: a header is evaluated"
            affected: "From 4.8.0 before 4.18.5"
            fixed: 4.18.5 and 4.23.0
            ---
            """;
    private static final String DRAFT = """
            ---
            draft: true
            type: security-advisory
            cve: CVE-2026-10002
            severity: CRITICAL
            ---
            """;

    @TempDir
    Path dir;

    private HttpServer server;
    private String base;
    private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();
    private volatile String sha1 = "aaa";
    private volatile int listingStatus = 200;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        serve("/listing", () -> listingStatus, () -> """
                [{"name": "CVE-2026-10001.md", "sha": "%1$s", "download_url": "%2$s/raw/CVE-2026-10001.md"},
                 {"name": "CVE-2026-10002.md", "sha": "bbb", "download_url": "%2$s/raw/CVE-2026-10002.md"},
                 {"name": "_index.md", "sha": "ccc", "download_url": "%2$s/raw/_index.md"}]""".formatted(sha1, base));
        serve("/raw/CVE-2026-10001.md", () -> 200, () -> PUBLISHED);
        serve("/raw/CVE-2026-10002.md", () -> 200, () -> DRAFT);
        serve("/mirror.json", () -> 200, () -> """
                [{"cve": "CVE-2026-10003", "severity": "LOW", "affected": "4.20.0", "components": ["camel-file"]}]""");
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void serve(String path, IntSupplier status, Supplier<String> body) {
        server.createContext(path, exchange -> {
            requests.computeIfAbsent(path, k -> new AtomicInteger()).incrementAndGet();
            byte[] bytes = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.getAsInt(), bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
    }

    private int count(String path) {
        AtomicInteger n = requests.get(path);
        return n != null ? n.get() : 0;
    }

    private static SecurityAdvisoryModel advisory(String cve, String severity) {
        SecurityAdvisoryModel a = new SecurityAdvisoryModel();
        a.setCve(cve);
        a.setSeverity(severity);
        return a;
    }

    private static List<String> cves(OnlineAdvisories.Result result) {
        return result.advisories().stream().map(SecurityAdvisoryModel::getCve).toList();
    }

    @Test
    void mergesTheOnlineAdvisoriesWithThoseOfTheCatalog() {
        List<SecurityAdvisoryModel> catalog = List.of(advisory("CVE-2013-4330", "CRITICAL"), advisory("CVE-2026-10001", "LOW"));

        OnlineAdvisories.Result result
                = OnlineAdvisories.load(base + "/listing", dir, Duration.ofDays(1), null, catalog);

        assertThat(result.warning()).isNull();
        // the draft is left out, and the online CVE-2026-10001 wins over the one of the catalog
        assertThat(cves(result)).containsExactly("CVE-2013-4330", "CVE-2026-10001");
        assertThat(result.advisories().get(1).getSeverity()).isEqualTo("HIGH");
        assertThat(result.advisories().get(1).getUrl()).isEqualTo("https://camel.apache.org/security/CVE-2026-10001.html");
        assertThat(result.advisories().get(1).getComponents()).containsExactly("camel-kafka");
        assertThat(result.online()).isEqualTo(1);
        assertThat(result.updated()).isNotNull();
    }

    @Test
    void downloadsOnlyTheChangedFiles() throws Exception {
        OnlineAdvisories.load(base + "/listing", dir, Duration.ofDays(1), null, List.of());
        assertThat(count("/listing")).isEqualTo(1);
        assertThat(count("/raw/CVE-2026-10001.md")).isEqualTo(1);
        assertThat(count("/raw/CVE-2026-10002.md")).isEqualTo(1);
        assertThat(count("/raw/_index.md")).isZero();

        // within a day: the cached copy, without a request
        OnlineAdvisories.load(base + "/listing", dir, Duration.ofDays(1), null, List.of());
        assertThat(count("/listing")).isEqualTo(1);

        // a day later, nothing changed: only the listing (and the draft is not downloaded again)
        expire();
        OnlineAdvisories.Result unchanged = OnlineAdvisories.load(base + "/listing", dir, Duration.ofDays(1), null, List.of());
        assertThat(count("/listing")).isEqualTo(2);
        assertThat(count("/raw/CVE-2026-10001.md")).isEqualTo(1);
        assertThat(count("/raw/CVE-2026-10002.md")).isEqualTo(1);
        assertThat(cves(unchanged)).containsExactly("CVE-2026-10001");

        // the advisory was changed: downloaded again
        expire();
        sha1 = "ddd";
        OnlineAdvisories.load(base + "/listing", dir, Duration.ofDays(1), null, List.of());
        assertThat(count("/raw/CVE-2026-10001.md")).isEqualTo(2);
    }

    @Test
    void usesTheCopyOfTheDayBeforeWhenTheRefreshFails() throws Exception {
        OnlineAdvisories.load(base + "/listing", dir, Duration.ofDays(1), null, List.of());
        expire();
        listingStatus = 403;

        OnlineAdvisories.Result result = OnlineAdvisories.load(base + "/listing", dir, Duration.ofDays(1), null, List.of());

        assertThat(result.warning()).contains("could not be checked online").contains("GITHUB_TOKEN").contains("is used");
        assertThat(cves(result)).containsExactly("CVE-2026-10001");
        assertThat(result.online()).isEqualTo(1);
    }

    @Test
    void usesTheCatalogWithoutAnyCopy() {
        listingStatus = 500;
        List<SecurityAdvisoryModel> catalog = List.of(advisory("CVE-2013-4330", "CRITICAL"));

        OnlineAdvisories.Result result = OnlineAdvisories.load(base + "/listing", dir, Duration.ofDays(1), null, catalog);

        assertThat(cves(result)).containsExactly("CVE-2013-4330");
        assertThat(result.online()).isZero();
        assertThat(result.updated()).isNull();
        assertThat(result.warning()).contains("HTTP status 500").contains("those of the catalog are used");
    }

    @Test
    void readsAMirrorOrAFileInTheFormatOfTheCatalog() throws Exception {
        OnlineAdvisories.Result mirror = OnlineAdvisories.load(base + "/mirror.json", dir, Duration.ofDays(1), null,
                List.of(advisory("CVE-2013-4330", "CRITICAL")));
        assertThat(mirror.warning()).isNull();
        assertThat(cves(mirror)).containsExactly("CVE-2013-4330", "CVE-2026-10003");

        Path file = Files.writeString(dir.resolve("advisories.json"), """
                [{"cve": "CVE-2026-10004", "severity": "MEDIUM"}]""");
        OnlineAdvisories.Result local = OnlineAdvisories.load(file.toString(), dir, Duration.ofDays(1), null, List.of());
        assertThat(cves(local)).containsExactly("CVE-2026-10004");
    }

    // the cached copy is two days old
    private void expire() throws IOException {
        try (var files = Files.list(dir)) {
            for (Path f : files.toList()) {
                Files.setLastModifiedTime(f, FileTime.from(Instant.now().minus(Duration.ofDays(2))));
            }
        }
    }
}
