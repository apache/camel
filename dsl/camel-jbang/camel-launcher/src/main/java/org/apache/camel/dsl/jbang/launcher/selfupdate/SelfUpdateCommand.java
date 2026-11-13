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
package org.apache.camel.dsl.jbang.launcher.selfupdate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.dsl.jbang.run.common.VersionHelper;
import org.apache.camel.util.FileUtil;

@CommandDefinition(name = "self-update", description = "Checks for and installs updates to the Camel CLI launcher",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel self-update",
                           "  camel self-update --version 4.23.0",
                           "  camel self-update --version SNAPSHOT",
                           "  camel self-update --check",
                           "",
                           "An install pinned with the web installer's --version X.Y.Z never moves; one pinned with",
                           "--version X.Y or --version LTS moves to the newest release of that line, or the newest LTS.",
                           "",
                           "--version SNAPSHOT (main) or X.Y.Z-SNAPSHOT installs the newest nightly build from the Apache",
                           "snapshot repository, and pins it, so camel self-update follows the nightly builds. Nightly",
                           "builds are development builds for testing, not Apache releases. A nightly build of a",
                           "maintenance branch is updated by re-running the installer with --version X.Y.Z-SNAPSHOT.",
                           "",
                           "--version switches the pin to another selector: SNAPSHOT, X.Y.Z-SNAPSHOT, one nightly build",
                           "(which never moves), LTS or X.Y. --version X.Y.Z installs that release without a pin." })
public class SelfUpdateCommand extends CamelCommand {

    @Option(name = "version",
            description = "Install a specific version instead of the latest: X.Y.Z, the newest release of X.Y or"
                          + " LTS (pinned), the nightly builds of SNAPSHOT (main) or X.Y.Z-SNAPSHOT (pinned), or"
                          + " one nightly build X.Y.Z-YYYYMMDD.HHMMSS-N (pinned)")
    String version;

    @Option(name = "check", hasValue = false, description = "Only report whether a newer version is available")
    boolean checkOnly;

    private final ManifestFetcher fetcherOverride;
    private final InstallScriptFetcher scriptFetcherOverride;
    private final Map<String, String> installerEnvironmentOverride;

    public SelfUpdateCommand(CamelJBangMain main) {
        this(main, null, null, null);
    }

    // Visible for testing: SelfUpdateIntegrationTest (Task 8) points these at a local fixture instead of the real
    // network / real install.sh|install.ps1 default base URL, and pins the delegated script's own environment
    // (CAMEL_INSTALL_*, HOME/LOCALAPPDATA) to an isolated test HOME rather than the real machine's. Production
    // callers (SelfUpdatePlugin) only ever use the public one-arg constructor, which passes null for all three -
    // fromEnvironment() and the real process environment are used unmodified.
    public SelfUpdateCommand(CamelJBangMain main, ManifestFetcher fetcherOverride, InstallScriptFetcher scriptFetcherOverride,
                             Map<String, String> installerEnvironmentOverride) {
        super(main);
        this.fetcherOverride = fetcherOverride;
        this.scriptFetcherOverride = scriptFetcherOverride;
        this.installerEnvironmentOverride = installerEnvironmentOverride;
    }

