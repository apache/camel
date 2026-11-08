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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects where the currently running Camel CLI launcher lives, and discovers other Camel CLI installations on the same
 * machine, across the web installer and the package managers the launcher is also distributed through (Homebrew,
 * Chocolatey, WinGet, Scoop, SDKMAN) or JBang. Used by {@code camel self-update} to refuse acting on a
 * non-web-installer install, and by {@code camel doctor} to report conflicting installations.
 */
public final class InstallDetector {

    public enum InstallMethod {
        WEB_INSTALLER,
        HOMEBREW,
        CHOCOLATEY,
        WINGET,
        SCOOP,
        SDKMAN,
        JBANG,
        UNKNOWN
    }

    public record InstallInfo(Path location, InstallMethod method) {
    }

    // not FileUtil.isWindows(): FileUtil has a logger, and the self-update check runs this before each command of the
    // launcher, where creating a logger would configure log4j
    private static final boolean IS_WINDOWS
            = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    // A nightly build as the Apache snapshot repository names it, which the web installer uses as its directory name:
    // X.Y.Z-YYYYMMDD.HHMMSS-N, the UTC timestamp and build number of the deploy.
    private static final Pattern NIGHTLY_BUILD
            = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+-([0-9]{8}\\.[0-9]{6})-[0-9]{1,9}");
    private static final DateTimeFormatter NIGHTLY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss");

    private InstallDetector() {
    }

    /**
     * The nightly build the running launcher was installed as by the web installer ({@code camel self-update --version
     * SNAPSHOT}), such as {@code 4.24.0-20261108.143653-24}: the name of its directory under
     * {@link #webInstallerVersionsRoot()}, when that is a nightly build.
     */
    public static Optional<String> runningNightlyBuild() {
        String jarPath = System.getProperty("camel.launcher.jar");
        if (jarPath == null || jarPath.isBlank()) {
            return Optional.empty();
        }
        return nightlyBuildOf(Paths.get(jarPath), webInstallerVersionsRoot());
    }

    /**
     * The nightly build a launcher JAR belongs to: the name of its directory under the web installer's
     * {@code versionsRoot}, when that is a nightly build ({@code X.Y.Z-YYYYMMDD.HHMMSS-N}).
     */
    public static Optional<String> nightlyBuildOf(Path launcherJar, Path versionsRoot) {
        if (launcherJar == null) {
            return Optional.empty();
        }
        Path jar = launcherJar.toAbsolutePath().normalize();
        Path root = versionsRoot.toAbsolutePath().normalize();
        if (!jar.startsWith(root) || jar.getNameCount() <= root.getNameCount()) {
            return Optional.empty();
        }
        String dir = jar.getName(root.getNameCount()).toString();
        return isNightlyBuild(dir) ? Optional.of(dir) : Optional.empty();
    }

    /** Whether {@code value} is a nightly build, {@code X.Y.Z-YYYYMMDD.HHMMSS-N}. */
    public static boolean isNightlyBuild(String value) {
        return value != null && NIGHTLY_BUILD.matcher(value).matches();
    }

