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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * JDK-only tool that computes SHA-256 checksums for a release TAR/ZIP pair and writes the
 * website's four-field release manifest ({@code format}, {@code version}, {@code tar_sha256},
 * {@code zip_sha256}). Writes are atomic.
 *
 * <p>
 * Manifests live in {@code <output>/releases}:
 * <ul>
 * <li>{@code X.Y.Z.properties}: one per release, immutable.</li>
 * <li>{@code X.Y.properties}: the newest release of the {@code X.Y} line.</li>
 * <li>{@code lts.properties}: the newest LTS release.</li>
 * <li>{@code latest.properties}: the newest release.</li>
 * </ul>
 * An alias is a byte-identical copy of the {@code X.Y.Z.properties} it points at. When publishing,
 * aliases only move forward to a higher version (numeric ordering) and never silently change the
 * checksums of the version they already point at, so a patch on an older LTS line (for example
 * 4.18.6 after 4.22.2) leaves {@code lts.properties} unchanged.
 *
 * <p>
 * Publish a release: {@code java WebsiteManifestGenerator.java --version X.Y.Z --tar <path>
 * --zip <path> --output <dir> --latest <true|false> [--lts <true|false>] [--install-sh <path>
 * --install-ps1 <path>]}
 *
 * <p>
 * Rebuild every alias from the existing {@code X.Y.Z.properties} files and the list of LTS
 * versions (backfill), or with {@code --check} only verify them and exit with status 1 on drift:
 * {@code java WebsiteManifestGenerator.java --rebuild-aliases --output <dir>
 * --lts-versions <X.Y.Z,...> [--check]}
 */
public class WebsiteManifestGenerator {

    private static final Pattern VERSION_PATTERN = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
    private static final Pattern VERSION_FILE_PATTERN = Pattern.compile("^(\\d+\\.\\d+\\.\\d+)\\.properties$");
    private static final Pattern LINE_ALIAS_FILE_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.properties$");
    private static final String LATEST_FILE = "latest.properties";
    private static final String LTS_FILE = "lts.properties";
    private static final Set<String> REQUIRED_OPTIONS
            = new LinkedHashSet<>(List.of("--version", "--tar", "--zip", "--output", "--latest"));
    private static final Set<String> OPTIONAL_OPTIONS = Set.of("--install-sh", "--install-ps1", "--lts");
    private static final String REBUILD_FLAG = "--rebuild-aliases";
    private static final String CHECK_FLAG = "--check";
    private static final Set<String> REBUILD_REQUIRED_OPTIONS = new LinkedHashSet<>(List.of("--output", "--lts-versions"));
    private static final Set<String> MANIFEST_KEYS
            = new LinkedHashSet<>(List.of("format", "version", "tar_sha256", "zip_sha256"));
    private static final String MANIFEST_FORMAT = "1";

    // Properties-style ASF license header prepended to every generated manifest. These '#' comment
    // lines carry no data and are skipped by this tool's own strict re-parser (parseStrictManifest)
    // and by the installers (install.sh / install.ps1), which tolerate a commented manifest.
    private static final String LICENSE_HEADER
            = "## ---------------------------------------------------------------------------\n"
              + "## Licensed to the Apache Software Foundation (ASF) under one or more\n"
              + "## contributor license agreements.  See the NOTICE file distributed with\n"
              + "## this work for additional information regarding copyright ownership.\n"
              + "## The ASF licenses this file to You under the Apache License, Version 2.0\n"
              + "## (the \"License\"); you may not use this file except in compliance with\n"
              + "## the License.  You may obtain a copy of the License at\n"
              + "##\n"
              + "##      http://www.apache.org/licenses/LICENSE-2.0\n"
              + "##\n"
              + "## Unless required by applicable law or agreed to in writing, software\n"
              + "## distributed under the License is distributed on an \"AS IS\" BASIS,\n"
              + "## WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
              + "## See the License for the specific language governing permissions and\n"
              + "## limitations under the License.\n"
              + "## ---------------------------------------------------------------------------\n";

