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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.BufferedReader;
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
import java.util.Locale;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ThreadLocalRandom;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.Page.WaitForFunctionOptions;
import com.microsoft.playwright.Page.WaitForSelectorOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ViewportSize;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
import org.apache.camel.dsl.jbang.core.commands.process.Jolokia;
import org.apache.camel.dsl.jbang.core.commands.process.StopProcess;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StopWatch;
import picocli.CommandLine;

@CommandLine.Command(name = "diagram", description = "Visualize Camel routes using Hawtio", sortOptions = false,
                     showDefaultValues = true)
public class Diagram extends CamelCommand {

    @CommandLine.Parameters(description = "The Camel file(s) to run. If no files specified then use --name to attach to a running integration.",
                            arity = "0..9", paramLabel = "<files>", parameterConsumer = FilesConsumer.class)
    Path[] filePaths; // Defined only for file path completion; the field never used
    List<String> files = new ArrayList<>();

    @CommandLine.Option(names = { "--name" },
                        description = "Name or pid of running Camel integration")
    String name;

    @CommandLine.Option(names = { "--renderer" },
                        description = "Renderer to use (hawtio)",
                        defaultValue = "hawtio")
    String renderer = "hawtio";

    @CommandLine.Option(names = { "--port" },
                        description = "Port number to use for Hawtio web console (port 8888 by default)", defaultValue = "8888")
    int port = 8888;

    @CommandLine.Option(names = { "--openUrl" },
                        description = "To automatic open Hawtio web console in the web browser", defaultValue = "true")
    boolean openUrl = true;

    @CommandLine.Option(names = { "--background-wait" }, defaultValue = "true",
                        description = "To wait for run in background to startup successfully, before returning")
    boolean backgroundWait = true;

    @CommandLine.Option(names = { "--keep-running" }, defaultValue = "false",
                        description = "Keep the background Camel integration running after exiting Hawtio")
    boolean keepRunning;

    @CommandLine.Option(names = { "--output" },
                        description = "Write a PNG snapshot of the route diagram to the given file")
    Path output;

    @CommandLine.Option(names = { "--browser" },
                        description = "Playwright browser to use (chromium only)",
                        defaultValue = "chromium")
    String browser = "chromium";

    @CommandLine.Option(names = { "--playwright-browser-path" },
                        description = "Path to the Playwright browser executable")
    String playwrightBrowserPath;

    @CommandLine.Option(names = { "--playwright-timeout" }, defaultValue = "120000",
                        description = "Timeout in millis for Playwright navigation and rendering")
    long playwrightTimeout = 120000;

    @CommandLine.Option(names = { "--route-id" },
                        description = "Route id to render (defaults to the first route)")
    String routeId;

    @CommandLine.Option(names = { "--jolokia-port" }, defaultValue = "8778",
                        description = "Jolokia port to attach when exporting PNG")
    int jolokiaPort = 8778;

    private boolean jolokiaAttached;

