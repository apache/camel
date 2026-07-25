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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

/**
 * Test fixture that starts a loopback-only HTTPS server serving website-installer manifests and archive payloads, plus
 * builders for safe and deliberately malicious TAR/ZIP archives. Used to exercise install.sh / install.ps1 without ever
 * contacting a production endpoint.
 */
final class WebsiteInstallerFixture implements AutoCloseable {

    private static final String KEYSTORE_PASSWORD = "camel-installer-test";
    private static final String KEY_ALIAS = "camel-installer-test";
    private static final String DEFAULT_BASE_VERSION = "9.9.9";
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(60);

    record Result(int exit, String stdout, String stderr) {
    }

    private record ArchiveEntrySpec(String name, byte[] content, boolean directory, boolean executable,
            String symlinkTarget) {
        static ArchiveEntrySpec dir(String name) {
            return new ArchiveEntrySpec(name, null, true, false, null);
        }

        static ArchiveEntrySpec file(String name, byte[] content, boolean executable) {
            return new ArchiveEntrySpec(name, content, false, executable, null);
        }

        static ArchiveEntrySpec symlink(String name, String target) {
            return new ArchiveEntrySpec(name, null, false, false, target);
        }
    }

    private final HttpsServer server;
    private final Path tempDir;
    private final Path caCertPem;
    private final Map<String, byte[]> content = new ConcurrentHashMap<>();

    private WebsiteInstallerFixture(HttpsServer server, Path tempDir, Path caCertPem) {
        this.server = server;
        this.tempDir = tempDir;
        this.caCertPem = caCertPem;
    }

