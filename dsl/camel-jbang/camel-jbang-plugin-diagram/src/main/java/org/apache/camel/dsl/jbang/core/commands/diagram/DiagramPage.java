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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.NavigateOptions;
import com.microsoft.playwright.Page.ScreenshotOptions;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.ViewportSize;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import org.apache.camel.dsl.jbang.core.common.Printer;
import org.apache.camel.util.json.JsonObject;

class DiagramPage {

    private final Page page;
    private final DiagramScripts scripts;
    private final Printer printer;
    private final String routeId;
    private final long timeoutMs;
    private static final long DIAGRAM_STABILITY_TIMEOUT_MS = 3_000L;

    DiagramPage(Page page, DiagramScripts scripts, Printer printer, String routeId, int timeoutSeconds) {
        this.page = page;
        this.scripts = scripts;
        this.printer = printer;
        this.routeId = routeId;
        this.timeoutMs = (long) timeoutSeconds * 1000;
    }

    void connectToJolokia(String hawtioUrl, String jolokiaUrl) {
        String connectionId = generateConnectionId();
        String connectionsJson = buildConnectionJson(connectionId, jolokiaUrl);
        String connectScript = scripts.load("diagram-connect.js")
                .replace("__CONNECTIONS__", escapeJavaScript(connectionsJson));
        String jolokiaProbe = jolokiaUrl.endsWith("/") ? jolokiaUrl + "version" : jolokiaUrl + "/version";
        waitForEndpoint(hawtioUrl);
        waitForEndpoint(jolokiaProbe);
        page.addInitScript(scripts.load("diagram-scripts.js"));
        page.addInitScript(connectScript);
        // Navigate to Hawtio with the pre-configured connection. The init script sets up
        // localStorage/sessionStorage before page scripts run, so Hawtio auto-connects.
        // Connection success is verified implicitly when openRouteDiagram() waits for route nodes.
        page.navigate(hawtioUrl + "?con=" + connectionId + "#/camel/routes",
                new NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    void openRouteDiagram() {
        if (routeId != null && !routeId.isBlank()) {
            // Select a specific route in the tree before going to the diagram view
            navigateToHash("#/camel");
            if (!selectRouteInTree(routeId)) {
                throw new IllegalStateException("Route id not found in Hawtio tree: " + routeId);
            }
        }
        navigateToHash("#/camel/routeDiagram");
        try {
            page.locator(".react-flow__node, .react-flow__nodes, svg").first().waitFor();
        } catch (PlaywrightException e) {
            throw new IllegalStateException("Route diagram not available in Hawtio. Ensure Jolokia connection succeeded.", e);
        }
        waitForDiagramStable();
        prepareDiagramForScreenshot();
    }

    void captureDiagramScreenshot(Path outputPath) {
        prepareDiagramForScreenshot();
        // openRouteDiagram() already waited for stability; no need to wait again here.
        if (routeId == null || routeId.isBlank()) {
            normalizeDiagramLayout();
            waitForDiagramStable();
        }
        if (captureDiagramClip(outputPath)) {
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
            } catch (PlaywrightException e) {
                printer.printErr("Diagram container changed while rendering, capturing full page instead: " + e.getMessage());
            }
        }
        Locator diagram = page.locator("svg").first();
        if (diagram.count() > 0) {
            try {
                diagram.screenshot(new Locator.ScreenshotOptions().setPath(outputPath));
                return;
            } catch (PlaywrightException e) {
                printer.printErr("Diagram element changed while rendering, capturing full page instead: " + e.getMessage());
            }
        }
        page.screenshot(new ScreenshotOptions().setPath(outputPath).setFullPage(true));
    }

    private void navigateToHash(String hash) {
        // Use page.navigate() for SPA hash changes instead of page.evaluate() which is CSP-blocked.
        String currentUrl = page.url();
        String baseUrl = currentUrl.contains("#") ? currentUrl.substring(0, currentUrl.indexOf('#')) : currentUrl;
        page.navigate(baseUrl + hash, new NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        page.waitForURL(url -> url.contains(hash));
    }

    private boolean selectRouteInTree(String targetRouteId) {
        try {
            Locator routeNodes = waitAndExpandTree();
            // Poll for the target route to appear in allTextContents().
            // "Expand all" is async (React re-render); wait up to 30s for route nodes to populate.
            // allTextContents() reads DOM textContent regardless of CSS visibility, so it works
            // even on hidden/collapsed nodes with display:none.
            List<String> labels = List.of();
            for (int attempt = 0; attempt < 60; attempt++) {
                labels = routeNodes.allTextContents();
                if (labels.stream().anyMatch(l -> targetRouteId.equals(l.trim()))) {
                    break;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            Locator routeNode = null;
            for (int i = 0; i < labels.size(); i++) {
                if (targetRouteId.equals(labels.get(i).trim())) {
                    routeNode = routeNodes.nth(i);
                    break;
                }
            }
            if (routeNode == null) {
                // Use filter() to avoid CSS selector injection with special characters in routeId
                routeNode = routeNodes.filter(new Locator.FilterOptions().setHasText(targetRouteId));
            }
            if (routeNode.count() == 0) {
                return false;
            }
            // dispatchEvent("click") fires a synthetic MouseEvent on the DOM element via CDP.
            // Unlike locator.click(), it does NOT require the element to be visible or have a
            // layout, so it works even when PatternFly tree node buttons have display:none.
            // Hawtio's React event handler still processes the event to select the route.
            routeNode.first().dispatchEvent("click");
            // Verify selection via aria-selected (ATTACHED - element may be visually hidden).
            try {
                page.locator("#camel-tree-view [aria-selected='true'] button.pf-v5-c-tree-view__node-text")
                        .filter(new Locator.FilterOptions().setHasText(targetRouteId))
                        .waitFor(new Locator.WaitForOptions()
                                .setState(WaitForSelectorState.ATTACHED).setTimeout(10000));
            } catch (PlaywrightException ignored) {
                // Selection attribute may not be set if node is collapsed; proceed
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void selectRoutesFolder() {
        try {
            Locator routeNodes = waitAndExpandTree();
            // Poll for "routes" folder to appear — "Expand all" triggers an async React re-render.
            List<String> labels = List.of();
            for (int attempt = 0; attempt < 20; attempt++) {
                labels = routeNodes.allTextContents();
                if (labels.stream().anyMatch(l -> "routes".equalsIgnoreCase(l.trim()))) {
                    break;
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            for (int i = 0; i < labels.size(); i++) {
                if ("routes".equalsIgnoreCase(labels.get(i).trim())) {
                    Locator routesNode = routeNodes.nth(i);
                    // dispatchEvent("click") works on hidden DOM nodes; same approach as selectRouteInTree.
                    routesNode.dispatchEvent("click");
                    // Wait for routes folder to become selected, using DOM attribute set by the
                    // state monitor in diagram-scripts.js (avoids CSP-blocked waitForFunction).
                    page.locator("html[data-camel-routes-folder-selected='true']").waitFor(
                            new Locator.WaitForOptions().setTimeout(10000));
                    break;
                }
            }
        } catch (Exception e) {
            printer.printErr("Failed to select routes folder: " + e.getMessage());
        }
    }

    private Locator waitAndExpandTree() {
        page.locator("#camel-tree-view .pf-v5-c-tree-view__node").first()
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        // "Expand all" is in a CSS :hover-gated toolbar — it has display:none until hover.
        // dispatchEvent("click") fires the event on the hidden DOM node directly via CDP,
        // bypassing visibility checks. The React handler processes it and expands the tree.
        Locator expandAll = page.locator("#camel-tree-view button:has-text(\"Expand all\")");
        if (expandAll.count() > 0) {
            try {
                expandAll.first().dispatchEvent("click");
            } catch (PlaywrightException ignored) {
                // ignore if button is absent
            }
        }
        Locator routeNodes = page.locator("#camel-tree-view button.pf-v5-c-tree-view__node-text");
        // Wait for node buttons to be present in the DOM (ATTACHED, not VISIBLE).
        // PatternFly v5 tree node-text buttons are CSS-hidden but always queryable via
        // allTextContents(). We use dispatchEvent("click") in selectRouteInTree to click them.
        routeNodes.first().waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED));
        return routeNodes;
    }

    private void waitForDiagramAssets() {
        // document.fonts.ready and image decode are handled via addInitScript in diagram-scripts.js
        // page.evaluate() is CSP-blocked when using Chrome, so we use a short locator-based wait
        try {
            page.locator("html[data-camel-stable='true']").waitFor(
                    new Locator.WaitForOptions().setTimeout(DIAGRAM_STABILITY_TIMEOUT_MS));
        } catch (Exception e) {
            // ignore - assets may still be loading
        }
    }

    private void waitForDiagramStable() {
        try {
            page.locator("html[data-camel-stable='true']").waitFor(
                    new Locator.WaitForOptions().setTimeout(DIAGRAM_STABILITY_TIMEOUT_MS));
        } catch (PlaywrightException e) {
            // State monitor didn't fire; fall back to react-flow node presence check.
            try {
                page.locator(".react-flow__node").first()
                        .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED)
                                .setTimeout(2000));
            } catch (PlaywrightException ignored) {
                // proceed anyway and attempt screenshot
            }
            page.waitForTimeout(300);
        }
    }

    private void prepareDiagramForScreenshot() {
        // prepare() is called automatically by the state monitor in diagram-scripts.js.
    }

    private void normalizeDiagramLayout() {
        // Resize the diagram container to exactly fit all route nodes (no clipping, no whitespace).
        // page.evaluate() uses CDP and is not blocked by Hawtio's CSP in most configurations.
        // If it does fail, the 3840px viewport ensures all routes are rendered as a fallback.
        try {
            page.evaluate("() => { if (window.camelDiagram) window.camelDiagram.normalize(); }");
        } catch (Exception ignored) {
            // ignore — large viewport fallback applies
        }
    }

    private boolean captureDiagramClip(Path outputPath) {
        try {
            Object clip = page.evaluate("() => window.camelDiagram.computeClip()");
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
            ensureViewportForClip(x + width, y + height);
            page.screenshot(new ScreenshotOptions().setPath(outputPath).setClip(x, y, width, height));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureViewportForClip(double requiredWidth, double requiredHeight) {
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
                conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                if (code >= 200 && code < 500 && code != 404) {
                    return;
                }
            } catch (Exception ignored) {
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

    private String generateConnectionId() {
        return "c" + UUID.randomUUID();
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
        JsonObject conn = new JsonObject();
        conn.put("id", connectionId);
        conn.put("name", "local");
        conn.put("scheme", scheme);
        conn.put("host", host);
        conn.put("port", port);
        conn.put("path", path);
        JsonObject root = new JsonObject();
        root.put(connectionId, conn);
        return root.toJson();
    }

    private String escapeJavaScript(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\u2028", "\\u2028").replace("\u2029", "\\u2029");
    }
}
