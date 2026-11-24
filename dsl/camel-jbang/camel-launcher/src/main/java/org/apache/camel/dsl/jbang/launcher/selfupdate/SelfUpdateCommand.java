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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Option;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelCommand;
import org.apache.camel.dsl.jbang.core.commands.plugin.PluginUpdate;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.InstallDetector;
import org.apache.camel.dsl.jbang.core.common.PluginHelper;
import org.apache.camel.dsl.jbang.run.common.VersionHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonObject;

@CommandDefinition(name = "self-update", description = "Checks for and installs updates to the Camel CLI launcher",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel self-update",
                           "  camel self-update --version 4.23.0",
                           "  camel self-update --version SNAPSHOT",
                           "  camel self-update --check",
                           "",
                           "An install pinned with the web installer's --version X.Y.Z never moves; one",
                           "pinned with --version X.Y or --version LTS moves to the newest release of that",
                           "line, or the newest LTS.",
                           "",
                           "--version SNAPSHOT (main) or X.Y.Z-SNAPSHOT installs the newest nightly build",
                           "from the Apache snapshot repository, and pins it, so camel self-update follows",
                           "the nightly builds. Nightly builds are development builds for testing, not",
                           "Apache releases. A nightly build of a maintenance branch is updated by",
                           "re-running the installer with --version X.Y.Z-SNAPSHOT.",
                           "",
                           "--version switches the pin to another selector: SNAPSHOT, X.Y.Z-SNAPSHOT, one",
                           "nightly build (which never moves), LTS or X.Y. --version X.Y.Z installs that",
                           "release without a pin.",
                           "",
                           "Once installed, the new Camel CLI (4.24 or newer) runs camel plugin update,",
                           "which selects again the plugin releases that camel plugin add selected, for the",
                           "new version. When --check finds a newer version, it reports what that would",
                           "change. --skip-plugins leaves the plugins as they are." })
public class SelfUpdateCommand extends CamelCommand {

    // the first version of the Camel CLI with camel plugin update
    private static final String PLUGIN_UPDATE_SINCE = "4.24.0";

    @Option(name = "version",
            description = "Install a specific version instead of the latest: X.Y.Z, the newest release of X.Y or"
                          + " LTS (pinned), the nightly builds of SNAPSHOT (main) or X.Y.Z-SNAPSHOT (pinned), or"
                          + " one nightly build X.Y.Z-YYYYMMDD.HHMMSS-N (pinned)")
    String version;

    @Option(name = "check", hasValue = false,
            description = "Only report whether a newer version is available, and what camel plugin update would"
                          + " change with it")
    boolean checkOnly;

    @Option(name = "skip-plugins", hasValue = false,
            description = "Do not run camel plugin update with the new version once installed (or, with --check, do not"
                          + " report what it would change)")
    boolean skipPlugins;

    private final ManifestFetcher fetcherOverride;
    private final InstallScriptFetcher scriptFetcherOverride;
    private final Map<String, String> installerEnvironmentOverride;
    // Visible for testing: set to a short duration to avoid a 5-minute wait in tests
    public long processWaitTimeoutSeconds = 5 * 60;

    public SelfUpdateCommand() {
        this(null, null, null);
    }

