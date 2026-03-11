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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.RunHelper;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

class DiagramPngExporter {

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
    private final boolean embeddedJolokia;
    private final int timeoutSeconds;
    private final CamelLaunch camelLaunch;
    private boolean jolokiaAttached;
    private long jolokiaPid;

    DiagramPngExporter(CamelJBangMain main, Printer printer, Path output, String browser,
                       String playwrightBrowserPath, String routeId, int jolokiaPort, int hawtioPort, boolean keepRunning,
                       boolean embeddedJolokia, int timeoutSeconds, CamelLaunch camelLaunch) {
        this.main = main;
        this.printer = printer;
        this.output = output;
        this.browser = browser;
        this.playwrightBrowserPath = playwrightBrowserPath;
        this.routeId = routeId;
        this.jolokiaPort = jolokiaPort;
        this.hawtioPort = hawtioPort;
        this.keepRunning = keepRunning;
        this.embeddedJolokia = embeddedJolokia;
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
        String jolokiaUrl = "http://127.0.0.1:" + jolokiaPort + "/jolokia";

        // Wait for Camel to be Running first (if we launched a new process).
        if (camelLaunch != null) {
            if (!waitForCamelRunning(camelLaunch)) {
                return 1;
            }
            target = Long.toString(camelLaunch.pid());
        }

        int attachCode = attachJolokia(target, jolokiaUrl);
        if (attachCode != 0) {
            return attachCode;
        }

        // Start Hawtio after Jolokia is attached. Starting it earlier (in parallel with the Camel
        // startup wait) caused resource contention between two concurrent JVM startups which slowed
        // both processes down and led to diagram-stability timeouts, increasing total time.
        HawtioProcess hawtioProcess = startHawtioProcess(hawtioPort);
        try {
            return exportDiagramPng(hawtioUrl, jolokiaUrl, outputPath, hawtioProcess);
        } finally {
            stopProcess(hawtioProcess.process);
            if (keepRunning && jolokiaAttached) {
                detachJolokia();
            }
        }
    }

