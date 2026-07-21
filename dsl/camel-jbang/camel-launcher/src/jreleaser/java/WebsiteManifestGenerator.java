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

// Intentionally in the default package so camel-package.sh can run this JDK-only source-file
// tool directly by path without compiling or loading a package-qualified launcher class.

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDK-only tool that computes SHA-256 checksums for a release TAR/ZIP pair and writes the
 * website's four-field release manifest ({@code format}, {@code version}, {@code tar_sha256},
 * {@code zip_sha256}). Per-version manifests are immutable; {@code latest.properties} can only
 * move forward to a higher semantic version and cannot silently change checksums for the same
 * version. Writes are atomic.
 *
 * <p>
 * Usage: {@code java WebsiteManifestGenerator.java --version X.Y.Z --tar <path> --zip <path>
 * --output <dir> --latest <true|false>}
 */
public class WebsiteManifestGenerator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Set<String> REQUIRED_OPTIONS
            = new LinkedHashSet<>(List.of("--version", "--tar", "--zip", "--output", "--latest"));
    private static final String MANIFEST_FORMAT = "1";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (UsageException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        } catch (ConflictException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        Map<String, String> options = parseArgs(args);

        String version = options.get("--version");
        if (!VERSION_PATTERN.matcher(version).matches()) {
            throw new UsageException("'" + version + "' is not a valid release version (expected X.Y.Z).");
        }

        Path tar = Paths.get(options.get("--tar"));
        Path zip = Paths.get(options.get("--zip"));
        if (!Files.isRegularFile(tar)) {
            throw new UsageException("TAR artifact not found: " + tar);
        }
        if (!Files.isRegularFile(zip)) {
            throw new UsageException("ZIP artifact not found: " + zip);
        }

        String latestValue = options.get("--latest");
        boolean latest;
        if ("true".equals(latestValue)) {
            latest = true;
        } else if ("false".equals(latestValue)) {
            latest = false;
        } else {
            throw new UsageException("--latest must be 'true' or 'false' (got '" + latestValue + "').");
        }

        Path output = Paths.get(options.get("--output"));

        String tarSha256 = sha256Hex(tar);
        String zipSha256 = sha256Hex(zip);
        byte[] manifest = renderManifest(version, tarSha256, zipSha256);

        Path releasesDir = output.resolve("releases");
        Files.createDirectories(releasesDir);
        writeVersionManifest(releasesDir.resolve(version + ".properties"), manifest, version);

        if (latest) {
            writeLatestManifest(releasesDir.resolve("latest.properties"), manifest, version);
        }
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        int i = 0;
        while (i < args.length) {
            String key = args[i];
            if (!REQUIRED_OPTIONS.contains(key)) {
                throw new UsageException("unknown option '" + key + "'.");
            }
            if (i + 1 >= args.length) {
                throw new UsageException("option '" + key + "' requires a value.");
            }
            if (options.containsKey(key)) {
                throw new UsageException("option '" + key + "' was specified more than once.");
            }
            options.put(key, args[i + 1]);
            i += 2;
        }
        for (String required : REQUIRED_OPTIONS) {
            if (!options.containsKey(required)) {
                throw new UsageException("missing required option '" + required + "'.");
            }
        }
        return options;
    }

    private static byte[] renderManifest(String version, String tarSha256, String zipSha256) {
        String content = "format=" + MANIFEST_FORMAT + "\n"
                          + "version=" + version + "\n"
                          + "tar_sha256=" + tarSha256 + "\n"
                          + "zip_sha256=" + zipSha256 + "\n";
        return content.getBytes(StandardCharsets.UTF_8);
    }

    private static void writeVersionManifest(Path versionFile, byte[] manifest, String version) throws IOException {
        if (Files.exists(versionFile)) {
            byte[] existing = Files.readAllBytes(versionFile);
            if (Arrays.equals(existing, manifest)) {
                return;
            }
            throw new ConflictException("version manifest for " + version + " already exists with different content"
                                         + " (" + versionFile + ") - version manifests are immutable.");
        }
        atomicWrite(versionFile, manifest);
    }

    private static void writeLatestManifest(Path latestFile, byte[] manifest, String version) throws IOException {
        if (!Files.exists(latestFile)) {
            atomicWrite(latestFile, manifest);
            return;
        }

        byte[] existing = Files.readAllBytes(latestFile);
        if (Arrays.equals(existing, manifest)) {
            return;
        }

        Map<String, String> existingFields = parseStrictManifest(existing, latestFile);
        String existingVersion = existingFields.get("version");
        int cmp = compareSemver(version, existingVersion);
        if (cmp < 0) {
            throw new ConflictException(
                    "latest.properties already points at a newer version (" + existingVersion + "); refusing to"
                                         + " move backward to " + version + ".");
        } else if (cmp == 0) {
            throw new ConflictException("latest.properties already has different checksums for version " + version
                                         + " - refusing to overwrite.");
        }
        atomicWrite(latestFile, manifest);
    }

    private static Map<String, String> parseStrictManifest(byte[] bytes, Path source) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : content.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new ConflictException("malformed manifest line in " + source + ": '" + line + "'.");
            }
            String key = line.substring(0, eq);
            if (fields.containsKey(key)) {
                throw new ConflictException("malformed manifest in " + source + ": duplicate key '" + key + "'.");
            }
            fields.put(key, line.substring(eq + 1));
        }
        Set<String> expectedKeys = new LinkedHashSet<>(List.of("format", "version", "tar_sha256", "zip_sha256"));
        if (!fields.keySet().equals(expectedKeys)) {
            throw new ConflictException("malformed manifest in " + source + ": expected keys " + expectedKeys
                                         + " but found " + fields.keySet() + ".");
        }
        if (!MANIFEST_FORMAT.equals(fields.get("format"))) {
            throw new ConflictException("malformed manifest in " + source + ": unsupported format '"
                                         + fields.get("format") + "'.");
        }
        if (!VERSION_PATTERN.matcher(fields.get("version")).matches()) {
            throw new ConflictException("malformed manifest in " + source + ": invalid version '"
                                         + fields.get("version") + "'.");
        }
        assertSha256(fields.get("tar_sha256"), source, "tar_sha256");
        assertSha256(fields.get("zip_sha256"), source, "zip_sha256");
        return fields;
    }

    private static void assertSha256(String value, Path source, String key) {
        if (!value.matches("[0-9a-f]{64}")) {
            throw new ConflictException("malformed manifest in " + source + ": invalid " + key + " '" + value + "'.");
        }
    }

    private static int compareSemver(String a, String b) {
        int[] pa = semverParts(a);
        int[] pb = semverParts(b);
        for (int i = 0; i < 3; i++) {
            int c = Integer.compare(pa[i], pb[i]);
            if (c != 0) {
                return c;
            }
        }
        return 0;
    }

    private static int[] semverParts(String version) {
        Matcher m = VERSION_PATTERN.matcher(version);
        if (!m.matches()) {
            throw new ConflictException("invalid semantic version '" + version + "'.");
        }
        return new int[] { Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)) };
    }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Path tmp = Files.createTempFile(dir, "wmg-", ".tmp");
        try {
            Files.write(tmp, bytes);
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String sha256Hex(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JDK and must always be available.", e);
        }
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class UsageException extends RuntimeException {
        UsageException(String message) {
            super(message);
        }
    }

    private static final class ConflictException extends RuntimeException {
        ConflictException(String message) {
            super(message);
        }
    }
}