    @Override
    public Integer doCall() throws Exception {
        InstallDetector.InstallInfo info = InstallDetector.locate();
        if (info.method() != InstallDetector.InstallMethod.WEB_INSTALLER) {
            printer().printErr(refusalMessage(info.method()));
            return 1;
        }

        // An exact X.Y.Z pin (or a pin file this version does not understand) never moves: only the installer writes
        // one. Every other pin is one camel self-update writes itself, so an explicit --version can switch it to
        // another selector, as the installer does.
        Optional<String> pinned = InstallDetector.pinnedVersion();
        Pin pin = pinned.map(Pin::parse).orElse(null);
        if (pin != null && (pin.kind() == Pin.Kind.EXACT || pin.kind() == Pin.Kind.INVALID)) {
            printer().printErr("this install is pinned to version " + pinned.get() + " - remove "
                               + InstallDetector.pinnedVersionFile() + " to resume tracking new releases");
            return 1;
        }
        if (pin != null && pin.kind() == Pin.Kind.NIGHTLY && version == null) {
            printer().printErr(nightlyPinRefusal(pin));
            return 1;
        }
        if (pin != null && pin.tracksAlias() && version != null && Pin.parse(version).kind() == Pin.Kind.EXACT) {
            // --version X.Y.Z writes no pin, and removing the selector pin would make the install track the latest
            // release, which is not what asking for one version means, so point at the installer, which replaces the
            // selector pin with an exact one.
            printer().printErr(selectorPinRefusal(pin, version));
            return 1;
        }
        // An explicit --version replaces the pin, once installed; until then the pin is left as it is.
        boolean replacesPin = pin != null && version != null;
        if (replacesPin) {
            pin = null;
        }

        ManifestFetcher fetcher = fetcherOverride != null ? fetcherOverride : ManifestFetcher.fromEnvironment();
        if (version != null && Nightly.isNightly(version)) {
            return updateToNightly(fetcher, info, Nightly.normalize(version), true);
        }
        if (pin != null && pin.followsNightlies()) {
            return updateToNightly(fetcher, info, pin.value(), false);
        }
        // --version LTS or X.Y installs the newest release of that selector and pins it, as the installer does.
        Pin selector = version != null ? Pin.parse(version) : null;
        if (selector != null && !selector.tracksAlias()) {
            selector = null;
        }
        ManifestFetcher.Manifest manifest;
        try {
            if (pin != null) {
                manifest = fetcher.fetchAlias(pin.aliasName());
            } else if (selector != null) {
                manifest = fetcher.fetchAlias(selector.aliasName());
            } else {
                manifest = version != null ? fetcher.fetch(version) : fetcher.fetchLatest();
            }
        } catch (SelfUpdateException e) {
            printer().printErr(e.getMessage());
            return 1;
        }

        if (selector != null && !selector.accepts(manifest.version())) {
            printer().printErr("manifest version (" + manifest.version() + ") does not match requested version ("
                               + selector.describe() + ")");
            return 1;
        }
        if (version != null && selector == null && !version.equals(manifest.version())) {
            printer().printErr("manifest version (" + manifest.version() + ") does not match requested version ("
                               + version + ")");
            return 1;
        }
        if (pin != null && !pin.accepts(manifest.version())) {
            printer().printErr("manifest version (" + manifest.version() + ") does not match this install, which is pinned"
                               + " to " + pin.describe());
            return 1;
        }

        CamelCatalog catalog = new DefaultCamelCatalog();
        String running = catalog.getCatalogVersion();
        boolean newer = VersionHelper.compare(manifest.version(), running) > 0;

        Pin follows = pin != null ? pin : selector;
        String upToDate = follows != null
                ? "Camel CLI is already on the latest version for " + follows.describe() + " (" + running + ")"
                : "Camel CLI is already on the latest version (" + running + ")";

        if (checkOnly) {
            printer().println(newer
                    ? "A new version is available (" + running + " -> " + manifest.version() + ")"
                    : upToDate);
            return 0;
        }

        if (!newer && (version == null || (selector != null && manifest.version().equals(running)))) {
            printer().println(upToDate);
            return selector != null ? writePin(selector.value()) : 0;
        }

        // Always pin the exact resolved version, even for a bare `camel self-update` with no --version: without
        // this, a new release could land in the window between the version compare above and the delegated
        // script's own manifest fetch below, and the script would silently install a version this command never
        // actually decided on. For an X.Y or LTS pin this is the version the alias resolved to; the delegated run
        // (CAMEL_INSTALL_SELF_UPDATE=true) leaves the selector in the pin file untouched.
        int exit = runInstaller(manifest.version());
        if (exit == 0 && selector != null) {
            return writePin(selector.value());
        }
        if (exit == 0 && replacesPin) {
            // --version X.Y.Z, which leaves the nightly builds: back to following the releases
            return writePin(null);
        }
        return exit;
    }

