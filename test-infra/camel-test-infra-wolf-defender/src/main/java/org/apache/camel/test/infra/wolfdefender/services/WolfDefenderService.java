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
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import org.apache.camel.test.infra.common.services.TestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Supplies the pinned Wolf-Defender model files for local inference tests. */
public class WolfDefenderService implements TestService {
    public static final String MODEL_DIRECTORY = "wolfDefender.modelDirectory";
    public static final String DOWNLOAD = "wolfDefender.download";
    public static final String CACHE_DIRECTORY = "wolfDefender.cacheDirectory";
    public static final String REVISION = "bcab2eff97bcabd7227849639e2d0d7a61b46c92";

    private static final Logger LOG = LoggerFactory.getLogger(WolfDefenderService.class);
    private static final Object DOWNLOAD_LOCK = new Object();
    private static final URI SOURCE = URI.create(
            "https://huggingface.co/patronus-studio/wolf-defender-prompt-injection-small/resolve/" + REVISION + "/");
    private static final Map<String, String> ARTIFACTS = Map.of(
            "onnx/onnx_fp32/model.onnx", "49455de2407c134dd136c64ca38d67ca17f8426e99fe5c6e286d69af9932993f",
            "tokenizer.json", "7e426c3929b44e6ab4c931770b5f22b913280633f5a1c67c81e9ad64decef55c",
            "config.json", "b5bfba7b100b4b1aa361e8160e5593695164d81ca09b47f3b26561332b520218");

    private final Path directory;
    private final boolean download;
    private final URI source;
    private final Map<String, String> artifacts;
    private boolean initialized;

    /** Uses an existing model directory, or downloads into a persistent cache when explicitly enabled. */
    public WolfDefenderService() {
        this(configuredDirectory(), configuredDownload(), SOURCE, ARTIFACTS);
    }

    /** Uses an existing model directory without downloading or modifying its contents. */
    public WolfDefenderService(Path modelDirectory) {
        this(modelDirectory, false, SOURCE, ARTIFACTS);
    }

    WolfDefenderService(Path directory, boolean download, URI source, Map<String, String> artifacts) {
        this.directory = directory.toAbsolutePath().normalize();
        this.download = download;
        this.source = source;
        this.artifacts = Map.copyOf(artifacts);
    }

    /** Whether real-model tests have been explicitly enabled. This method performs no I/O. */
    public static boolean isEnabled() {
        return existingDirectory() != null || Boolean.getBoolean(DOWNLOAD);
    }

    private static String existingDirectory() {
        String value = System.getProperty(MODEL_DIRECTORY);
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean configuredDownload() {
        return existingDirectory() == null && Boolean.getBoolean(DOWNLOAD);
    }

    private static Path configuredDirectory() {
        String existing = existingDirectory();
        if (existing != null) {
            return Path.of(existing);
        }
        String cache = System.getProperty(CACHE_DIRECTORY);
        Path root = cache == null || cache.isBlank()
                ? Path.of(System.getProperty("user.home"), ".camel-test", "wolf-defender") : Path.of(cache);
        return root.resolve(REVISION);
    }

    @Override
    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            if (download) {
                provisionCache();
            } else {
                for (var artifact : artifacts.entrySet()) {
                    verify(directory.resolve(artifact.getKey()), artifact.getValue());
                }
            }
            initialized = true;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot provision Wolf-Defender model files in " + directory, e);
        }
    }

    private void provisionCache() throws IOException {
        // Serialize callers in this JVM before taking the cross-process cache lock.
        synchronized (DOWNLOAD_LOCK) {
            Files.createDirectories(directory);
            try (var channel = FileChannel.open(directory.resolve(".download.lock"),
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 var lock = channel.lock()) {
                for (var artifact : artifacts.entrySet()) {
                    Path file = directory.resolve(artifact.getKey());
                    if (!Files.isRegularFile(file) || !checksum(file).equals(artifact.getValue())) {
                        download(artifact.getKey(), file, artifact.getValue());
                    }
                }
            }
        }
    }

    private void download(String name, Path file, String expected) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) source.resolve(name).toURL().openConnection();
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(30_000);
        Files.createDirectories(file.getParent());
        // A fixed staging name under the cache lock also replaces partial files left by a killed JVM.
        Path temporary = file.resolveSibling(".wolf-defender-" + file.getFileName() + ".part");
        try {
            LOG.info("Downloading pinned Wolf-Defender artifact {}", name);
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("Cannot download " + name + ": HTTP " + status);
            }
            try (InputStream input = connection.getInputStream()) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            verify(temporary, expected);
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            connection.disconnect();
            Files.deleteIfExists(temporary);
        }
    }

    private static void verify(Path file, String expected) throws IOException {
        if (!checksum(file).equals(expected)) {
            throw new IOException("Wolf-Defender artifact checksum mismatch: " + file.getFileName());
        }
    }

    private static String checksum(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (var input = new DigestInputStream(Files.newInputStream(file), digest)) {
            byte[] buffer = new byte[65_536];
            while (input.read(buffer) != -1) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new IOException("Wolf-Defender artifact verification interrupted");
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /** Returns the verified directory containing the ONNX model, tokenizer and model configuration. */
    public Path getModelDirectory() {
        if (!initialized) {
            throw new IllegalStateException("Wolf-Defender test service has not been initialized");
        }
        return directory;
    }

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void shutdown() {
        // Model files remain available for subsequent test runs; this service owns no inference runtime.
    }
}
