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
package org.apache.camel.dsl.jbang.core.common;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The download of an example from GitHub fails when a file of it cannot be downloaded, instead of running the example
 * without that file (CAMEL-27004), and a file whose content stalls does not hang the download.
 */
class ExampleHelperDownloadTest {

    private HttpServer server;
    private ExecutorService executor;
    private String rawUrl;
    // the status per file name; 200 when not given
    private final Map<String, Integer> status = new ConcurrentHashMap<>();
    private final CountDownLatch release = new CountDownLatch(1);
    private volatile String stalled;
    // the content of the first file, to tell whether a folder of it was left behind
    private final String marker = UUID.randomUUID().toString();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            String file = exchange.getRequestURI().getPath().substring("/hello/".length());
            int code = status.getOrDefault(file, 200);
            if (code != 200) {
                exchange.sendResponseHeaders(code, -1);
                exchange.close();
                return;
            }
            byte[] body = ("route.yaml".equals(file) ? marker : "content of " + file).getBytes(StandardCharsets.UTF_8);
            if (file.equals(stalled)) {
                // the headers, and then the content only when the test ends
                exchange.sendResponseHeaders(200, body.length + 1);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                    out.flush();
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        rawUrl = "http://localhost:" + server.getAddress().getPort() + "/%s/%s";
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    @Test
    void downloadsEachFile() throws Exception {
        Path dir = ExampleHelper.downloadGithubExample(example(), false, rawUrl, Duration.ofSeconds(10));
        try {
            assertThat(dir.resolve("route.yaml")).hasContent(marker);
            assertThat(dir.resolve("input/order.xml")).hasContent("content of input/order.xml");
        } finally {
            FileUtil.removeDir(dir.toFile());
        }
    }

    @Test
    void aFileThatIsNotThereFailsTheDownload() throws Exception {
        status.put("input/order.xml", 404);

        assertThatThrownBy(() -> ExampleHelper.downloadGithubExample(example(), false, rawUrl, Duration.ofSeconds(10)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("/hello/input/order.xml")
                .hasMessageContaining("HTTP 404");
        // and not half of the example is left
        assertThat(foldersWithTheFirstFile()).isEmpty();
    }

    @Test
    void aRateLimitSaysSo() {
        status.put("route.yaml", 429);

        assertThatThrownBy(() -> ExampleHelper.downloadGithubExample(example(), false, rawUrl, Duration.ofSeconds(10)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("HTTP 429")
                .hasMessageContaining("rate limit");
    }

    @Test
    void aStalledFileTimesOut() throws Exception {
        stalled = "input/order.xml";

        long start = System.nanoTime();
        assertThatThrownBy(() -> ExampleHelper.downloadGithubExample(example(), false, rawUrl, Duration.ofSeconds(1)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("timed out");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
        assertThat(foldersWithTheFirstFile()).isEmpty();
    }

    private static JsonObject example() {
        JsonObject entry = new JsonObject();
        entry.put("name", "hello");
        entry.put("files", List.of("route.yaml", "input/order.xml"));
        return entry;
    }

    // the example folders in the temporary directory that hold the first file of this test
    private List<Path> foldersWithTheFirstFile() throws IOException {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        try (Stream<Path> dirs = Files.list(tmp)) {
            return dirs.filter(d -> d.getFileName().toString().startsWith(ExampleHelper.EXAMPLE_DIR_PREFIX))
                    .filter(d -> {
                        try {
                            Path f = d.resolve("route.yaml");
                            return Files.isRegularFile(f) && marker.equals(Files.readString(f));
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();
        }
    }
}
