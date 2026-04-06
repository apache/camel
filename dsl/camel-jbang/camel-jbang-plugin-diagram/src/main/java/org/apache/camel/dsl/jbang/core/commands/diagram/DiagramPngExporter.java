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
package org.apache.camel.dsl.jbang.core.commands.diagram;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.RunHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.main.download.DependencyDownloaderClassLoader;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

class DiagramPngExporter {

    /**
     * Playwright version to download on demand. Must match playwright-version property in the parent POM.
     */
    private static final String PLAYWRIGHT_VERSION = "1.58.0";
    private static final String PLAYWRIGHT_ARTIFACT_ID = "playwright";

    /**
     * Holds metadata for a Camel integration process that has been launched but may not yet have reached Running state.
     * Passed from {@link DiagramCommand} so that {@link DiagramPngExporter} can wait for it concurrently while Hawtio
     * starts up in parallel, reducing total startup time.
     */
    record CamelLaunch(Process process, long pid, Path logPath, String name) {
    }

    private final CamelJBangMain main;
    private final Printer printer;
    private final Path output;
    private final String browser;
    private final String playwrightBrowserPath;
    private final String routeId;
    private final int jolokiaPort;
    private final int hawtioPort;
    private final boolean keepRunning;
    private final int timeoutSeconds;
    private final CamelLaunch camelLaunch;
    private boolean jolokiaAttached;
    private long jolokiaPid;
    private String jolokiaUrl;

    DiagramPngExporter(CamelJBangMain main, Printer printer, Path output, String browser,
                       String playwrightBrowserPath, String routeId, int jolokiaPort, int hawtioPort, boolean keepRunning,
                       int timeoutSeconds, CamelLaunch camelLaunch) {
        this.main = main;
        this.printer = printer;
        this.output = output;
        this.browser = browser;
        this.playwrightBrowserPath = playwrightBrowserPath;
        this.routeId = routeId;
        this.jolokiaPort = jolokiaPort;
        this.hawtioPort = hawtioPort;
        this.keepRunning = keepRunning;
        this.timeoutSeconds = timeoutSeconds;
        this.camelLaunch = camelLaunch;
    }

    int export(String target) throws Exception {
        Path outputPath = output;
        if (outputPath == null) {
            printer.printErr("Output file is required for PNG export");
            return 1;
        }
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        if (browser != null && !"chromium".equalsIgnoreCase(browser)) {
            printer.printErr("Only chromium is supported for PNG export at the moment.");
            return 1;
        }

        String hawtioUrl = "http://localhost:" + hawtioPort + "/hawtio";
        // jolokiaUrl may be updated after attach if a different port was used
        jolokiaUrl = "http://127.0.0.1:" + jolokiaPort + "/jolokia";

        // Start Hawtio in parallel while we wait for Camel to reach Running state.
        Process hawtioProcess = startHawtioProcess(hawtioPort);
        try {
            // Wait for Camel to be Running (if we launched a new process).
            if (camelLaunch != null) {
                if (!waitForCamelRunning(camelLaunch)) {
                    return 1;
                }
                target = Long.toString(camelLaunch.pid());
            }

            int attachCode = attachJolokia(target);
            if (attachCode != 0) {
                return attachCode;
            }

            // Ensure Hawtio is ready before spawning the subprocess — the subprocess will
            // proceed immediately without needing to wait for the endpoint itself.
            waitForHawtio(hawtioUrl, hawtioProcess);

            return exportDiagramPng(hawtioUrl, jolokiaUrl, outputPath, hawtioProcess);
        } finally {
            stopProcess(hawtioProcess);
            if (keepRunning && jolokiaAttached) {
                detachJolokia();
            }
        }
    }