    public static void main(String[] args) {
        try {
            run(args);
        } catch (UsageException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(2);
        } catch (ConflictException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(String[] args) throws IOException {
        if (Arrays.asList(args).contains(REBUILD_FLAG)) {
            rebuildAliases(args);
        } else {
            publish(args);
        }
    }

    private static void publish(String[] args) throws IOException {
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

        Path installSh = null;
        Path installPs1 = null;
        String installShValue = options.get("--install-sh");
        if (installShValue != null) {
            installSh = Paths.get(installShValue);
            if (!Files.isRegularFile(installSh)) {
                throw new UsageException("install.sh not found: " + installSh);
            }
        }
        String installPs1Value = options.get("--install-ps1");
        if (installPs1Value != null) {
            installPs1 = Paths.get(installPs1Value);
            if (!Files.isRegularFile(installPs1)) {
                throw new UsageException("install.ps1 not found: " + installPs1);
            }
        }

        boolean latest = parseBoolean("--latest", options.get("--latest"));
        boolean lts = options.containsKey("--lts") && parseBoolean("--lts", options.get("--lts"));

        Path output = Paths.get(options.get("--output"));

        String tarSha256 = sha256Hex(tar);
        String zipSha256 = sha256Hex(zip);
        byte[] manifest = renderManifest(version, tarSha256, zipSha256);

        Path releasesDir = output.resolve("releases");
        if (!latest) {
            rejectNewHighestWithoutLatest(releasesDir, version);
        }
        Files.createDirectories(releasesDir);
        writeVersionManifest(releasesDir.resolve(version + ".properties"), manifest, version);

        // Validate every alias before touching any of them, so a rejected alias (for example latest
        // moving backward) does not leave the others half-updated. The immutable version manifest
        // above is still written, as before.
        Map<Path, byte[]> aliasUpdates = new LinkedHashMap<>();
        // An older release (a late patch on the same line, or a patch on an older LTS line) leaves the
        // X.Y and lts aliases where they are. latest is only requested for a new release, so asking
        // to move it backward is an error.
        planForwardOnlyAlias(releasesDir.resolve(lineOf(version) + ".properties"), manifest, version, false,
                aliasUpdates);
        if (lts) {
            planForwardOnlyAlias(releasesDir.resolve(LTS_FILE), manifest, version, false, aliasUpdates);
        }
        if (latest) {
            planForwardOnlyAlias(releasesDir.resolve(LATEST_FILE), manifest, version, true, aliasUpdates);
        }
        for (Map.Entry<Path, byte[]> update : aliasUpdates.entrySet()) {
            atomicWrite(update.getKey(), update.getValue());
        }

        if (installSh != null && installPs1 != null) {
            writeInstallChecksums(output.getParent(), installSh, installPs1);
        }
    }

    /**
     * {@code --rebuild-aliases --check} expects {@code latest.properties} to match the highest {@code X.Y.Z} manifest.
     * Publishing a version higher than every existing manifest without {@code --latest true} would leave
     * {@code latest} behind and fail that check after the manifests were written, so refuse it before writing anything.
     * An empty (or missing) releases directory, such as a fresh staging build, has nothing to compare with.
     */
    private static void rejectNewHighestWithoutLatest(Path releasesDir, String version) throws IOException {
        if (!Files.isDirectory(releasesDir)) {
            return;
        }
        String highest = null;
        try (Stream<Path> files = Files.list(releasesDir)) {
            for (Path file : files.toList()) {
                Matcher m = VERSION_FILE_PATTERN.matcher(file.getFileName().toString());
                if (m.matches() && (highest == null || compareSemver(m.group(1), highest) > 0)) {
                    highest = m.group(1);
                }
            }
        }
        if (highest != null && compareSemver(version, highest) > 0) {
            throw new UsageException(version + " is higher than every published release (highest is " + highest
                                     + "), so latest.properties must move to it - pass --latest true.");
        }
    }

    private static boolean parseBoolean(String option, String value) {
        if ("true".equals(value)) {
            return true;
        } else if ("false".equals(value)) {
            return false;
        }
        throw new UsageException(option + " must be 'true' or 'false' (got '" + value + "').");
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> options = new LinkedHashMap<>();
        int i = 0;
        while (i < args.length) {
            String key = args[i];
            if (!REQUIRED_OPTIONS.contains(key) && !OPTIONAL_OPTIONS.contains(key)) {
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
        String content = LICENSE_HEADER
                          + "format=" + MANIFEST_FORMAT + "\n"
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

    /**
     * Checks whether {@code aliasFile} moves to {@code version} and, if it needs to change, records the new content in
     * {@code updates}. It never moves backward: an older {@code version} is an error when {@code failIfOlder} is set,
     * and leaves the alias unchanged otherwise. Different checksums for the version it already points at are always an
     * error.
     */
    private static void planForwardOnlyAlias(
            Path aliasFile, byte[] manifest, String version, boolean failIfOlder, Map<Path, byte[]> updates)
            throws IOException {
        String aliasName = aliasFile.getFileName().toString();
        if (!Files.exists(aliasFile)) {
            updates.put(aliasFile, manifest);
            return;
        }

        byte[] existing = Files.readAllBytes(aliasFile);
        if (Arrays.equals(existing, manifest)) {
            return;
        }

        Map<String, String> existingFields = parseStrictManifest(existing, aliasFile);
        String existingVersion = existingFields.get("version");
        if (LINE_ALIAS_FILE_PATTERN.matcher(aliasName).matches()
                && !(lineOf(existingVersion) + ".properties").equals(aliasName)) {
            throw new ConflictException(aliasName + " points at version " + existingVersion
                                         + ", which is not on that release line - refusing to update it.");
        }
        int cmp = compareSemver(version, existingVersion);
        if (cmp < 0 && !failIfOlder) {
            System.out.println(aliasName + " stays at " + existingVersion + " (newer than " + version + ").");
            return;
        } else if (cmp < 0) {
            throw new ConflictException(
                    aliasName + " already points at a newer version (" + existingVersion + "); refusing to"
                                         + " move backward to " + version + ".");
        } else if (cmp == 0) {
            throw new ConflictException(aliasName + " already has different checksums for version " + version
                                         + " - refusing to overwrite.");
        }
        updates.put(aliasFile, manifest);
    }

    /**
     * Regenerates {@code latest}, {@code lts} and every {@code X.Y} alias from the immutable {@code X.Y.Z.properties}
     * files. Unlike publishing, this is not forward-only: the immutable manifests and the LTS list are the source of
     * truth. With {@code --check} nothing is written and any drift fails with status 1.
     */
    private static void rebuildAliases(String[] args) throws IOException {
        Map<String, String> options = new LinkedHashMap<>();
        boolean check = false;
        int i = 0;
        while (i < args.length) {
            String key = args[i];
            if (REBUILD_FLAG.equals(key) || CHECK_FLAG.equals(key)) {
                if (CHECK_FLAG.equals(key)) {
                    check = true;
                }
                i++;
                continue;
            }
            if (!REBUILD_REQUIRED_OPTIONS.contains(key)) {
                throw new UsageException("unknown option '" + key + "' for " + REBUILD_FLAG + ".");
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
        for (String required : REBUILD_REQUIRED_OPTIONS) {
            if (!options.containsKey(required)) {
                throw new UsageException("missing required option '" + required + "'.");
            }
        }

        Set<String> ltsVersions = new LinkedHashSet<>();
        for (String entry : options.get("--lts-versions").split(",", -1)) {
            String v = entry.trim();
            if (v.isEmpty()) {
                continue;
            }
            if (!VERSION_PATTERN.matcher(v).matches()) {
                throw new UsageException("'" + v + "' in --lts-versions is not a valid release version (expected X.Y.Z).");
            }
            ltsVersions.add(v);
        }

        Path releasesDir = Paths.get(options.get("--output")).resolve("releases");
        if (!Files.isDirectory(releasesDir)) {
            throw new UsageException("releases directory not found: " + releasesDir);
        }

        // Read every immutable manifest and every existing alias.
        Map<String, byte[]> versions = new TreeMap<>(WebsiteManifestGenerator::compareSemver);
        Set<String> existingAliases = new TreeSet<>();
        try (Stream<Path> files = Files.list(releasesDir)) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                Matcher m = VERSION_FILE_PATTERN.matcher(name);
                if (m.matches()) {
                    byte[] bytes = Files.readAllBytes(file);
                    String declared = parseStrictManifest(bytes, file).get("version");
                    if (!declared.equals(m.group(1))) {
                        throw new ConflictException(file + " declares version " + declared + ", expected " + m.group(1) + ".");
                    }
                    versions.put(declared, bytes);
                } else if (LATEST_FILE.equals(name) || LTS_FILE.equals(name)
                        || LINE_ALIAS_FILE_PATTERN.matcher(name).matches()) {
                    existingAliases.add(name);
                }
            }
        }
        if (versions.isEmpty()) {
            throw new ConflictException("no X.Y.Z.properties manifests found in " + releasesDir + ".");
        }
        for (String v : ltsVersions) {
            if (!versions.containsKey(v)) {
                throw new ConflictException("LTS version " + v + " has no manifest (" + releasesDir.resolve(v + ".properties")
                                             + ") - refusing to pick another version.");
            }
        }

        // Expected alias -> version. versions is sorted ascending, so later entries win.
        Map<String, String> expected = new TreeMap<>();
        for (String v : versions.keySet()) {
            expected.put(lineOf(v) + ".properties", v);
            expected.put(LATEST_FILE, v);
            if (ltsVersions.contains(v)) {
                expected.put(LTS_FILE, v);
            }
        }

        List<String> drift = new ArrayList<>();
        Map<Path, byte[]> writes = new LinkedHashMap<>();
        for (Map.Entry<String, String> alias : expected.entrySet()) {
            Path file = releasesDir.resolve(alias.getKey());
            byte[] want = versions.get(alias.getValue());
            if (!Files.exists(file)) {
                drift.add(alias.getKey() + " is missing (expected " + alias.getValue() + ")");
                writes.put(file, want);
                continue;
            }
            byte[] have = Files.readAllBytes(file);
            if (!Arrays.equals(have, want)) {
                String described;
                try {
                    described = "points at " + parseStrictManifest(have, file).get("version");
                } catch (ConflictException e) {
                    described = "is malformed";
                }
                drift.add(alias.getKey() + " " + described + " (expected a copy of " + alias.getValue() + ".properties)");
                writes.put(file, want);
            }
        }
        List<String> unexpected = new ArrayList<>();
        for (String name : existingAliases) {
            if (!expected.containsKey(name)) {
                unexpected.add(name);
                drift.add(name + " is not expected (no matching release manifest or LTS version)");
            }
        }

        if (check) {
            if (!drift.isEmpty()) {
                for (String d : drift) {
                    System.err.println("drift: " + d);
                }
                throw new ConflictException(drift.size() + " alias manifest(s) in " + releasesDir
                                             + " are out of date; regenerate them with " + REBUILD_FLAG + ".");
            }
            System.out.println("All " + expected.size() + " alias manifests in " + releasesDir + " are up to date.");
            return;
        }

        if (!unexpected.isEmpty()) {
            throw new ConflictException("unexpected alias manifest(s) " + unexpected + " in " + releasesDir
                                         + " - refusing to delete published files; remove them manually.");
        }
        for (Map.Entry<Path, byte[]> write : writes.entrySet()) {
            atomicWrite(write.getKey(), write.getValue());
            System.out.println("wrote " + write.getKey().getFileName() + " -> "
                               + expected.get(write.getKey().getFileName().toString()));
        }
        if (writes.isEmpty()) {
            System.out.println("All " + expected.size() + " alias manifests in " + releasesDir + " are up to date.");
        }
    }

    // install.sh/install.ps1 aren't versioned the way the release archive is - they always describe
    // "how to install whatever is currently latest," so unlike writeVersionManifest/planForwardOnlyAlias
    // there's no immutability or monotonic-version invariant to enforce here: this file is simply
    // overwritten every release with the checksums of whatever install.sh/install.ps1 currently are.
    private static void writeInstallChecksums(Path websiteRoot, Path installSh, Path installPs1) throws IOException {
        String content = "install_sh_sha256=" + sha256Hex(installSh) + "\n"
                           + "install_ps1_sha256=" + sha256Hex(installPs1) + "\n";
        atomicWrite(websiteRoot.resolve("install.sha256"), content.getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseStrictManifest(byte[] bytes, Path source) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : content.split("\n", -1)) {
            // Skip blank lines and '#' comment lines (e.g. the ASF license header this tool now
            // prepends), so re-reading a previously generated latest.properties stays valid.
            if (line.isEmpty() || line.startsWith("#")) {
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
        if (!fields.keySet().equals(MANIFEST_KEYS)) {
            throw new ConflictException("malformed manifest in " + source + ": expected keys " + MANIFEST_KEYS
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

    private static String lineOf(String version) {
        int[] parts = semverParts(version);
        return parts[0] + "." + parts[1];
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