    static WebsiteInstallerFixture start(Path temp) throws Exception {
        Files.createDirectories(temp);
        Path keystore = temp.resolve("installer-test.p12");
        Path caCertPem = temp.resolve("installer-test-ca.pem");
        generateSelfSignedKeystore(keystore, caCertPem);

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(keystore)) {
            keyStore.load(in, KEYSTORE_PASSWORD.toCharArray());
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray());
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), null, new SecureRandom());

        HttpsServer server = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext));

        WebsiteInstallerFixture fixture = new WebsiteInstallerFixture(server, temp, caCertPem);
        server.createContext("/", fixture::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        return fixture;
    }

    private static void generateSelfSignedKeystore(Path keystore, Path caCertPem) throws Exception {
        runTool(new ProcessBuilder(
                keytool(),
                "-genkeypair",
                "-alias", KEY_ALIAS,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-keystore", keystore.toString(),
                "-storetype", "PKCS12",
                "-storepass", KEYSTORE_PASSWORD,
                "-keypass", KEYSTORE_PASSWORD,
                "-dname", "CN=127.0.0.1",
                "-ext", "san=ip:127.0.0.1"));

        runTool(new ProcessBuilder(
                keytool(),
                "-exportcert",
                "-alias", KEY_ALIAS,
                "-keystore", keystore.toString(),
                "-storetype", "PKCS12",
                "-storepass", KEYSTORE_PASSWORD,
                "-rfc",
                "-file", caCertPem.toString()));
    }

    private static String keytool() {
        String exe = FakeJava.WINDOWS ? "keytool.exe" : "keytool";
        return Path.of(System.getProperty("java.home"), "bin", exe).toString();
    }

    private static void runTool(ProcessBuilder pb) throws Exception {
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("keytool did not finish in time");
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
    }

    String baseUrl() {
        return "https://127.0.0.1:" + server.getAddress().getPort();
    }

    String mavenUrl() {
        return baseUrl() + "/maven2/org/apache/camel/camel-launcher";
    }

    Path caCertificate() {
        return caCertPem;
    }

    Map<String, String> environment(Path home) {
        Map<String, String> env = new HashMap<>();
        env.put("CAMEL_INSTALL_MANIFEST_BASE_URL", baseUrl() + "/camel-cli/releases");
        env.put("CAMEL_INSTALL_MAVEN_BASE_URL", mavenUrl());
        env.put("CAMEL_INSTALL_CA_CERT", caCertPem.toString());
        env.put("HOME", home.toString());
        env.put("USERPROFILE", home.toString());
        env.put("LOCALAPPDATA", home.resolve("AppData").resolve("Local").toString());
        return env;
    }

    void publish(String urlPath, byte[] body) {
        content.put(urlPath, body);
    }

    void publishManifest(String urlPath, String version, Path tar, Path zip) throws Exception {
        publishManifest(urlPath, version, tar, zip, "");
    }

    /** Publishes a manifest optionally prefixed with a comment {@code header}, to exercise comment tolerance. */
    void publishManifest(String urlPath, String version, Path tar, Path zip, String header) throws Exception {
        String manifest = header
                          + "format=1\n"
                          + "version=" + version + "\n"
                          + "tar_sha256=" + sha256Hex(tar) + "\n"
                          + "zip_sha256=" + sha256Hex(zip) + "\n";
        publish(urlPath, manifest.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    Path safeTar(String version) throws Exception {
        Path archive = Files.createTempFile(tempDir, "safe-", "-" + version + ".tar.gz");
        writeTarGz(archive, launcherEntries(version));
        return archive;
    }

    Path safeZip(String version) throws Exception {
        Path archive = Files.createTempFile(tempDir, "safe-", "-" + version + ".zip");
        writeZip(archive, launcherEntries(version));
        return archive;
    }

    Path maliciousTar(String entry) throws Exception {
        List<ArchiveEntrySpec> entries = new ArrayList<>(launcherEntries(DEFAULT_BASE_VERSION));
        entries.add(ArchiveEntrySpec.file(entry, "malicious".getBytes(StandardCharsets.UTF_8), false));
        Path archive = Files.createTempFile(tempDir, "malicious-", ".tar.gz");
        writeTarGz(archive, entries);
        return archive;
    }

    Path maliciousZip(String entry) throws Exception {
        List<ArchiveEntrySpec> entries = new ArrayList<>(launcherEntries(DEFAULT_BASE_VERSION));
        entries.add(ArchiveEntrySpec.file(entry, "malicious".getBytes(StandardCharsets.UTF_8), false));
        Path archive = Files.createTempFile(tempDir, "malicious-", ".zip");
        writeZip(archive, entries);
        return archive;
    }

    Path safeTarWithFailingLauncher(String version) throws Exception {
        Path archive = Files.createTempFile(tempDir, "failing-launcher-", "-" + version + ".tar.gz");
        writeTarGz(archive, launcherEntries(version, 1));
        return archive;
    }

    Path safeZipWithFailingLauncher(String version) throws Exception {
        Path archive = Files.createTempFile(tempDir, "failing-launcher-", "-" + version + ".zip");
        writeZip(archive, launcherEntries(version, 1));
        return archive;
    }

    Path safeTarMissingCamelSh(String version) throws Exception {
        String root = "camel-launcher-" + version;
        List<ArchiveEntrySpec> entries = List.of(
                ArchiveEntrySpec.dir(root + "/"),
                ArchiveEntrySpec.dir(root + "/bin/"),
                ArchiveEntrySpec.file(root + "/bin/camel.bat", batScript(version, 0).getBytes(StandardCharsets.UTF_8),
                        false));
        Path archive = Files.createTempFile(tempDir, "missing-camel-sh-", "-" + version + ".tar.gz");
        writeTarGz(archive, entries);
        return archive;
    }

    Path safeZipMissingCamelBat(String version) throws Exception {
        String root = "camel-launcher-" + version;
        List<ArchiveEntrySpec> entries = List.of(
                ArchiveEntrySpec.dir(root + "/"),
                ArchiveEntrySpec.dir(root + "/bin/"),
                ArchiveEntrySpec.file(root + "/bin/camel.sh", shScript(version, 0).getBytes(StandardCharsets.UTF_8), true));
        Path archive = Files.createTempFile(tempDir, "missing-camel-bat-", "-" + version + ".zip");
        writeZip(archive, entries);
        return archive;
    }

    Path maliciousTarSymlink(String entry, String linkTarget) throws Exception {
        List<ArchiveEntrySpec> entries = new ArrayList<>(launcherEntries(DEFAULT_BASE_VERSION));
        entries.add(ArchiveEntrySpec.symlink(entry, linkTarget));
        Path archive = Files.createTempFile(tempDir, "malicious-symlink-", ".tar.gz");
        writeTarGz(archive, entries);
        return archive;
    }

    Path maliciousZipSymlink(String entry, String linkTarget) throws Exception {
        List<ArchiveEntrySpec> entries = new ArrayList<>(launcherEntries(DEFAULT_BASE_VERSION));
        entries.add(ArchiveEntrySpec.symlink(entry, linkTarget));
        Path archive = Files.createTempFile(tempDir, "malicious-symlink-", ".zip");
        writeZip(archive, entries);
        return archive;
    }

    private static List<ArchiveEntrySpec> launcherEntries(String version) throws Exception {
        return launcherEntries(version, 0);
    }

    private static List<ArchiveEntrySpec> launcherEntries(String version, int versionExitCode) throws Exception {
        String root = "camel-launcher-" + version;
        byte[] sh = shScript(version, versionExitCode).getBytes(StandardCharsets.UTF_8);
        byte[] bat = batScript(version, versionExitCode).getBytes(StandardCharsets.UTF_8);
        return List.of(
                ArchiveEntrySpec.dir(root + "/"),
                ArchiveEntrySpec.dir(root + "/bin/"),
                ArchiveEntrySpec.file(root + "/bin/camel.sh", sh, true),
                ArchiveEntrySpec.file(root + "/bin/camel.bat", bat, false));
    }

    private static String shScript(String version, int versionExitCode) {
        return "#!/bin/sh\n"
               + "case \"$1\" in\n"
               + "  version) echo \"Camel " + version + "\"; exit " + versionExitCode + " ;;\n"
               + "  echo-args) shift; echo \"$@\"; exit 0 ;;\n"
               + "  exit-code) exit \"$2\" ;;\n"
               + "esac\n"
               + "echo \"Camel " + version + "\"\n";
    }

    private static String batScript(String version, int versionExitCode) {
        return "@echo off\r\n"
               + "if \"%~1\"==\"version\" (echo Camel " + version + "& exit /b " + versionExitCode + ")\r\n"
               + "if \"%~1\"==\"echo-args\" (shift & echo %*& exit /b 0)\r\n"
               + "if \"%~1\"==\"exit-code\" (exit /b %2)\r\n"
               + "echo Camel " + version + "\r\n";
    }

    private void writeTarGz(Path archive, List<ArchiveEntrySpec> entries) throws IOException {
        try (var fos = Files.newOutputStream(archive);
             var gzos = new GzipCompressorOutputStream(fos);
             var taos = new TarArchiveOutputStream(gzos)) {
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            taos.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            for (ArchiveEntrySpec spec : entries) {
                writeTarEntry(taos, spec);
            }
        }
    }

    private void writeTarEntry(TarArchiveOutputStream taos, ArchiveEntrySpec spec) throws IOException {
        if (spec.symlinkTarget() != null) {
            TarArchiveEntry entry = new TarArchiveEntry(spec.name(), TarConstants.LF_SYMLINK, true);
            entry.setLinkName(spec.symlinkTarget());
            taos.putArchiveEntry(entry);
            taos.closeArchiveEntry();
            return;
        }
        if (spec.directory()) {
            String name = spec.name().endsWith("/") ? spec.name() : spec.name() + "/";
            taos.putArchiveEntry(new TarArchiveEntry(name, true));
            taos.closeArchiveEntry();
            return;
        }
        TarArchiveEntry entry = new TarArchiveEntry(spec.name(), true);
        entry.setSize(spec.content().length);
        entry.setMode(spec.executable() ? 0100755 : 0100644);
        taos.putArchiveEntry(entry);
        taos.write(spec.content());
        taos.closeArchiveEntry();
    }

    private void writeZip(Path archive, List<ArchiveEntrySpec> entries) throws IOException {
        try (var fos = Files.newOutputStream(archive);
             var zaos = new ZipArchiveOutputStream(fos)) {
            for (ArchiveEntrySpec spec : entries) {
                writeZipEntry(zaos, spec);
            }
        }
    }

    private void writeZipEntry(ZipArchiveOutputStream zaos, ArchiveEntrySpec spec) throws IOException {
        if (spec.symlinkTarget() != null) {
            ZipArchiveEntry entry = new ZipArchiveEntry(spec.name());
            entry.setUnixMode(0120777); // S_IFLNK | rwxrwxrwx
            byte[] target = spec.symlinkTarget().getBytes(StandardCharsets.UTF_8);
            zaos.putArchiveEntry(entry);
            zaos.write(target);
            zaos.closeArchiveEntry();
            return;
        }
        if (spec.directory()) {
            String name = spec.name().endsWith("/") ? spec.name() : spec.name() + "/";
            zaos.putArchiveEntry(new ZipArchiveEntry(name));
            zaos.closeArchiveEntry();
            return;
        }
        ZipArchiveEntry entry = new ZipArchiveEntry(spec.name());
        entry.setUnixMode(spec.executable() ? 0100755 : 0100644);
        zaos.putArchiveEntry(entry);
        zaos.write(spec.content());
        zaos.closeArchiveEntry();
    }

    Result run(List<String> command, Map<String, String> env) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().clear();
        pb.environment().put("PATH", System.getenv("PATH"));
        if (FakeJava.WINDOWS) {
            String sysRoot = System.getenv("SystemRoot");
            if (sysRoot != null) {
                pb.environment().put("SystemRoot", sysRoot);
            }
            pb.environment().put("OS", "Windows_NT");
            String pathExt = System.getenv("PATHEXT");
            if (pathExt != null) {
                pb.environment().put("PATHEXT", pathExt);
            }
        }
        pb.environment().putAll(env);
        String home = env.get("HOME");
        if (home != null && Files.isDirectory(Path.of(home))) {
            // Any accidental relative-path side effect lands in the isolated test HOME rather than
            // wherever the test JVM happens to be running from.
            pb.directory(Path.of(home).toFile());
        }

        Process process = pb.start();
        ExecutorService collectors = Executors.newFixedThreadPool(2);
        try {
            Future<byte[]> stdout = collectors.submit(() -> process.getInputStream().readAllBytes());
            Future<byte[]> stderr = collectors.submit(() -> process.getErrorStream().readAllBytes());
            process.getOutputStream().close();
            if (!process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("installer did not exit in time");
            }
            String out = new String(await(stdout), StandardCharsets.UTF_8);
            String err = new String(await(stderr), StandardCharsets.UTF_8);
            return new Result(process.exitValue(), out, err);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            collectors.shutdownNow();
            collectors.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private static byte[] await(Future<byte[]> collector) throws Exception {
        try {
            return collector.get(10, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("failed to collect installer output", cause);
        } catch (TimeoutException e) {
            throw new IllegalStateException("timed out collecting installer output", e);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            byte[] body = content.get(path);
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        } finally {
            exchange.close();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