    /** When a nightly build was deployed to the snapshot repository (its timestamp is UTC). */
    public static Optional<ZonedDateTime> nightlyBuildTime(String build) {
        Matcher m = build != null ? NIGHTLY_BUILD.matcher(build) : null;
        if (m == null || !m.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(m.group(1), NIGHTLY_TIMESTAMP).atZone(ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    /**
     * Determines where the currently running launcher lives, by resolving the path of the JAR backing this JVM process
     * (set as the {@code camel.launcher.jar} system property by {@code CamelLauncher.main()}).
     */
    public static InstallInfo locate() {
        String jarPath = System.getProperty("camel.launcher.jar");
        if (jarPath == null || jarPath.isBlank()) {
            return new InstallInfo(null, InstallMethod.UNKNOWN);
        }
        Path path = Paths.get(jarPath);
        return new InstallInfo(path, classify(path, webInstallerVersionsRoot()));
    }

    // Package-private overload: takes the web-installer versions root explicitly so tests never touch the real
    // $HOME/$LOCALAPPDATA.
    static InstallMethod classify(Path path, Path webInstallerVersionsRoot) {
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(webInstallerVersionsRoot.toAbsolutePath().normalize())) {
            return InstallMethod.WEB_INSTALLER;
        }
        String normalized = absolute.toString().replace('\\', '/');
        if (normalized.contains("/Cellar/")) {
            return InstallMethod.HOMEBREW;
        }
        if (normalized.toLowerCase().contains("/chocolatey/")) {
            return InstallMethod.CHOCOLATEY;
        }
        if (normalized.toLowerCase().contains("/winget/packages/")) {
            return InstallMethod.WINGET;
        }
        if (normalized.toLowerCase().contains("/scoop/apps/")) {
            return InstallMethod.SCOOP;
        }
        if (normalized.contains("/.sdkman/candidates/")) {
            return InstallMethod.SDKMAN;
        }
        if (normalized.contains("/.jbang/")) {
            return InstallMethod.JBANG;
        }
        return InstallMethod.UNKNOWN;
    }

    /**
     * Root directory the web installer extracts version directories into:
     * {@code ${XDG_DATA_HOME:-$HOME/.local/share}/camel-cli/versions} on POSIX,
     * {@code %LOCALAPPDATA%\Apache Camel\cli\versions} on Windows.
     */
    public static Path webInstallerVersionsRoot() {
        if (IS_WINDOWS) {
            return Paths.get(System.getenv("LOCALAPPDATA"), "Apache Camel", "cli", "versions");
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        Path dataHome = xdgDataHome != null && !xdgDataHome.isBlank()
                ? Paths.get(xdgDataHome) : Paths.get(System.getProperty("user.home"), ".local", "share");
        return dataHome.resolve("camel-cli").resolve("versions");
    }

    /**
     * The raw content of the web installer's pin file, if any: the value a human last passed to {@code install.sh}/
     * {@code install.ps1} as {@code --version}/{@code -Version}. That is an exact release ({@code X.Y.Z}), a release
     * line ({@code X.Y}) or {@code LTS}; the file is cleared on a bare, unversioned run and never written by
     * {@code camel self-update}'s own delegated invocation, which always passes a resolved version for TOCTOU-safety
     * but is not itself a pin request. {@code camel self-update} refuses to move an exact pin (or a value it does not
     * recognise), and moves an {@code X.Y} or {@code LTS} pin only to the newest release of that line or the newest LTS
     * release. This method returns the value as written; interpreting it is up to the caller.
     */
    public static Optional<String> pinnedVersion() {
        return pinnedVersion(webInstallerVersionsRoot());
    }

    // Package-private overload: takes the web-installer versions root explicitly so tests never touch the real
    // $HOME/$LOCALAPPDATA.
    static Optional<String> pinnedVersion(Path webInstallerVersionsRoot) {
        Path file = pinnedVersionFile(webInstallerVersionsRoot);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8).strip();
            return content.isEmpty() ? Optional.empty() : Optional.of(content);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * The state file {@code install.sh}/{@code install.ps1} write the pinned version into, sibling to
     * {@link #webInstallerVersionsRoot()} (mirrors {@code UpdateChecker}'s own cache-file placement).
     */
    public static Path pinnedVersionFile() {
        return pinnedVersionFile(webInstallerVersionsRoot());
    }

    private static Path pinnedVersionFile(Path webInstallerVersionsRoot) {
        return webInstallerVersionsRoot.getParent().resolve("pinned-version");
    }

    /**
     * Probes every known install location for the presence of a {@code camel} executable, regardless of which one the
     * current process happens to be running from. Best-effort: an install method whose root cannot be determined
     * deterministically (WinGet, whose folder name embeds a publisher hash) is not scanned here and is only ever
     * reported via {@link #resolveActiveOnPath()}.
     */
    public static List<InstallInfo> scanKnownLocations() {
        List<InstallInfo> found = new ArrayList<>();
        scanWebInstaller(found);
        if (IS_WINDOWS) {
            scanChocolatey(found);
            scanScoopWindows(found);
        } else {
            scanHomebrew(found);
            scanSdkman(found);
            scanJBang(found);
        }
        return found;
    }

    private static void scanWebInstaller(List<InstallInfo> found) {
        Path root = webInstallerVersionsRoot();
        listVersionDirs(root, dir -> Files.exists(dir.resolve("bin").resolve(IS_WINDOWS ? "camel.bat" : "camel.sh")))
                .forEach(dir -> found.add(new InstallInfo(dir, InstallMethod.WEB_INSTALLER)));
    }

    private static void scanHomebrew(List<InstallInfo> found) {
        // "apache-camel", not "camel-cli": the Homebrew formula is renamed as part of this same
        // branch of work (see the Homebrew Distribution tasks later in this plan) - the installed
        // Cellar keg directory is always named after the formula, never the JReleaser distribution id.
        for (String prefix : List.of("/opt/homebrew", "/usr/local", "/home/linuxbrew/.linuxbrew")) {
            Path cellar = Paths.get(prefix, "Cellar", "apache-camel");
            listVersionDirs(cellar, dir -> Files.exists(dir.resolve("libexec").resolve("bin").resolve("camel.sh")))
                    .forEach(dir -> found.add(new InstallInfo(dir.resolve("libexec"), InstallMethod.HOMEBREW)));
        }
    }

    private static void scanSdkman(List<InstallInfo> found) {
        Path candidates = Paths.get(System.getProperty("user.home"), ".sdkman", "candidates", "camel");
        listVersionDirs(candidates, dir -> Files.exists(dir.resolve("bin").resolve("camel.sh")))
                .forEach(dir -> found.add(new InstallInfo(dir, InstallMethod.SDKMAN)));
    }

    private static void scanJBang(List<InstallInfo> found) {
        Path shim = Paths.get(System.getProperty("user.home"), ".jbang", "bin", "camel");
        if (Files.exists(shim)) {
            found.add(new InstallInfo(shim, InstallMethod.JBANG));
        }
    }

    private static void scanChocolatey(List<InstallInfo> found) {
        Path tools = Paths.get("C:\\ProgramData\\chocolatey\\lib\\camel-cli\\tools");
        if (Files.exists(tools.resolve("camel.bat"))) {
            found.add(new InstallInfo(tools, InstallMethod.CHOCOLATEY));
        }
    }

    private static void scanScoopWindows(List<InstallInfo> found) {
        String scoopHome = System.getenv("SCOOP");
        Path apps = Paths.get(scoopHome != null && !scoopHome.isBlank()
                ? scoopHome
                : Paths.get(System.getProperty("user.home"), "scoop").toString(), "apps", "camel-cli");
        listVersionDirs(apps, dir -> Files.exists(dir.resolve("bin").resolve("camel.bat")))
                .forEach(dir -> found.add(new InstallInfo(dir, InstallMethod.SCOOP)));
    }

    private static List<Path> listVersionDirs(Path root, Predicate<Path> hasLauncher) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (var stream = Files.list(root)) {
            return stream.filter(Files::isDirectory).filter(hasLauncher).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Walks {@code PATH} the way a shell would and returns the first {@code camel} executable found, with its symbolic
     * links followed (the web installer puts a symlink to {@code versions/<version>/bin/camel.sh} on {@code PATH}).
     */
    public static Optional<Path> resolveActiveOnPath() {
        String path = System.getenv("PATH");
        return resolveActiveOnPath(path);
    }

    static Optional<Path> resolveActiveOnPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String exeName = IS_WINDOWS ? "camel.cmd" : "camel";
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Paths.get(dir, exeName);
            if (Files.exists(candidate)) {
                return Optional.of(followLinks(candidate));
            }
        }
        return Optional.empty();
    }

    // Follows the symbolic links of the file itself (a chain of them, at most 16), so the active install is the
    // version directory the link points into. The parent directories are not resolved (unlike toRealPath, which turns
    // macOS /var into /private/var), as the scanned install locations are not resolved either.
    static Path followLinks(Path path) {
        Path current = path;
        for (int i = 0; i < 16 && Files.isSymbolicLink(current); i++) {
            try {
                Path target = Files.readSymbolicLink(current);
                Path parent = current.toAbsolutePath().getParent();
                current = (parent != null ? parent.resolve(target) : target).normalize();
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                break;
            }
        }
        return current;
    }
}
