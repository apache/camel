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

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ViewportSize;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Standalone entry point for PNG screenshot capture. Spawned as a child JVM process by {@link DiagramPngExporter} with
 * Playwright JARs on the classpath — so Playwright is never loaded in the parent (camel-launcher fat JAR) process.
 * <p>
 * Arguments: hawtioUrl jolokiaUrl outputPath routeId|- execPath timeoutSeconds
 */
final class DiagramScreenshotter {

    private static final long DIAGRAM_STABILITY_TIMEOUT_MS = 3_000L;

    public static void main(String[] args) {
        if (args.length < 6) {
            System.err.println(
                    "Usage: DiagramScreenshotter <hawtioUrl> <jolokiaUrl> <outputPath> <routeId|-> <execPath> <timeoutSeconds>");
            System.exit(1);
        }
        String hawtioUrl = args[0];
        String jolokiaUrl = args[1];
        Path outputPath = Path.of(args[2]);
        String routeId = "-".equals(args[3]) ? null : args[3];
        String execPath = args[4];
        int timeoutSeconds = Integer.parseInt(args[5]);

        try {
            new DiagramScreenshotter(hawtioUrl, jolokiaUrl, outputPath, routeId, execPath, timeoutSeconds).run();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("Screenshot failed: " + e.getMessage());
            System.exit(1);
        }
    }

    private final String hawtioUrl;
    private final String jolokiaUrl;
    private final Path outputPath;
    private final String routeId;
    private final String execPath;
    private final long timeoutMs;

    DiagramScreenshotter(String hawtioUrl, String jolokiaUrl, Path outputPath,
                         String routeId, String execPath, int timeoutSeconds) {
        this.hawtioUrl = hawtioUrl;
        this.jolokiaUrl = jolokiaUrl;
        this.outputPath = outputPath;
        this.routeId = routeId;
        this.execPath = execPath;
        this.timeoutMs = (long) timeoutSeconds * 1000;
    }

    private void run() {
        DiagramScripts scripts = new DiagramScripts();
        Playwright.CreateOptions createOptions = new Playwright.CreateOptions();
        createOptions.setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1"));
        try (Playwright playwright = Playwright.create(createOptions)) {
            BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setExecutablePath(Paths.get(execPath))
                    .setArgs(List.of(
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-extensions",
                            "--disable-background-networking",
                            "--disable-translate",
                            "--no-first-run",
                            "--disable-default-apps"));
            try (Browser browser = playwright.chromium().launch(launchOptions)) {
                Page page = browser.newPage();
                page.setViewportSize(3840, 2160);
                page.addInitScript("(() => { const s = document.createElement('style');"
                                   + " s.textContent = '* { transition: none !important;"
                                   + " animation-duration: 0.001s !important;"
                                   + " animation-delay: 0s !important; }';"
                                   + " (document.head || document.documentElement).appendChild(s); })();");
                connectToJolokia(page, scripts);
                openRouteDiagram(page);
                captureDiagramScreenshot(page);
            }
        }
    }