    // Visible for testing: SelfUpdateIntegrationTest (Task 8) points these at a local fixture instead of the real
    // network / real install.sh|install.ps1 default base URL, and pins the environment of the delegated script and of
    // the installed CLI (CAMEL_INSTALL_*, HOME/LOCALAPPDATA) to an isolated test HOME rather than the real machine's.
    // Production callers (SelfUpdatePlugin) only ever use the public one-arg constructor, which passes null for all
    // three - fromEnvironment() and the real process environment are used unmodified.
    public SelfUpdateCommand(ManifestFetcher fetcherOverride, InstallScriptFetcher scriptFetcherOverride,
                             Map<String, String> installerEnvironmentOverride) {
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
            if (newer) {
                checkPlugins(manifest.version(), manifest.version());
            }
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
        if (exit != 0) {
            return exit;
        }
        if (selector != null) {
            exit = writePin(selector.value());
        } else if (replacesPin) {
            // --version X.Y.Z, which leaves the nightly builds: back to following the releases
            exit = writePin(null);
        }
        updatePlugins(manifest.version(), manifest.version());
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
            if (!same && newer) {
                String snapshotVersion = Nightly.snapshotVersionOf(build);
                checkPlugins(snapshotVersion, Nightly.releaseOf(snapshotVersion));
            }
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
        exit = explicit ? writePin(selector) : 0;
        updatePlugins(build, Nightly.releaseOf(Nightly.snapshotVersionOf(build)));
        printNote(releasedNote);
        printNote(maintenanceNote);
        return exit;
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

            // Tells install.sh/install.ps1 that the --version/-Version above is this command's own resolved
            // target (TOCTOU-safety), not a human pinning a version - the script must not write or clear its
            // pin-state file for this run.
            Integer exit = runProcess("installer", command, Map.of("CAMEL_INSTALL_SELF_UPDATE", "true"));
            if (exit == null) {
                printer().printErr("installer timed out after " + processWaitTimeoutSeconds + " seconds");
                return CliExitCode.TIMEOUT;
            }
            return exit;
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

    // Runs camel plugin update with the Camel CLI just installed (the directory of the version, and its release): it
    // selects the plugin releases built for its own version, which this CLI does not know before it is installed. Best
    // effort: the CLI is installed and stays so, and a failure only prints a hint.
    private void updatePlugins(String installed, String release) {
        if (skipPlugins || !hasPlugins(pluginConfig()) || !hasPluginUpdate(release)) {
            return;
        }
        String hint = "run 'camel plugin update' to update them";
        Path launcher = InstallDetector.webInstallerVersionsRoot().resolve(installed).resolve("bin")
                .resolve(FileUtil.isWindows() ? "camel.bat" : "camel.sh");
        if (!Files.isRegularFile(launcher)) {
            printer().printWarn("The plugins were not updated (" + launcher + " not found): " + hint);
            return;
        }
        List<String> command = FileUtil.isWindows()
                ? List.of("cmd.exe", "/c", launcher.toString(), "plugin", "update")
                : List.of(launcher.toString(), "plugin", "update");
        try {
            // no update notice from the new CLI, which was just installed
            Integer exit = runProcess("camel plugin update", command, Map.of("CAMEL_SELF_UPDATE_CHECK", "false"));
            if (exit == null) {
                printer().printWarn("The plugins were not updated (camel plugin update timed out after "
                                    + processWaitTimeoutSeconds + " seconds): " + hint);
            } else if (exit != 0) {
                printer().printWarn("The plugins were not updated (camel plugin update exited with " + exit + "): "
                                    + hint);
            }
        } catch (Exception e) {
            printer().printWarn("The plugins were not updated (" + e.getMessage() + "): " + hint);
        }
    }

    // For --check: what camel plugin update would change once the version (and its release) is installed, with the
    // releases selected by this CLI for that version, without downloading the plugins or changing anything.
    private void checkPlugins(String cliVersion, String release) {
        Path config = pluginConfig();
        if (skipPlugins || !hasPlugins(config) || !hasPluginUpdate(release)) {
            return;
        }
        try {
            PluginUpdate dryRun = getMain().command(new PluginUpdate());
            dryRun.dryRunFor(cliVersion, config);
            dryRun.doCall();
        } catch (Exception e) {
            printer().printWarn("Could not check the plugins: " + e.getMessage());
        }
    }

    // Whether the Camel CLI of this release has camel plugin update; when it has not, the plugins are kept as they are.
    private boolean hasPluginUpdate(String release) {
        if (VersionHelper.compare(release, PLUGIN_UPDATE_SINCE) >= 0) {
            return true;
        }
        printer().println("Note: Camel CLI " + release + " has no camel plugin update, so the plugins are kept as they"
                          + " are");
        return false;
    }

    // The plugin configuration of the new CLI, which runs with the environment of runProcess, or null when it cannot be
    // told.
    private Path pluginConfig() {
        String env = installerEnvironmentOverride != null
                ? installerEnvironmentOverride.get(PluginHelper.PLUGIN_CONFIG_ENV)
                : System.getenv(PluginHelper.PLUGIN_CONFIG_ENV);
        try {
            return env != null && !env.isBlank()
                    ? Path.of(env) : CommandLineHelper.getHomeDir().resolve(PluginHelper.PLUGIN_CONFIG);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    // Whether there are plugins to update. A configuration that cannot be read is left to camel plugin update to report.
    private static boolean hasPlugins(Path pluginConfig) {
        if (pluginConfig == null) {
            return true;
        }
        try {
            JsonObject config = PluginHelper.getPluginConfig(pluginConfig);
            JsonObject plugins = config != null ? config.getMap("plugins") : null;
            return plugins != null && !plugins.isEmpty();
        } catch (RuntimeException e) {
            return true;
        }
    }

    // Runs a process of the update, and prints its output once it ends. The environment is added to the one of this
    // process, or to the test override. Returns the exit code, or null when the process timed out (it is killed).
    private Integer runProcess(String what, List<String> command, Map<String, String> environment) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        if (installerEnvironmentOverride != null) {
            pb.environment().clear();
            pb.environment().put("PATH", System.getenv("PATH"));
            pb.environment().putAll(installerEnvironmentOverride);
        }
        // set last so it can never be dropped by the override block above
        pb.environment().putAll(environment);
        Process process = pb.start();
        // Drain stdout in a separate thread to prevent the subprocess from blocking on a full pipe buffer in the gap
        // before waitFor() returns. Then wait with a timeout to prevent an indefinitely stalled process from hanging
        // the CLI.
        ExecutorService drainer = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "camel-self-update-drainer");
            t.setDaemon(true);
            return t;
        });
        Future<String> outputFuture = drainer
                .submit(() -> new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        try {
            boolean finished = process.waitFor(processWaitTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                // Kill grandchildren first (curl/wget etc.) before the shell, so they release the pipe
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                return null;
            }
            int exit = process.exitValue();
            // outputFuture.get() can time out when a grandchild (curl/wget) keeps the pipe open after the shell
            // exits, or fail with an I/O error in the drainer thread. Either case is best-effort: the process already
            // finished (process.exitValue() was called above), so treat the drain failure as non-fatal and print what
            // we have.
            try {
                String output = outputFuture.get(10, TimeUnit.SECONDS);
                printer().print(output);
            } catch (TimeoutException | ExecutionException e) {
                // stdout drain timed out or failed — the process already returned exit, so continue
                printer().print(what + " finished, but its output could not be read: " + e.getMessage());
            }
            return exit;
        } finally {
            drainer.shutdownNow();
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
