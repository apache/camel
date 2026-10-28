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
package org.apache.camel.test.infra.wolfdefender.services;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ResourceLock("java.lang.System.properties")
class WolfDefenderServiceTest {
    private static final String FILE = "onnx/onnx_fp32/model.onnx";
    private static final byte[] CONTENT = "model fixture".getBytes(StandardCharsets.UTF_8);
    @TempDir
    Path directory;
    private HttpServer server;
    private URI source;
    private Map<String, String> artifacts;
    private final AtomicInteger requests = new AtomicInteger();
    private final Map<String, String> previousProperties = new HashMap<>();
    private static final String[] PROPERTIES = {
            WolfDefenderService.MODEL_DIRECTORY,
            WolfDefenderService.DOWNLOAD, WolfDefenderService.CACHE_DIRECTORY };
    private volatile int status = 200;
    private volatile byte[] response = CONTENT;

    @BeforeEach
    void setUp() throws Exception {
        for (String property : PROPERTIES) {
            previousProperties.put(property, System.getProperty(property));
            System.clearProperty(property);
        }
        artifacts = Map.of(FILE, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(CONTENT)));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/" + FILE, exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(status, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            } finally {
                exchange.close();
            }
        });
        server.start();
        source = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
        previousProperties.forEach((name, value) -> {
            if (value == null) {
                System.clearProperty(name);
            } else {
                System.setProperty(name, value);
            }
        });
    }

    @Test
    void requiresExplicitModelTestConfiguration() {
        assertThat(WolfDefenderService.isEnabled()).isFalse();
        System.setProperty(WolfDefenderService.CACHE_DIRECTORY, directory.toString());
        assertThat(WolfDefenderService.isEnabled()).isFalse();
        System.setProperty(WolfDefenderService.DOWNLOAD, "false");
        assertThat(WolfDefenderService.isEnabled()).isFalse();
        System.setProperty(WolfDefenderService.DOWNLOAD, "true");
        assertThat(WolfDefenderService.isEnabled()).isTrue();
        System.clearProperty(WolfDefenderService.DOWNLOAD);
        System.setProperty(WolfDefenderService.MODEL_DIRECTORY, directory.toString());
        assertThat(WolfDefenderService.isEnabled()).isTrue();
    }

    @Test
    void existingDirectoryTakesPrecedenceOverDownload() {
        System.setProperty(WolfDefenderService.MODEL_DIRECTORY, directory.toString());
        System.setProperty(WolfDefenderService.DOWNLOAD, "true");
        System.setProperty(WolfDefenderService.CACHE_DIRECTORY, directory.resolve("cache").toString());
        var service = new WolfDefenderService();
        assertThatThrownBy(service::initialize).hasRootCauseInstanceOf(NoSuchFileException.class);
        assertThat(directory).isEmptyDirectory();
    }

    @Test
    void validatesExistingFilesWithoutNetworkOrModification() throws Exception {
        Path file = directory.resolve(FILE);
        Files.createDirectories(file.getParent());
        Files.write(file, CONTENT);
        var service = service(false);
        assertThatThrownBy(service::getModelDirectory).isInstanceOf(IllegalStateException.class);
        service.initialize();
        service.shutdown();
        assertThat(service.getModelDirectory()).isEqualTo(directory);
        assertThat(Files.readAllBytes(file)).isEqualTo(CONTENT);
        assertThat(requests).hasValue(0);
    }

    @Test
    void rejectsMissingOrCorruptExistingFilesWithoutDownloading() throws Exception {
        var service = service(false);
        assertThatThrownBy(service::initialize).isInstanceOf(IllegalStateException.class);
        Path file = directory.resolve(FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "corrupt");
        assertThatThrownBy(service::initialize).hasRootCauseMessage("Wolf-Defender artifact checksum mismatch: model.onnx");
        assertThat(Files.readString(file)).isEqualTo("corrupt");
        assertThat(requests).hasValue(0);
    }

    @Test
    void downloadsOnceAndReusesVerifiedCacheAcrossInstances() throws Exception {
        assertThat(requests).hasValue(0);
        var first = service(true);
        assertThat(Files.exists(directory.resolve(FILE))).isFalse();
        first.initialize();
        first.shutdown();
        service(true).initialize();
        assertThat(Files.readAllBytes(directory.resolve(FILE))).isEqualTo(CONTENT);
        assertThat(requests).hasValue(1);
        assertNoPartialFiles();
    }

    @Test
    void replacesAnAbandonedPartialDownload() throws Exception {
        Path partial = directory.resolve("onnx/onnx_fp32/.wolf-defender-model.onnx.part");
        Files.createDirectories(partial.getParent());
        Files.writeString(partial, "abandoned download");
        service(true).initialize();
        assertThat(Files.readAllBytes(directory.resolve(FILE))).isEqualTo(CONTENT);
        assertThat(requests).hasValue(1);
        assertNoPartialFiles();
    }

    @Test
    void replacesCorruptCachedFilesAfterVerifyingTheDownload() throws Exception {
        Path file = directory.resolve(FILE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "corrupt");
        service(true).initialize();
        assertThat(Files.readAllBytes(file)).isEqualTo(CONTENT);
        assertThat(requests).hasValue(1);
        assertNoPartialFiles();
    }

    @Test
    void rejectsCorruptDownloadsAndRemovesPartialFiles() throws Exception {
        response = "wrong model".getBytes(StandardCharsets.UTF_8);
        var service = service(true);
        assertThatThrownBy(service::initialize).hasRootCauseInstanceOf(IOException.class);
        assertThatThrownBy(service::getModelDirectory).isInstanceOf(IllegalStateException.class);
        assertThat(Files.exists(directory.resolve(FILE))).isFalse();
        assertNoPartialFiles();
    }

    @Test
    void rejectsHttpErrorsAndRemovesPartialFiles() throws Exception {
        status = 503;
        assertThatThrownBy(() -> service(true).initialize())
                .hasRootCauseMessage("Cannot download " + FILE + ": HTTP 503");
        assertThat(Files.exists(directory.resolve(FILE))).isFalse();
        assertNoPartialFiles();
    }

    @Test
    void serializesConcurrentCacheProvisioning() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(service(true)::initialize);
            var second = executor.submit(service(true)::initialize);
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(requests).hasValue(1);
        assertNoPartialFiles();
    }

    private WolfDefenderService service(boolean download) {
        return new WolfDefenderService(directory, download, source, artifacts);
    }

    private void assertNoPartialFiles() throws IOException {
        try (var files = Files.walk(directory)) {
            assertThat(files.noneMatch(path -> path.toString().endsWith(".part"))).isTrue();
        }
    }
}