    private void connectToJolokia(Page page, DiagramScripts scripts) {
        String connectionId = "c" + UUID.randomUUID();
        String connectionsJson = buildConnectionJson(connectionId, jolokiaUrl);
        String connectScript = scripts.load("diagram-connect.js")
                .replace("__CONNECTIONS__", escapeJavaScript(connectionsJson));
        String jolokiaProbe = jolokiaUrl.endsWith("/") ? jolokiaUrl + "version" : jolokiaUrl + "/version";
        waitForEndpoint(hawtioUrl);
        waitForEndpoint(jolokiaProbe);
        page.addInitScript(scripts.load("diagram-scripts.js"));
        page.addInitScript(connectScript);
        page.navigate(hawtioUrl + "?con=" + connectionId + "#/camel/routes",
                new NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    private void openRouteDiagram(Page page) {
        // Navigate to the route diagram view - renders all routes as react-flow nodes.
        navigateToHash(page, "#/camel/routeDiagram");
        try {
            page.locator(".react-flow__node, .react-flow__nodes, svg").first().waitFor(
                    new Locator.WaitForOptions().setTimeout(20000));
        } catch (PlaywrightException e) {
            throw new IllegalStateException(
                    "Route diagram not available in Hawtio. Ensure Jolokia connection succeeded.", e);
        }
        waitForDiagramStable(page);
    }

    private void captureDiagramScreenshot(Page page) {
        // Normalize the diagram layout to fit all route nodes before capturing.
        // The stability wait already happened in openRouteDiagram.
        normalizeDiagramLayout(page);
        if (captureDiagramClip(page)) {
            return;
        }
        Locator container = page.locator("#camel-route-diagram-outer-div");
        if (container.count() == 0) {
            container = page.locator(".react-flow");
        }
        if (container.count() > 0) {
            // Only use the container screenshot if it has a meaningful size.
            // A tiny element (e.g. 24x24) means the diagram hasn't rendered yet
            // and a full-page screenshot is more useful as a fallback.
            var bounds = container.first().boundingBox();
            if (bounds != null && bounds.width >= 200 && bounds.height >= 100) {
                try {
                    container.first().screenshot(new Locator.ScreenshotOptions().setPath(outputPath));
                    return;
                } catch (PlaywrightException e) {
                    System.err.println("Diagram container changed while rendering, capturing full page instead: "
                                       + e.getMessage());
                }
            }
        }
        // Full-page fallback: captures whatever Hawtio is showing, which is useful for debugging.
        // Avoid the svg.first() shortcut — the first SVG on the page is often a tiny icon.
        page.screenshot(new ScreenshotOptions().setPath(outputPath).setFullPage(true));
    }

    private void navigateToHash(Page page, String hash) {
        String currentUrl = page.url();
        String baseUrl = currentUrl.contains("#") ? currentUrl.substring(0, currentUrl.indexOf('#')) : currentUrl;
        page.navigate(baseUrl + hash, new NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForURL(url -> url.contains(hash));
    }

    private void waitForDiagramStable(Page page) {
        try {
            page.locator("html[data-camel-stable='true']").waitFor(
                    new Locator.WaitForOptions().setTimeout(DIAGRAM_STABILITY_TIMEOUT_MS));
        } catch (PlaywrightException e) {
            // data-camel-stable did not fire within timeout (nodes may still be loading).
            // Wait for node presence, then give the diagram extra time to render content,
            // then try one more time for stability before proceeding.
            try {
                page.locator(".react-flow__node").first()
                        .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED)
                                .setTimeout(2000));
            } catch (PlaywrightException ignored) {
                // proceed anyway and attempt screenshot
            }
            page.waitForTimeout(500);
            // One more attempt — content may have loaded during the extended wait
            try {
                page.locator("html[data-camel-stable='true']").waitFor(
                        new Locator.WaitForOptions().setTimeout(DIAGRAM_STABILITY_TIMEOUT_MS));
            } catch (PlaywrightException ignored) {
                // proceed with whatever is on the page
            }
        }
    }

    private void normalizeDiagramLayout(Page page) {
        try {
            page.evaluate("() => { if (window.camelDiagram) window.camelDiagram.normalize(); }");
        } catch (Exception ignored) {
            // ignore — large viewport fallback applies
        }
    }

    private boolean captureDiagramClip(Page page) {
        try {
            // For a specific route, compute the clip covering only that route's nodes.
            // For all routes, compute the clip covering the full diagram.
            String evalScript = (routeId != null && !routeId.isBlank())
                    ? "() => window.camelDiagram.computeClipForRoute(" + escapeJs(routeId) + ")"
                    : "() => window.camelDiagram.computeClip()";
            Object clip = page.evaluate(evalScript);
            if (clip == null && routeId != null && !routeId.isBlank()) {
                throw new IllegalStateException("Route id not found in diagram: " + routeId);
            }
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
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            return false;
        }
    }

    private static String escapeJs(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
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

    private void waitForEndpoint(String url) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) URI.create(url).toURL().openConnection(); //NOSONAR java:S5332
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                // Accept any non-404 response including 4xx: Hawtio's CORS/auth responses indicate
                // the server is up even if the specific endpoint requires authentication.
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
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for endpoint: " + url);
            }
        }
        throw new IllegalStateException("Endpoint not available after " + (timeoutMs / 1000) + "s: " + url);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
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
        // Build the Jolokia connection JSON manually to avoid a camel-util-json dependency
        // in this standalone subprocess entry point.
        String conn = "{\"id\":\"" + connectionId + "\",\"name\":\"local\","
                      + "\"scheme\":\"" + scheme + "\",\"host\":\"" + host + "\","
                      + "\"port\":" + port + ",\"path\":\"" + path + "\"}";
        return "{\"" + connectionId + "\":" + conn + "}";
    }

    private String escapeJavaScript(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
}