    public Diagram(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        String selectedRenderer = renderer == null ? "hawtio" : renderer.toLowerCase(Locale.ROOT);
        if (!"hawtio".equals(selectedRenderer)) {
            printer().printErr("Unsupported renderer: " + renderer);
            return 1;
        }

        boolean hasFiles = files != null && !files.isEmpty();
        boolean exportPng = output != null;
        if (exportPng && openUrl) {
            openUrl = false;
        }

        String runName = name;
        if (hasFiles && (runName == null || runName.isBlank())) {
            runName = FileUtil.onlyName(FileUtil.stripPath(files.get(0)));
        }
        String target = runName;
        if (!hasFiles && (target == null || target.isBlank())) {
            new CommandLine(this).execute("--help");
            return 0;
        }

        long pid = 0;
        boolean started = false;
        int exit = 0;
        try {
            if (hasFiles) {
                Run run = new Run(getMain());
                run.backgroundWait = backgroundWait;
                if (runName != null && !runName.isBlank()) {
                    run.name = runName;
                }
                List<String> args = new ArrayList<>();
                args.add("run");
                if (runName != null && !runName.isBlank()) {
                    args.add("--name=" + runName);
                }
                args.addAll(files);
                RunHelper.addCamelJBangCommand(args);
                ProcessBuilder pb = new ProcessBuilder();
                pb.command(args);
                int rc = run.runBackgroundProcess(pb, "Camel Main");
                if (rc != 0) {
                    return rc;
                }
                pid = run.spawnPid;
                if (pid <= 0) {
                    printer().printErr("Unable to determine the running Camel PID");
                    return 1;
                }
                target = Long.toString(pid);
                started = true;
            }

            if (exportPng) {
                String hawtioUrl = "http://localhost:" + port + "/hawtio";
                String jolokiaUrl = "http://127.0.0.1:" + jolokiaPort + "/jolokia";
                int attachCode = attachJolokia(target, jolokiaUrl);
                if (attachCode != 0) {
                    return attachCode;
                }
                HawtioProcess hawtioProcess = startHawtioProcess(port);
                try {
                    waitForHawtio(hawtioUrl, hawtioProcess);
                    exit = exportDiagramPng(hawtioUrl, jolokiaUrl);
                } finally {
                    stopProcess(hawtioProcess.process);
                    if (keepRunning && jolokiaAttached) {
                        detachJolokia(target);
                    }
                }
                return exit;
            } else {
                Hawtio hawtio = new Hawtio(getMain());
                List<String> hawtioArgs = new ArrayList<>();
                if (target != null && !target.isBlank()) {
                    hawtioArgs.add(target);
                }
                hawtioArgs.add("--port=" + port);
                hawtioArgs.add("--openUrl=" + openUrl);
                CommandLine.populateCommand(hawtio, hawtioArgs.toArray(new String[0]));
                exit = hawtio.doCall();
                return exit;
            }
        } finally {
            if (started && !keepRunning) {
                StopProcess stop = new StopProcess(getMain());
                if (target != null && !target.isBlank()) {
                    CommandLine.populateCommand(stop, target);
                }
                stop.doCall();
            }
        }
    }

    private int attachJolokia(String target, String jolokiaUrl) throws Exception {
        if (target == null || target.isBlank()) {
            printer().printErr("Name or PID required to attach Jolokia for PNG export");
            return 1;
        }
        jolokiaAttached = false;
        if (isJolokiaAvailable(jolokiaUrl)) {
            return 0;
        }
        Jolokia jolokia = new Jolokia(getMain());
        List<String> args = new ArrayList<>();
        args.add(target);
        args.add("--port=" + jolokiaPort);
        CommandLine.populateCommand(jolokia, args.toArray(new String[0]));
        int code = jolokia.doCall();
        if (code != 0) {
            return waitForJolokia(jolokiaUrl, 5000) ? 0 : code;
        }
        if (waitForJolokia(jolokiaUrl, 5000)) {
            jolokiaAttached = true;
            return 0;
        }
        printer().printErr("Unable to attach Jolokia at " + jolokiaUrl);
        return 1;
    }

    private void detachJolokia(String target) {
        try {
            if (target == null || target.isBlank()) {
                return;
            }
            Jolokia jolokia = new Jolokia(getMain());
            CommandLine.populateCommand(jolokia, "--stop", target);
            jolokia.doCall();
        } catch (Exception e) {
            printer().printErr("Failed to stop Jolokia: " + e.getMessage());
        }
    }