    /**
     * Waits for the launched Camel integration to reach Running state (phase=5). Polls every 200ms (reduced from 500ms)
     * to minimise startup latency. Hawtio is already starting in parallel while this method runs.
     */
    private boolean waitForCamelRunning(CamelLaunch launch) throws InterruptedException {
        StopWatch watch = new StopWatch();
        int state = 0;
        while (launch.process().isAlive() && watch.taken() < (long) timeoutSeconds * 1000 && state < 5) {
            JsonObject root = loadCamelStatus(launch.pid());
            if (root != null) {
                JsonObject context = (JsonObject) root.get("context");
                if (context != null) {
                    Object phaseObj = context.get("phase");
                    if (phaseObj instanceof Number number) {
                        state = number.intValue();
                    }
                }
            }
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

    private int attachJolokia(String target, String jolokiaUrl) throws Exception {
        if (target == null || target.isBlank()) {
            printer.printErr("Name or PID required to attach Jolokia for PNG export");
            return 1;
        }
        jolokiaAttached = false;
        jolokiaPid = 0;
        if (isJolokiaAvailable(jolokiaUrl)) {
            return 0;
        }
        if (embeddedJolokia) {
            return waitForJolokia(jolokiaUrl);
        }
        JolokiaAttacher attacher = new JolokiaAttacher(printer);
        long pid = attacher.resolvePid(target);
        if (pid <= 0) {
            return 1;
        }
        int code = attacher.attach(pid, jolokiaPort);
        if (code == 0) {
            jolokiaAttached = true;
            jolokiaPid = pid;
            return 0;
        }
        if (isJolokiaAvailable(jolokiaUrl)) {
            return 0;
        }
        return code;
    }

    private int waitForJolokia(String jolokiaUrl) throws InterruptedException {
        String lastError = null;
        for (int i = 0; i < 30; i++) {
            String error = checkJolokia(jolokiaUrl);
            if (error == null) {
                return 0;
            }
            lastError = error;
            Thread.sleep(1000);
        }
        printer.printErr("Jolokia endpoint not available at " + jolokiaUrl
                         + (lastError != null ? " (" + lastError + ")" : ""));
        return 1;
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

    private String checkJolokia(String jolokiaUrl) {
        HttpURLConnection conn = null;
        try {
            String probeUrl = jolokiaUrl.endsWith("/") ? jolokiaUrl + "version" : jolokiaUrl + "/version";
            URL url = URI.create(probeUrl).toURL();
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
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

    private boolean isJolokiaAvailable(String jolokiaUrl) {
        return checkJolokia(jolokiaUrl) == null;
    }

    private HawtioProcess startHawtioProcess(int port) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("hawtio");
        args.add("--port=" + port);
        args.add("--openUrl=false");
        RunHelper.addCamelCLICommand(args);
        ProcessBuilder pb = new ProcessBuilder();
        pb.command(args);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        List<String> output = Collections.synchronizedList(new ArrayList<>());
        startOutputPump(process.getInputStream(), output);
        return new HawtioProcess(process, output);
    }

    private void stopProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            process.destroy();
            if (!process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private int exportDiagramPng(String hawtioUrl, String jolokiaUrl, Path outputPath, HawtioProcess hawtioProcess)
            throws Exception {
        String execPath = resolveBrowserPath();
        if (execPath == null) {
            printer.printErr("Playwright browser executable path not configured. "
                             + "Set --playwright-browser-path or PLAYWRIGHT_*_EXECUTABLE_PATH.");
            return 1;
        }
        DiagramScripts scripts = new DiagramScripts();
        Playwright.CreateOptions createOptions = new Playwright.CreateOptions();
        if (execPath != null && !execPath.isBlank()) {
            // Skip Playwright's own browser download when a custom binary path is provided
            createOptions.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        }
        try (Playwright playwright = Playwright.create(createOptions)) {
            BrowserType browserType = selectBrowser(playwright);
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setExecutablePath(Paths.get(execPath))
                    .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage"));
            try (Browser browserInstance = browserType.launch(launchOptions)) {
                var page = browserInstance.newPage();
                // Large viewport ensures React Flow renders all route nodes before screenshotting.
                // The default 1280x720 clips multi-route diagrams on the right side.
                page.setViewportSize(3840, 2160);
                DiagramPage diagramPage = new DiagramPage(page, scripts, printer, routeId, timeoutSeconds);
                diagramPage.connectToJolokia(hawtioUrl, jolokiaUrl);
                diagramPage.openRouteDiagram();
                diagramPage.captureDiagramScreenshot(outputPath);
            }
        } catch (Exception e) {
            if (hawtioProcess != null && !hawtioProcess.process.isAlive()) {
                throw new IllegalStateException(
                        "Hawtio terminated before startup." + formatHawtioOutput(hawtioProcess.output), e);
            }
            throw e;
        }
        return 0;
    }

    private BrowserType selectBrowser(Playwright playwright) {
        return playwright.chromium();
    }

    private String resolveBrowserPath() {
        if (playwrightBrowserPath != null && !playwrightBrowserPath.isBlank()) {
            return playwrightBrowserPath;
        }
        Map<String, String> env = System.getenv();
        return env.get("PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH");
    }

    private void startOutputPump(InputStream inputStream, List<String> output) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    synchronized (output) {
                        if (output.size() >= 200) {
                            output.remove(0);
                        }
                        output.add(line);
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }, "camel-diagram-hawtio-output");
        thread.setDaemon(true);
        thread.start();
    }

    private String formatHawtioOutput(List<String> output) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        synchronized (output) {
            if (output.isEmpty()) {
                return "";
            }
            return System.lineSeparator() + "Hawtio output:" + System.lineSeparator()
                   + String.join(System.lineSeparator(), output);
        }
    }

    private static final class HawtioProcess {
        private final Process process;
        private final List<String> output;

        private HawtioProcess(Process process, List<String> output) {
            this.process = process;
            this.output = output;
        }
    }
}