    /**
     * Extracts the Camel context phase from a status JSON, or 0 if unavailable.
     */
    private int loadCamelPhase(long pid) {
        JsonObject root = loadCamelStatus(pid);
        if (root == null) {
            return 0;
        }
        JsonObject context = (JsonObject) root.get("context");
        if (context == null) {
            return 0;
        }
        Object phaseObj = context.get("phase");
        if (phaseObj instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /**
     * Waits for the launched Camel integration to reach Running state (phase=5).
     */
    private boolean waitForCamelRunning(CamelLaunch launch) throws InterruptedException {
        StopWatch watch = new StopWatch();
        int state = 0;
        while (launch.process().isAlive() && watch.taken() < (long) timeoutSeconds * 1000 && state < 5) {
            state = loadCamelPhase(launch.pid());
            if (state < 5) {
                Thread.sleep(200);
            }
        }

        if (!launch.process().isAlive()) {
            printer.printErr("Camel Main: " + launch.name() + " startup failure");
            try {
                String text = Files.readString(launch.logPath());
                if (!text.isBlank()) {
                    printer.printErr(text);
                }
            } catch (IOException ignored) {
                // ignore
            }
            try {
                Files.deleteIfExists(launch.logPath());
            } catch (IOException ignored) {
                // ignore
            }
            return false;
        }

        String stateLabel = state >= 5 ? "Running" : "Starting";
        printer.println("Camel Main: " + launch.name() + " (state: " + stateLabel + ")");
        try {
            Files.deleteIfExists(launch.logPath());
        } catch (IOException ignored) {
            // ignore
        }
        return true;
    }

    private JsonObject loadCamelStatus(long pid) {
        try {
            Path file = CommandLineHelper.getCamelDir().resolve(pid + "-status.json");
            if (Files.exists(file)) {
                try (InputStream is = Files.newInputStream(file)) {
                    String text = IOHelper.loadText(is);
                    return (JsonObject) Jsoner.deserialize(text);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private int attachJolokia(String target) throws Exception {
        if (target == null || target.isBlank()) {
            printer.printErr("Name or PID required to attach Jolokia for PNG export");
            return 1;
        }
        jolokiaAttached = false;
        jolokiaPid = 0;
        // Always resolve the target PID and attach/verify Jolokia for that specific process.
        // Using isJolokiaAvailable() as a shortcut is unsafe — the endpoint may belong to a
        // different process (e.g. a leftover from a previous run or a different integration).
        JolokiaAttacher attacher = new JolokiaAttacher(printer);
        long pid = attacher.resolvePid(target);
        if (pid <= 0) {
            return 1;
        }
        int actualPort = attacher.attachGetPort(pid, jolokiaPort);
        if (actualPort < 0) {
            return 1;
        }
        // Update jolokiaUrl in case a different port was used (e.g. requested port was busy)
        jolokiaUrl = "http://127.0.0.1:" + actualPort + "/jolokia";
        jolokiaAttached = true;
        jolokiaPid = pid;
        return 0;
    }

    /**
     * Waits for the Hawtio HTTP server to be available. This is called in the parent process so that the Playwright
     * subprocess can connect immediately without waiting.
     */
    private void waitForHawtio(String hawtioUrl, Process hawtioProcess) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            if (!hawtioProcess.isAlive()) {
                return; // subprocess will detect Hawtio not running
            }
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) URI.create(hawtioUrl).toURL().openConnection();
                conn.setConnectTimeout(500);
                conn.setReadTimeout(500);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                // Accept 2xx/3xx/4xx (except 404) as "server is up": Hawtio may respond
                // with 401 (auth), 403 (CSRF), or 405 (method) before its routes are fully
                // initialised — all mean the HTTP listener is already accepting connections.
                // 404 is excluded because it means the endpoint itself is not yet available.
                if (code >= 200 && code < 500 && code != 404) {
                    return;
                }
            } catch (Exception ignored) {
                // connection not yet available — keep polling
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            Thread.sleep(500);
        }
    }

    private void detachJolokia() {
        try {
            if (jolokiaPid <= 0) {
                return;
            }
            JolokiaAttacher attacher = new JolokiaAttacher(printer);
            attacher.detach(jolokiaPid);
        } catch (Exception e) {
            printer.printErr("Failed to stop Jolokia: " + e.getMessage());
        }
    }

    String checkJolokia(String jolokiaUrl) {
        HttpURLConnection conn = null;
        try {
            String probeUrl = jolokiaUrl.endsWith("/") ? jolokiaUrl + "version" : jolokiaUrl + "/version";
            URL url = URI.create(probeUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            // Accept 2xx/3xx/4xx (except 404) as "server is up": Jolokia may respond
            // with 401 (auth), 403 (CSRF), or 405 (method) before fully initialised —
            // all mean the HTTP listener is already accepting connections.
            // 404 is excluded because it means the endpoint itself is not yet available.
            if (code >= 200 && code < 500 && code != 404) {
                return null;
            }
            return "HTTP " + code;
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private Process startHawtioProcess(int port) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("hawtio");
        args.add("--port=" + port);
        args.add("--openUrl=false");
        RunHelper.addCamelCLICommand(args);
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(args);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return pb.start();
    }

    void stopProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.destroy();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // ignore
        }
    }

    private int exportDiagramPng(String hawtioUrl, String jolokiaUrl, Path outputPath, Process hawtioProcess)
            throws Exception {
        String execPath = resolveBrowserPath();
        if (execPath == null) {
            printer.printErr("Playwright browser executable path not configured. "
                             + "Set --playwright-browser-path or PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH.");
            return 1;
        }

        if (!isPlaywrightCached()) {
            printer.println("Downloading Playwright (one-time setup, cached in ~/.m2)...");
        }
        List<Path> playwrightJars = downloadPlaywrightJars();
        Path pluginJar = resolvePluginJarPath();

        List<String> cmd = new ArrayList<>();
        cmd.add(getJavaExecutable());
        // Use only tier-1 JIT to reduce cold-start overhead for this short-lived subprocess.
        cmd.add("-XX:TieredStopAtLevel=1");
        cmd.add("-cp");
        cmd.add(buildClassPath(playwrightJars, pluginJar));
        cmd.add("org.apache.camel.dsl.jbang.core.commands.diagram.DiagramScreenshotter");
        cmd.add(hawtioUrl);
        cmd.add(jolokiaUrl);
        cmd.add(outputPath.toAbsolutePath().toString());
        cmd.add(routeId != null && !routeId.isBlank() ? routeId : "-");
        cmd.add(execPath);
        cmd.add(String.valueOf(timeoutSeconds));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();
        if (exitCode != 0 && hawtioProcess != null && !hawtioProcess.isAlive()) {
            throw new IllegalStateException("Hawtio terminated before startup.");
        }
        if (exitCode != 0) {
            throw new IllegalStateException("PNG export failed (exit code " + exitCode + ").");
        }
        return exitCode;
    }

    /**
     * Returns true if the Playwright JAR is already present in the local Maven repository, meaning no network download
     * is needed.
     */
    boolean isPlaywrightCached() {
        String home = System.getProperty("user.home", "");
        Path jar = Path.of(home, ".m2", "repository", "com", "microsoft", PLAYWRIGHT_ARTIFACT_ID,
                PLAYWRIGHT_ARTIFACT_ID, PLAYWRIGHT_VERSION, PLAYWRIGHT_ARTIFACT_ID + "-" + PLAYWRIGHT_VERSION + ".jar");
        return Files.exists(jar);
    }

    /**
     * Downloads Playwright and its transitive dependencies on demand via Maven, returning the local JAR paths.
     */
    private List<Path> downloadPlaywrightJars() throws Exception {
        DependencyDownloaderClassLoader cl = new DependencyDownloaderClassLoader(null);
        try (MavenDependencyDownloader downloader = new MavenDependencyDownloader()) {
            downloader.setClassLoader(cl);
            downloader.start();
            downloader.downloadDependency("com.microsoft.playwright", PLAYWRIGHT_ARTIFACT_ID, PLAYWRIGHT_VERSION);
        }
        return Arrays.stream(cl.getURLs())
                .map(url -> {
                    try {
                        return Path.of(url.toURI());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .filter(p -> p != null)
                .collect(Collectors.toList());
    }

    /**
     * Returns the Path to the plugin JAR so the subprocess classloader can load {@link DiagramScreenshotter}. When
     * running inside a Spring Boot fat JAR, the code source location is a nested {@code nested:} URL; in that case the
     * JAR bytes are extracted to a temp file.
     */
    private Path resolvePluginJarPath() throws Exception {
        URL location = DiagramPngExporter.class.getProtectionDomain().getCodeSource().getLocation();
        String loc = location.toString();
        if (loc.startsWith("nested:") || (loc.contains("!/") && !loc.startsWith("file:"))) {
            // Running inside Spring Boot fat JAR — extract the nested plugin JAR to a temp file
            Path tempJar = Files.createTempFile("camel-diagram-plugin", ".jar"); //NOSONAR java:S5443
            tempJar.toFile().deleteOnExit();
            try (InputStream is = location.openStream()) {
                Files.copy(is, tempJar, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempJar;
        }
        return Path.of(location.toURI());
    }

    String buildClassPath(List<Path> playwrightJars, Path pluginJar) {
        return playwrightJars.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator))
               + File.pathSeparator + pluginJar;
    }

    static String getJavaExecutable() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            String exe = javaHome + File.separator + "bin" + File.separator + "java";
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                exe += ".exe";
            }
            if (new File(exe).exists()) {
                return exe;
            }
        }
        return "java";
    }

    String resolveBrowserPath() {
        if (playwrightBrowserPath != null && !playwrightBrowserPath.isBlank()) {
            return playwrightBrowserPath;
        }
        Map<String, String> env = System.getenv();
        return env.get("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH");
    }
}
