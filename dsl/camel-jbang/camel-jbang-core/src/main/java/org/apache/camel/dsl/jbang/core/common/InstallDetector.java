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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.camel.util.FileUtil;

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

    private InstallDetector() {
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
        if (normalized.contains("/WinGet/Packages/")) {
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
        if (FileUtil.isWindows()) {
            return Paths.get(System.getenv("LOCALAPPDATA"), "Apache Camel", "cli", "versions");
        }
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        Path dataHome = xdgDataHome != null && !xdgDataHome.isBlank()
                ? Paths.get(xdgDataHome) : Paths.get(System.getProperty("user.home"), ".local", "share");
        return dataHome.resolve("camel-cli").resolve("versions");
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
        if (FileUtil.isWindows()) {
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
        listVersionDirs(root, dir -> Files.exists(dir.resolve("bin").resolve(FileUtil.isWindows() ? "camel.bat" : "camel.sh")))
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
     * Walks {@code PATH} the way a shell would and returns the first {@code camel} executable found.
     */
    public static Optional<Path> resolveActiveOnPath() {
        String path = System.getenv("PATH");
        return resolveActiveOnPath(path);
    }

    static Optional<Path> resolveActiveOnPath(String path) {
        if (path == null || path.isBlank()) {
            return Optional.empty();
        }
        String exeName = FileUtil.isWindows() ? "camel.cmd" : "camel";
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Paths.get(dir, exeName);
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