    // Installs the newest nightly build of the selector (SNAPSHOT or X.Y.Z-SNAPSHOT), or the nightly build it names.
    // An explicit --version pins the selector, so a later camel self-update follows the nightly builds (or, for one
    // nightly build, stays on it); following the pin (explicit=false) only installs a newer build.
    private Integer updateToNightly(
            ManifestFetcher fetcher, InstallDetector.InstallInfo info, String selector, boolean explicit) {
        String build;
        // printed after the outcome: X.Y.Z-SNAPSHOT was released, or is a maintenance branch
        String releasedNote = null;
        String maintenanceNote = null;
        try {
            if (Nightly.isBuild(selector)) {
                build = selector;
            } else {
                List<String> published = fetcher.fetchSnapshotVersions();
                String main = ManifestFetcher.latestSnapshotVersion(published);
                String snapshotVersion = Nightly.isLatest(selector) ? main : selector;
                if (!Nightly.isLatest(selector)) {
                    String release = Nightly.releaseOf(snapshotVersion);
                    boolean released = fetcher.isReleased(release);
                    if (!published.contains(snapshotVersion)) {
                        // the snapshot repository removes a snapshot version some time after its release
                        printer().printErr("there are no nightly builds of " + snapshotVersion + " any more"
                                           + (released ? " (" + release + " has been released)" : "") + " - "
                                           + waysOut(snapshotVersion, released, published, main));
                        return 1;
                    }
                    if (released) {
                        releasedNote = "Note: " + release + " has been released, so " + snapshotVersion
                                       + " gets no more nightly builds - "
                                       + waysOut(snapshotVersion, true, published, main);
                    } else if (!snapshotVersion.equals(main)) {
                        maintenanceNote = maintenanceNote(snapshotVersion);
                    }
                }
                build = fetcher.fetchNewestBuild(snapshotVersion);
            }
        } catch (SelfUpdateException e) {
            printer().printErr(e.getMessage());
            return 1;
        }

        Optional<String> running = Nightly.runningBuild(info.location(), InstallDetector.webInstallerVersionsRoot());
        boolean same = running.isPresent() && running.get().equals(build);
        boolean newer = running.isEmpty()
                || !Nightly.snapshotVersionOf(running.get()).equals(Nightly.snapshotVersionOf(build))
                || Nightly.compare(build, running.get()) > 0;
        String current = running.orElseGet(() -> new DefaultCamelCatalog().getCatalogVersion());

        if (checkOnly) {
            printer().println(same || !newer
                    ? "Camel CLI is already on the newest nightly build for " + Nightly.describe(selector) + " (" + current
                      + ")"
                    : "A new nightly build is available (" + current + " -> " + build + ")");
            printNote(releasedNote);
            return 0;
        }
        if (same || (!newer && !explicit)) {
            printer().println(
                    "Camel CLI is already on the newest nightly build for " + Nightly.describe(selector) + " (" + current
                              + ")");
            printNote(releasedNote);
            return explicit ? writePin(selector) : 0;
        }

        printer().println(Nightly.notice(build));
        int exit = runInstaller(build);
        if (exit != 0) {
            return exit;
        }
        printNote(releasedNote);
        printNote(maintenanceNote);
        return explicit ? writePin(selector) : 0;
    }

    private void printNote(String note) {
        if (note != null) {
            printer().println(note);
        }
    }

    // Package-visible for testing. How to move on from the nightly builds of a snapshot version that was released or
    // is no longer published: the release, the next published snapshot version, main, or the LTS releases.
    static String waysOut(String snapshotVersion, boolean released, List<String> published, String main) {
        StringBuilder sb = new StringBuilder("camel self-update");
        if (released) {
            sb.append(" --version ").append(Nightly.releaseOf(snapshotVersion)).append(" installs the release,");
        }
        String next = null;
        for (String candidate : published) {
            if (!candidate.equals(main)
                    && VersionHelper.compare(Nightly.releaseOf(candidate), Nightly.releaseOf(snapshotVersion)) > 0
                    && (next == null || VersionHelper.compare(Nightly.releaseOf(candidate), Nightly.releaseOf(next)) < 0)) {
                next = candidate;
            }
        }
        if (next != null) {
            sb.append(" --version ").append(next).append(" follows the nightly builds of ").append(next).append(',');
        }
        sb.append(" --version SNAPSHOT follows the nightly builds of main (").append(main)
                .append("), and --version LTS installs the newest LTS release");
        return sb.toString();
    }

    // Package-visible for testing. The Camel CLI of a maintenance branch cannot update its nightly builds itself (its
    // camel self-update has no nightly builds), so after installing one, point at the installer.
    static String maintenanceNote(String snapshotVersion) {
        return "Note: " + snapshotVersion + " is a maintenance branch, whose Camel CLI cannot update its nightly builds"
               + " with camel self-update - to update it, re-run the installer: " + installerCommand(snapshotVersion);
    }

    // Package-visible for testing. One nightly build never moves; name the ways to follow the nightly builds again,
    // or to go back to the releases.
    static String nightlyPinRefusal(Pin pin) {
        return "this install is pinned to nightly build " + pin.value() + " - run camel self-update --version "
               + Nightly.snapshotVersionOf(pin.value()) + " (or --version SNAPSHOT) to follow the nightly builds,"
               + " or --version LTS to go back to the releases";
    }

    // Writes (or, for null, removes) the pin of the web installer, as install.sh does for an explicit --version.
    private int writePin(String selector) {
        Path file = InstallDetector.pinnedVersionFile();
        try {
            if (selector == null) {
                Files.deleteIfExists(file);
            } else {
                Files.createDirectories(file.getParent());
                Files.writeString(file, selector + "\n", StandardCharsets.UTF_8);
            }
            return 0;
        } catch (IOException e) {
            printer().printErr("installed, but failed to update the pin in " + file, e);
            return 1;
        }
    }