    private HawtioProcess startHawtioProcess(int port) throws Exception {
        List<String> args = new ArrayList<>();
        args.add("hawtio");
        args.add("--port=" + port);
        args.add("--openUrl=false");
        RunHelper.addCamelJBangCommand(args);
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
            process.waitFor();
        } catch (Exception e) {
            // ignore
        }
    }

    private void waitForHawtio(String hawtioUrl, HawtioProcess hawtioProcess) throws Exception {
        StopWatch watch = new StopWatch();
        while (watch.taken() < playwrightTimeout) {
            try {
                URL url = URI.create(hawtioUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                if (code >= 200 && code < 500) {
                    return;
                }
            } catch (Exception e) {
                // ignore until timeout
            }
            if (hawtioProcess != null && !hawtioProcess.process.isAlive()) {
                throw new IllegalStateException("Hawtio terminated before startup." + formatHawtioOutput(hawtioProcess.output));
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(
                "Hawtio did not start within " + playwrightTimeout + " ms." + formatHawtioOutput(hawtioProcess.output));
    }

    private boolean isJolokiaAvailable(String jolokiaUrl) {
        try {
            String probeUrl = jolokiaUrl.endsWith("/") ? jolokiaUrl + "version" : jolokiaUrl + "/version";
            URL url = URI.create(probeUrl).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(1000);
            conn.setReadTimeout(1000);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code >= 200 && code < 500 && code != 404;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForJolokia(String jolokiaUrl, long timeoutMs) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < timeoutMs) {
            if (isJolokiaAvailable(jolokiaUrl)) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private int exportDiagramPng(String hawtioUrl, String jolokiaUrl) throws Exception {
        Path outputPath = output;
        if (outputPath == null) {
            printer().printErr("Output file is required for PNG export");
            return 1;
        }
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        if (browser != null && !"chromium".equalsIgnoreCase(browser)) {
            printer().printErr("Only chromium is supported for PNG export at the moment.");
            return 1;
        }
        try (Playwright playwright = Playwright.create()) {
            BrowserType browserType = selectBrowser(playwright);
            String execPath = resolveBrowserPath();
            if (execPath == null) {
                printer().printErr("Playwright browser executable path not configured. "
                                   + "Set --playwright-browser-path or PLAYWRIGHT_*_EXECUTABLE_PATH.");
                return 1;
            }
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setExecutablePath(Paths.get(execPath));
            try (Browser browserInstance = browserType.launch(launchOptions)) {
                Page page = browserInstance.newPage();
                page.setDefaultTimeout(playwrightTimeout);
                page.setDefaultNavigationTimeout(playwrightTimeout);
                connectToJolokia(page, hawtioUrl, jolokiaUrl);
                openRouteDiagram(page);
                captureDiagramScreenshot(page, outputPath);
            }
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

    private void connectToJolokia(Page page, String hawtioUrl, String jolokiaUrl) {
        String connectionId = generateConnectionId();
        String connectionsJson = buildConnectionJson(connectionId, jolokiaUrl);
        String escapedConnections = escapeJavaScript(connectionsJson);
        page.addInitScript(
                "(() => { const connections = JSON.parse('" + escapedConnections + "'); "
                           + "localStorage.setItem('connect.connections', JSON.stringify(connections)); "
                           + "const id = Object.keys(connections)[0]; "
                           + "sessionStorage.setItem('connect.currentConnection', JSON.stringify(id)); })();");
        page.navigate(hawtioUrl + "?con=" + connectionId + "#/camel/routes",
                new NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        waitForFunction(page, "sessionStorage.getItem('connect.currentConnection') !== null",
                playwrightTimeout / 3);
    }

    private void openRouteDiagram(Page page) {
        navigateToHash(page, "#/camel");
        if (routeId != null && !routeId.isBlank()) {
            if (!selectRouteInTree(page, routeId)) {
                throw new IllegalStateException("Route id not found in Hawtio tree: " + routeId);
            }
        } else {
            selectRoutesFolder(page);
        }
        navigateToHash(page, "#/camel/routeDiagram");
        if (!waitForSelector(page, ".react-flow__node, .react-flow__nodes, svg", playwrightTimeout / 2)) {
            throw new IllegalStateException("Route diagram not available in Hawtio. Ensure Jolokia connection succeeded.");
        }
        waitForDiagramAssets(page);
        waitForDiagramStable(page);
        prepareDiagramForScreenshot(page);
    }

    private boolean selectRouteInTree(Page page, String routeId) {
        try {
            waitForSelector(page, "#camel-tree-view .pf-v5-c-tree-view__node", playwrightTimeout / 6);
            Locator expandAll = page.locator("#camel-tree-view button:has-text(\"Expand all\")");
            if (expandAll.count() > 0) {
                expandAll.first().click();
                page.waitForTimeout(300);
            }
            Locator search = page.locator("#input-search");
            if (search.count() > 0) {
                search.fill(routeId);
                page.waitForTimeout(300);
            }
            Locator routeNode = null;
            Locator routeNodes = page.locator("#camel-tree-view button.pf-v5-c-tree-view__node-text");
            List<String> labels = routeNodes.allTextContents();
            for (int i = 0; i < labels.size(); i++) {
                if (routeId.equals(labels.get(i).trim())) {
                    routeNode = routeNodes.nth(i);
                    break;
                }
            }
            if (routeNode == null) {
                routeNode = page.locator("#camel-tree-view button.pf-v5-c-tree-view__node-text:has-text(\"" + routeId + "\")");
            }
            if (routeNode.count() == 0) {
                return false;
            }
            routeNode.first().scrollIntoViewIfNeeded();
            routeNode.first().click();
            waitForFunction(
                    page,
                    "routeId => Array.from(document.querySelectorAll('#camel-tree-view [aria-selected=\"true\"]'"
                          + ")).some(el => Array.from(el.querySelectorAll('button.pf-v5-c-tree-view__node-text'))"
                          + ".some(btn => btn.textContent && btn.textContent.trim() === routeId))",
                    routeId,
                    playwrightTimeout / 6);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void selectRoutesFolder(Page page) {
        try {
            waitForSelector(page, "#camel-tree-view .pf-v5-c-tree-view__node", playwrightTimeout / 6);
            Locator expandAll = page.locator("#camel-tree-view button:has-text(\"Expand all\")");
            if (expandAll.count() > 0) {
                expandAll.first().click();
                page.waitForTimeout(300);
            }
            Locator routeNodes = page.locator("#camel-tree-view button.pf-v5-c-tree-view__node-text");
            List<String> labels = routeNodes.allTextContents();
            for (int i = 0; i < labels.size(); i++) {
                if ("routes".equalsIgnoreCase(labels.get(i).trim())) {
                    Locator routesNode = routeNodes.nth(i);
                    routesNode.scrollIntoViewIfNeeded();
                    routesNode.click();
                    waitForFunction(
                            page,
                            "(() => Array.from(document.querySelectorAll('#camel-tree-view [aria-selected=\"true\"]'))"
                                  + ".some(el => Array.from(el.querySelectorAll('button.pf-v5-c-tree-view__node-text'))"
                                  + ".some(btn => btn.textContent && btn.textContent.trim().toLowerCase() === 'routes'))"
                                  + ")()",
                            playwrightTimeout / 6);
                    break;
                }
            }
        } catch (Exception e) {
            printer().printErr("Failed to select routes folder: " + e.getMessage());
        }
    }

    private void captureDiagramScreenshot(Page page, Path outputPath) {
        prepareDiagramForScreenshot(page);
        waitForDiagramStable(page);
        if (routeId == null || routeId.isBlank()) {
            normalizeDiagramLayout(page);
            waitForDiagramStable(page);
        }
        if (captureDiagramClip(page, outputPath)) {
            return;
        }
        Locator container = page.locator("#camel-route-diagram-outer-div");
        if (container.count() == 0) {
            container = page.locator(".react-flow");
        }
        if (container.count() > 0) {
            try {
                container.first().screenshot(new Locator.ScreenshotOptions().setPath(outputPath));
                return;
            } catch (com.microsoft.playwright.PlaywrightException e) {
                printer().printErr("Diagram container changed while rendering, capturing full page instead: " + e.getMessage());
            }
        }
        Locator diagram = page.locator("svg").first();
        if (diagram.count() > 0) {
            try {
                diagram.screenshot(new Locator.ScreenshotOptions().setPath(outputPath));
                return;
            } catch (com.microsoft.playwright.PlaywrightException e) {
                printer().printErr("Diagram element changed while rendering, capturing full page instead: " + e.getMessage());
            }
        }
        page.screenshot(new ScreenshotOptions().setPath(outputPath).setFullPage(true));
    }

    private void normalizeDiagramLayout(Page page) {
        try {
            page.evaluate("(() => {"
                          + "const outer = document.querySelector('#camel-route-diagram-outer-div');"
                          + "const viewport = outer ? outer.querySelector('.react-flow__viewport') : null;"
                          + "const nodes = outer ? Array.from(outer.querySelectorAll('.react-flow__node')) : [];"
                          + "const edges = outer ? Array.from(outer.querySelectorAll('.react-flow__edge-path')) : [];"
                          + "const labels = outer ? Array.from(outer.querySelectorAll('.react-flow__node *, .react-flow__edge-text, .react-flow__edge-textwrapper')) : [];"
                          + "const renderer = outer ? outer.querySelector('.react-flow__renderer') : null;"
                          + "if (!outer || !viewport || (nodes.length === 0 && edges.length === 0)) { return; }"
                          + "const transform = viewport.style.transform || '';"
                          + "let scale = 1;"
                          + "let tx = 0;"
                          + "let ty = 0;"
                          + "let match = transform.match(/translate\\(([-0-9.]+)px,\\s*([-0-9.]+)px\\)/);"
                          + "if (!match) { match = transform.match(/translate3d\\(([-0-9.]+)px,\\s*([-0-9.]+)px/); }"
                          + "if (match) { tx = parseFloat(match[1]); ty = parseFloat(match[2]); }"
                          + "const scaleMatch = transform.match(/scale\\(([-0-9.]+)\\)/);"
                          + "if (scaleMatch) { scale = parseFloat(scaleMatch[1]); }"
                          + "if (!isFinite(scale) || scale <= 0) { scale = 1; }"
                          + "if (!isFinite(tx)) { tx = 0; }"
                          + "if (!isFinite(ty)) { ty = 0; }"
                          + "const base = renderer || outer;"
                          + "const baseRect = base.getBoundingClientRect();"
                          + "let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;"
                          + "const updateBounds = (rect) => {"
                          + "  const x1 = (rect.left - baseRect.left - tx) / scale;"
                          + "  const y1 = (rect.top - baseRect.top - ty) / scale;"
                          + "  const x2 = (rect.right - baseRect.left - tx) / scale;"
                          + "  const y2 = (rect.bottom - baseRect.top - ty) / scale;"
                          + "  minX = Math.min(minX, x1);"
                          + "  minY = Math.min(minY, y1);"
                          + "  maxX = Math.max(maxX, x2);"
                          + "  maxY = Math.max(maxY, y2);"
                          + "};"
                          + "nodes.forEach(node => updateBounds(node.getBoundingClientRect()));"
                          + "edges.forEach(edge => updateBounds(edge.getBoundingClientRect()));"
                          + "labels.forEach(label => updateBounds(label.getBoundingClientRect()));"
                          + "if (!isFinite(minX) || !isFinite(minY) || !isFinite(maxX) || !isFinite(maxY)) { return; }"
                          + "const padding = 24;"
                          + "const extraLeft = 48;"
                          + "const extraRight = 96;"
                          + "const extraTop = 0;"
                          + "const extraBottom = 24;"
                          + "const leftPadding = padding + extraLeft;"
                          + "const rightPadding = padding + extraRight;"
                          + "const topPadding = padding + extraTop;"
                          + "const bottomPadding = padding + extraBottom;"
                          + "const width = Math.ceil((maxX - minX) * scale + leftPadding + rightPadding);"
                          + "const height = Math.ceil((maxY - minY) * scale + topPadding + bottomPadding);"
                          + "outer.style.width = `${width}px`;"
                          + "outer.style.height = `${height}px`;"
                          + "outer.style.minWidth = outer.style.width;"
                          + "outer.style.minHeight = outer.style.height;"
                          + "const container = outer.querySelector('.react-flow');"
                          + "if (container) {"
                          + "  container.style.width = outer.style.width;"
                          + "  container.style.height = outer.style.height;"
                          + "}"
                          + "viewport.style.transformOrigin = '0 0';"
                          + "viewport.style.transform = `translate(${leftPadding - minX * scale}px, ${topPadding - minY * scale}px)`"
                          + " + ` scale(${scale})`;"
                          + "})();");
        } catch (Exception e) {
            // ignore layout failures
        }
    }

    private void waitForDiagramStable(Page page) {
        waitForFunction(
                page,
                "(() => {"
                      + "const nodes = Array.from(document.querySelectorAll('.react-flow__node'));"
                      + "if (!nodes.length) { return false; }"
                      + "let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;"
                      + "for (const node of nodes) {"
                      + "  const rect = node.getBoundingClientRect();"
                      + "  minX = Math.min(minX, rect.left);"
                      + "  minY = Math.min(minY, rect.top);"
                      + "  maxX = Math.max(maxX, rect.right);"
                      + "  maxY = Math.max(maxY, rect.bottom);"
                      + "}"
                      + "const bounds = {"
                      + "minX: Math.round(minX), minY: Math.round(minY),"
                      + "maxX: Math.round(maxX), maxY: Math.round(maxY) };"
                      + "const viewport = document.querySelector('.react-flow__viewport');"
                      + "const transform = viewport ? viewport.style.transform : '';"
                      + "const now = Date.now();"
                      + "const state = window.__camelDiagramState || (window.__camelDiagramState = {"
                      + "count: nodes.length, transform, bounds, at: now });"
                      + "const changed = state.count !== nodes.length || state.transform !== transform"
                      + "|| !state.bounds || state.bounds.minX !== bounds.minX || state.bounds.minY !== bounds.minY"
                      + "|| state.bounds.maxX !== bounds.maxX || state.bounds.maxY !== bounds.maxY;"
                      + "if (changed) {"
                      + "state.count = nodes.length; state.transform = transform; state.bounds = bounds; state.at = now;"
                      + "return false; }"
                      + "return (now - state.at) > 500;"
                      + "})()",
                playwrightTimeout / 2);
    }

    private void prepareDiagramForScreenshot(Page page) {
        try {
            // Hide Hawtio chrome and allow overflow so the diagram renders unclipped.
            page.evaluate("(() => {"
                          + "const sidebar = document.querySelector('.pf-v5-c-page__sidebar');"
                          + "if (sidebar) { sidebar.style.display = 'none'; }"
                          + "const header = document.querySelector('.pf-v5-c-page__header');"
                          + "if (header) { header.style.display = 'none'; }"
                          + "const split = document.querySelector('.camel-split');"
                          + "if (split && split.children && split.children.length > 1) {"
                          + "  split.children[0].style.display = 'none';"
                          + "  split.children[1].style.width = '100%';"
                          + "}"
                          + "const outer = document.querySelector('#camel-route-diagram-outer-div');"
                          + "const container = outer ? outer.querySelector('.react-flow') : null;"
                          + "const layers = ["
                          + "  '#camel-route-diagram-outer-div .react-flow__renderer',"
                          + "  '#camel-route-diagram-outer-div .react-flow__pane',"
                          + "  '#camel-route-diagram-outer-div .react-flow__viewport',"
                          + "  '#camel-route-diagram-outer-div .react-flow__container',"
                          + "  '#camel-route-diagram-outer-div .camel-route-diagram'"
                          + "];"
                          + "if (outer) { outer.style.overflow = 'visible'; }"
                          + "if (container) { container.style.overflow = 'visible'; }"
                          + "layers.forEach(sel => document.querySelectorAll(sel)"
                          + "  .forEach(el => { el.style.overflow = 'visible'; }));"
                          + "document.querySelectorAll('.pf-v5-c-scroll-outer-wrapper, .pf-v5-c-scroll-inner-wrapper')"
                          + "  .forEach(el => {"
                          + "    el.style.overflow = 'visible';"
                          + "    el.style.maxWidth = 'none';"
                          + "    el.style.maxHeight = 'none';"
                          + "  });"
                          + "const unclip = (el) => {"
                          + "  let node = el;"
                          + "  while (node && node !== document.body) {"
                          + "    node.style.overflow = 'visible';"
                          + "    node.style.maxWidth = 'none';"
                          + "    node.style.maxHeight = 'none';"
                          + "    node = node.parentElement;"
                          + "  }"
                          + "};"
                          + "if (outer) { unclip(outer); }"
                          + "const main = document.querySelector('#camel-content-main');"
                          + "if (main) { main.style.overflow = 'visible'; }"
                          + "document.documentElement.style.overflow = 'visible';"
                          + "document.body.style.overflow = 'visible';"
                          + "})();");
        } catch (Exception e) {
            // ignore prepare failures
        }
    }

    private boolean captureDiagramClip(Page page, Path outputPath) {
        try {
            // Compute a tight clip around nodes/edges so we avoid full-page screenshots.
            Object clip = page.evaluate("(() => {"
                                        + "const outer = document.querySelector('#camel-route-diagram-outer-div');"
                                        + "const viewport = document.querySelector('#camel-route-diagram-outer-div .react-flow__viewport');"
                                        + "const nodes = Array.from(document.querySelectorAll('#camel-route-diagram-outer-div .react-flow__node'));"
                                        + "const edges = Array.from(document.querySelectorAll('#camel-route-diagram-outer-div .react-flow__edge-path'));"
                                        + "const labels = Array.from(document.querySelectorAll('#camel-route-diagram-outer-div .react-flow__node *,'"
                                        + " + ' #camel-route-diagram-outer-div .react-flow__edge-text,'"
                                        + " + ' #camel-route-diagram-outer-div .react-flow__edge-textwrapper'));"
                                        + "if (nodes.length === 0 && edges.length === 0) { return null; }"
                                        + "const parseTransform = () => {"
                                        + "  const transform = viewport ? viewport.style.transform || '' : '';"
                                        + "  let tx = 0, ty = 0, scale = 1;"
                                        + "  let match = transform.match(/translate\\(([-0-9.]+)px,\\s*([-0-9.]+)px\\)/);"
                                        + "  if (!match) { match = transform.match(/translate3d\\(([-0-9.]+)px,\\s*([-0-9.]+)px/); }"
                                        + "  if (match) { tx = parseFloat(match[1]); ty = parseFloat(match[2]); }"
                                        + "  const scaleMatch = transform.match(/scale\\(([-0-9.]+)\\)/);"
                                        + "  if (scaleMatch) { scale = parseFloat(scaleMatch[1]); }"
                                        + "  if (!isFinite(scale) || scale <= 0) { scale = 1; }"
                                        + "  return { tx, ty, scale };"
                                        + "};"
                                        + "const computeBounds = () => {"
                                        + "  let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;"
                                        + "  const updateBounds = (rect) => {"
                                        + "    minX = Math.min(minX, rect.left);"
                                        + "    minY = Math.min(minY, rect.top);"
                                        + "    maxX = Math.max(maxX, rect.right);"
                                        + "    maxY = Math.max(maxY, rect.bottom);"
                                        + "  };"
                                        + "  const applyBounds = (list) => {"
                                        + "    for (const el of list) { updateBounds(el.getBoundingClientRect()); }"
                                        + "  };"
                                        + "  if (nodes.length > 0) { applyBounds(nodes); }"
                                        + "  if (labels.length > 0) { applyBounds(labels); }"
                                        + "  if (edges.length > 0) {"
                                        + "    let eMinX = Infinity, eMinY = Infinity, eMaxX = -Infinity, eMaxY = -Infinity;"
                                        + "    for (const el of edges) {"
                                        + "      const rect = el.getBoundingClientRect();"
                                        + "      eMinX = Math.min(eMinX, rect.left);"
                                        + "      eMinY = Math.min(eMinY, rect.top);"
                                        + "      eMaxX = Math.max(eMaxX, rect.right);"
                                        + "      eMaxY = Math.max(eMaxY, rect.bottom);"
                                        + "    }"
                                        + "    if (nodes.length == 0) {"
                                        + "      minX = eMinX; minY = eMinY; maxX = eMaxX; maxY = eMaxY;"
                                        + "    } else {"
                                        + "      const edgeSlack = 24;"
                                        + "      if (eMinX < minX - edgeSlack) { minX = eMinX; }"
                                        + "      if (eMinY < minY - edgeSlack) { minY = eMinY; }"
                                        + "      if (eMaxX > maxX + edgeSlack) { maxX = eMaxX; }"
                                        + "      if (eMaxY > maxY + edgeSlack) { maxY = eMaxY; }"
                                        + "    }"
                                        + "  }"
                                        + "  if (!isFinite(minX) || !isFinite(minY) || !isFinite(maxX) || !isFinite(maxY)) { return null; }"
                                        + "  return { minX, minY, maxX, maxY };"
                                        + "};"
                                        + "const padding = 24;"
                                        + "const extraLeft = 48;"
                                        + "const extraRight = 96;"
                                        + "const extraTop = 0;"
                                        + "const extraBottom = 24;"
                                        + "const leftPadding = padding + extraLeft;"
                                        + "const rightPadding = padding + extraRight;"
                                        + "const topPadding = padding + extraTop;"
                                        + "const bottomPadding = padding + extraBottom;"
                                        + "let bounds = computeBounds();"
                                        + "if (!bounds) { return null; }"
                                        + "if (viewport) {"
                                        + "  const rect = outer ? outer.getBoundingClientRect() : { left: 0, top: 0 };"
                                        + "  const desiredLeft = rect.left + leftPadding;"
                                        + "  const desiredTop = rect.top + topPadding;"
                                        + "  const deltaX = bounds.minX < desiredLeft ? (desiredLeft - bounds.minX) : 0;"
                                        + "  const deltaY = bounds.minY < desiredTop ? (desiredTop - bounds.minY) : 0;"
                                        + "  if (Math.abs(deltaX) > 1 || Math.abs(deltaY) > 1) {"
                                        + "    const transform = parseTransform();"
                                        + "    const tx = transform.tx + deltaX;"
                                        + "    const ty = transform.ty + deltaY;"
                                        + "    viewport.style.transformOrigin = '0 0';"
                                        + "    viewport.style.transform = `translate(${tx}px, ${ty}px) scale(${transform.scale})`;"
                                        + "    bounds = computeBounds();"
                                        + "    if (!bounds) { return null; }"
                                        + "  }"
                                        + "}"
                                        + "const scrollX = window.scrollX || window.pageXOffset || 0;"
                                        + "const scrollY = window.scrollY || window.pageYOffset || 0;"
                                        + "const x1 = bounds.minX + scrollX - leftPadding;"
                                        + "const y1 = bounds.minY + scrollY - topPadding;"
                                        + "const x2 = bounds.maxX + scrollX + rightPadding;"
                                        + "const y2 = bounds.maxY + scrollY + bottomPadding;"
                                        + "const x = Math.max(0, x1);"
                                        + "const y = Math.max(0, y1);"
                                        + "const width = Math.max(1, x2 - x1);"
                                        + "const height = Math.max(1, y2 - y1);"
                                        + "return {x, y, width, height};"
                                        + "})()");
            if (!(clip instanceof Map<?, ?> clipMap)) {
                return false;
            }
            Double x = toDouble(clipMap.get("x"));
            Double y = toDouble(clipMap.get("y"));
            Double width = toDouble(clipMap.get("width"));
            Double height = toDouble(clipMap.get("height"));
            if (x == null || y == null || width == null || height == null || width <= 0 || height <= 0) {
                return false;
            }
            ensureViewportForClip(page, x + width, y + height);
            page.screenshot(new ScreenshotOptions().setPath(outputPath).setClip(x, y, width, height));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureViewportForClip(Page page, double requiredWidth, double requiredHeight) {
        if (requiredWidth <= 0 || requiredHeight <= 0) {
            return;
        }
        ViewportSize current = page.viewportSize();
        int currentWidth = current != null ? current.width : 0;
        int currentHeight = current != null ? current.height : 0;
        int targetWidth = (int) Math.ceil(requiredWidth);
        int targetHeight = (int) Math.ceil(requiredHeight);
        if (targetWidth > currentWidth || targetHeight > currentHeight) {
            page.setViewportSize(Math.max(targetWidth, currentWidth), Math.max(targetHeight, currentHeight));
        }
    }

    private void waitForDiagramAssets(Page page) {
        try {
            page.evaluate("async () => {"
                          + "if (document.fonts && document.fonts.ready) { await document.fonts.ready; }"
                          + "const imgs = Array.from(document.querySelectorAll('#camel-route-diagram-outer-div img'));"
                          + "await Promise.all(imgs.map(img => img.decode().catch(() => {})));"
                          + "}");
        } catch (Exception e) {
            // ignore asset wait failures
        }
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private String generateConnectionId() {
        int random = ThreadLocalRandom.current().nextInt(1_000_000);
        return "c" + String.format("%06d", random) + "-" + System.currentTimeMillis();
    }

    private String buildConnectionJson(String connectionId, String jolokiaUrl) {
        URI uri = URI.create(jolokiaUrl);
        String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
        String host = uri.getHost() != null ? uri.getHost() : "127.0.0.1";
        int port = uri.getPort();
        if (port <= 0) {
            port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
        }
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            path = "/jolokia";
        }
        return "{\"" + escapeJson(connectionId) + "\":{\"id\":\"" + escapeJson(connectionId) + "\","
               + "\"name\":\"local\"," + "\"scheme\":\"" + escapeJson(scheme) + "\","
               + "\"host\":\"" + escapeJson(host) + "\"," + "\"port\":" + port + ","
               + "\"path\":\"" + escapeJson(path) + "\"}}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeJavaScript(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void navigateToHash(Page page, String hash) {
        try {
            page.evaluate("hash => { window.location.hash = hash; }", hash);
            page.waitForTimeout(500);
        } catch (Exception e) {
            // ignore navigation issues
        }
    }

    private boolean waitForSelector(Page page, String selector, long timeout) {
        try {
            page.waitForSelector(selector, new WaitForSelectorOptions().setTimeout(timeout));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForFunction(Page page, String expression, long timeout) {
        try {
            page.waitForFunction(expression, new WaitForFunctionOptions().setTimeout(timeout));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean waitForFunction(Page page, String expression, Object arg, long timeout) {
        try {
            page.waitForFunction(expression, arg, new WaitForFunctionOptions().setTimeout(timeout));
            return true;
        } catch (Exception e) {
            return false;
        }
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

    static class FilesConsumer extends ParameterConsumer<Diagram> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Diagram cmd) {
            String arg = args.pop();
            cmd.files.add(arg);
        }
    }
}
