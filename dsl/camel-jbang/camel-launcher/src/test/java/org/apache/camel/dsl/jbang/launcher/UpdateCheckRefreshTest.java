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
package org.apache.camel.dsl.jbang.launcher;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.dsl.jbang.core.common.LauncherHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The check for a newer release of a launcher of the web installer, once a day: a command ends before the fetch over
 * the network would, so the launcher waits for it a little after the command, and marks the check before the fetch, so
 * that a failed fetch is not tried again by every command. Each command runs in a new JVM, against a manifest server of
 * the test.
 */
class UpdateCheckRefreshTest {

    private static final String MANIFEST = "format=1\nversion=4.99.0\ntar_sha256=" + "a".repeat(64) + "\nzip_sha256="
                                           + "b".repeat(64) + "\n";

    @TempDir
    Path dir;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 200;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = MANIFEST.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void aQuickCommandRefreshesAStaleCache() throws Exception {
        long start = System.currentTimeMillis();

        Properties cache = run("stale");

        assertThat(requests).hasValue(1);
        assertThat(cache.getProperty("latest_version")).isEqualTo("4.99.0");
        assertThat(Long.parseLong(cache.getProperty("last_checked"))).isGreaterThanOrEqualTo(start);
    }

    @Test
    void aFailedFetchIsNotTriedAgainByTheNextCommand() throws Exception {
        status = 500;

        run("stale");
        Properties cache = run("as-is");

        assertThat(requests).hasValue(1);
        // the version of the check before
        assertThat(cache.getProperty("latest_version")).isEqualTo("4.0.0");
    }

    // runs camel version in a new JVM, and returns the cache of the check after it
    private Properties run(String cache) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder builder = new ProcessBuilder(
                List.of(java, "-cp", System.getProperty("java.class.path"),
                        "-Duser.home=" + dir, WebInstallerVersion.class.getName(), cache))
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(dir.resolve("out.txt").toFile());
        Map<String, String> env = builder.environment();
        env.remove("CAMEL_SELF_UPDATE_CHECK");
        // where the web installer installs (POSIX and Windows)
        env.put("XDG_DATA_HOME", dir.toString());
        env.put("LOCALAPPDATA", dir.toString());
        env.put("CAMEL_SELF_UPDATE_MANIFEST_BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort());
        Process process = builder.start();
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as("exit code, output: " + Files.readString(dir.resolve("out.txt"))).isZero();
        Properties properties = new Properties();
        try (var in = Files.newInputStream(Path.of(Files.readString(dir.resolve("cache-file.txt"))))) {
            properties.load(in);
        }
        return properties;
    }

    /**
     * camel version of a launcher installed by the web installer.
     */
    public static final class WebInstallerVersion {

        public static void main(String... args) throws Exception {
            Path root = InstallDetector.webInstallerVersionsRoot();
            Path cache = root.getParent().resolve("update-check.properties");
            if ("stale".equals(args[0])) {
                // checked a day and more ago, which found 4.0.0
                Files.createDirectories(cache.getParent());
                Files.writeString(cache, "last_checked=1\nlatest_version=4.0.0\n");
            }
            Files.writeString(Path.of("cache-file.txt"), cache.toString());
            Path jar = root.resolve("4.0.0").resolve("lib").resolve("camel-launcher.jar");
            System.setProperty(LauncherHelper.CAMEL_LAUNCHER_JAR_PROPERTY, jar.toString());
            new CamelLauncherMain().execute("version");
        }
    }
}