    private Integer runInstaller(String targetVersion) {
        Path stagingDir;
        try {
            stagingDir = Files.createTempDirectory("camel-self-update-");
        } catch (Exception e) {
            printer().printErr("failed to create a staging directory", e);
            return 1;
        }
        try {
            InstallScriptFetcher scriptFetcher = scriptFetcherOverride != null
                    ? scriptFetcherOverride
                    : InstallScriptFetcher.fromEnvironment();
            Path script = scriptFetcher.fetch(stagingDir);

            List<String> command = FileUtil.isWindows()
                    ? List.of("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script.toString(),
                            "-Version", targetVersion)
                    : List.of("/bin/sh", script.toString(), "--version", targetVersion);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            if (installerEnvironmentOverride != null) {
                pb.environment().clear();
                pb.environment().put("PATH", System.getenv("PATH"));
                pb.environment().putAll(installerEnvironmentOverride);
            }
            // Tells install.sh/install.ps1 that the --version/-Version below is this command's own resolved
            // target (TOCTOU-safety), not a human pinning a version - the script must not write or clear its
            // pin-state file for this run. Set last so it can never be dropped by the override block above.
            pb.environment().put("CAMEL_INSTALL_SELF_UPDATE", "true");
            Process process = pb.start();
            // Drain stdout in a separate thread to prevent the subprocess from blocking on a full pipe buffer in
            // the gap before waitFor() returns. Then wait with a timeout to prevent an indefinitely stalled
            // installer from hanging the CLI.
            ExecutorService drainer = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "camel-self-update-drainer");
                t.setDaemon(true);
                return t;
            });
            Future<String> outputFuture = drainer
                    .submit(() -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            try {
                boolean finished = process.waitFor(5, TimeUnit.MINUTES);
                if (!finished) {
                    // Kill grandchildren first (curl/wget etc.) before the shell, so they release the pipe
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    printer().printErr("installer timed out after 5 minutes");
                    return CliExitCode.TIMEOUT;
                }
                String output = outputFuture.get(10, TimeUnit.SECONDS);
                int exit = process.exitValue();
                printer().print(output);
                return exit;
            } finally {
                drainer.shutdownNow();
            }
        } catch (SelfUpdateException e) {
            printer().printErr(e.getMessage());
            return 1;
        } catch (Exception e) {
            printer().printErr("failed to run the installer", e);
            return 1;
        } finally {
            deleteRecursively(stagingDir);
        }
    }

    // Package-visible for testing. Only refused for an exact X.Y.Z, which camel self-update installs without a pin.
    static String selectorPinRefusal(Pin pin, String requestedVersion) {
        return "this install is pinned to " + pin.describe() + " - to pin an exact version instead, re-run the"
               + " installer with that version: " + installerCommand(requestedVersion);
    }

    // The installer command form the install docs use for the running platform. install.ps1 has no nightly builds.
    private static String installerCommand(String version) {
        return FileUtil.isWindows() && !Nightly.isNightly(version)
                ? "irm https://camel.apache.org/install.ps1 -OutFile install.ps1; .\\install.ps1 -Version " + version
                : "curl -fsSL https://camel.apache.org/install.sh | sh -s -- --version " + version;
    }

    private static String refusalMessage(InstallDetector.InstallMethod method) {
        return switch (method) {
            case HOMEBREW -> "this install is managed by Homebrew - run 'brew upgrade apache-camel'";
            case CHOCOLATEY -> "this install is managed by Chocolatey - run 'choco upgrade camel-cli'";
            case WINGET -> "this install is managed by WinGet - run 'winget upgrade ApacheCamel.CLI'";
            case SCOOP -> "this install is managed by Scoop - run 'scoop update camel-cli'";
            case SDKMAN -> "this install is managed by SDKMAN - run 'sdk upgrade camel'";
            case JBANG -> "this install is managed by JBang - run 'jbang app install --force camel@apache/camel'";
            default -> "unable to determine how the Camel CLI was installed - see the install docs for how to upgrade";
        };
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    silentDelete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path d, java.io.IOException exc) {
                    silentDelete(d);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (java.io.IOException e) {
            // Best-effort staging cleanup; a leftover temp directory doesn't affect correctness of the next run.
        }
    }

    private static void silentDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (java.io.IOException e) {
            // Best-effort.
        }
    }
}
