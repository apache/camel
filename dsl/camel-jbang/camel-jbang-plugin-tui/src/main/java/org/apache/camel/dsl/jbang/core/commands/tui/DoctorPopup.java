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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.io.File;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.tooling.maven.MavenDownloaderImpl;
import org.apache.camel.tooling.maven.MavenResolutionException;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class DoctorPopup {

    private boolean visible;
    private List<Line> lines;
    private boolean mcpEnabled;
    private int mcpPort;
    private Supplier<String> mcpConnectedClient;

    boolean isVisible() {
        return visible;
    }

    void setMcpState(boolean enabled, int port, Supplier<String> connectedClient) {
        this.mcpEnabled = enabled;
        this.mcpPort = port;
        this.mcpConnectedClient = connectedClient;
    }

    void open() {
        lines = new ArrayList<>();
        checkJava(lines);
        checkCamelVersion(lines);
        checkJBang(lines);
        checkMavenRepository(lines);
        checkContainerRuntime(lines);
        checkCommonPorts(lines);
        checkDiskSpace(lines);
        checkAiProvider(lines);
        checkMcpConnection(lines);
        visible = true;
    }

    void close() {
        visible = false;
    }

    boolean handleKeyEvent(KeyEvent ke) {
        if (visible) {
            if (ke.isCancel()) {
                visible = false;
            }
            return true;
        }
        return false;
    }

    void render(Frame frame, Rect area) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        int popupW = Math.min(62, area.width() - 4);
        int popupH = Math.min(lines.size() + 2, area.height() - 4);
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + 2;
        Rect popup = new Rect(x, y, Math.min(popupW, area.width()), Math.min(popupH, area.height()));

        frame.renderWidget(Clear.INSTANCE, popup);
        Paragraph para = Paragraph.builder()
                .text(Text.from(lines.toArray(Line[]::new)))
                .block(Block.builder()
                        .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" " + TuiIcons.DOCTOR + " Doctor ")
                        .build())
                .build();
        frame.renderWidget(para, popup);
    }

    void renderFooter(List<Span> spans) {
        hintLast(spans, "Esc", "back");
    }

    // ---- Checks ----

    private void checkJava(List<Line> result) {
        String version = System.getProperty("java.version");
        String vendor = System.getProperty("java.vendor", "");
        int major = Runtime.version().feature();
        String status;
        String emoji;
        if (major >= 21) {
            status = null;
            emoji = TuiIcons.OK;
        } else if (major >= 17) {
            status = "Consider upgrading to 21 or 25";
            emoji = TuiIcons.WARN;
        } else {
            status = "17+ required";
            emoji = TuiIcons.FAIL;
        }
        result.add(Line.from(
                Span.raw(TuiIcons.indent(TuiIcons.JAVA)),
                Span.styled(String.format("%-14s", "Java"), Theme.muted()),
                Span.raw(String.format("%-30s", version + " (" + vendor + ")")),
                Span.raw(" " + emoji)));
        if (status != null) {
            result.add(Line.from(Span.styled("                    " + status, Style.EMPTY.dim())));
        }
    }

    private void checkCamelVersion(List<Line> result) {
        try {
            CamelCatalog catalog = new DefaultCamelCatalog();
            String version = catalog.getCatalogVersion();
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.CAMEL)),
                    Span.styled(String.format("%-14s", "Camel"), Theme.muted()),
                    Span.raw(String.format("%-30s", version)),
                    Span.raw(" " + TuiIcons.OK)));
        } catch (Exception e) {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.CAMEL)),
                    Span.styled(String.format("%-14s", "Camel"), Theme.muted()),
                    Span.raw(String.format("%-30s", "Not detected")),
                    Span.raw(" " + TuiIcons.FAIL)));
        }
    }

    private void checkJBang(List<Line> result) {
        String version = VersionHelper.getJBangVersion();
        if (version != null) {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.BUNDLED)),
                    Span.styled(String.format("%-14s", "JBang"), Theme.muted()),
                    Span.raw(String.format("%-30s", version)),
                    Span.raw(" " + TuiIcons.OK)));
        } else {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.BUNDLED)),
                    Span.styled(String.format("%-14s", "JBang"), Theme.muted()),
                    Span.raw(String.format("%-30s", "Not detected")),
                    Span.raw(" " + TuiIcons.WARN)));
        }
    }

    private void checkMavenRepository(List<Line> result) {
        try (MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            downloader.build();
            CamelCatalog catalog = new DefaultCamelCatalog();
            String version = catalog.getCatalogVersion();
            downloader.resolveArtifacts(
                    List.of("org.apache.camel:camel-api:" + version),
                    Set.of(), false, false);
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.INFRA)),
                    Span.styled(String.format("%-14s", "Maven"), Theme.muted()),
                    Span.raw(String.format("%-30s", "Artifact resolution")),
                    Span.raw(" " + TuiIcons.OK)));
        } catch (MavenResolutionException e) {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.INFRA)),
                    Span.styled(String.format("%-14s", "Maven"), Theme.muted()),
                    Span.raw(String.format("%-30s", "Resolution failed")),
                    Span.raw(" " + TuiIcons.FAIL)));
            result.add(Line.from(Span.styled("                    " + TuiHelper.truncate(e.getMessage(), 40),
                    Style.EMPTY.dim())));
        } catch (Exception e) {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.INFRA)),
                    Span.styled(String.format("%-14s", "Maven"), Theme.muted()),
                    Span.raw(String.format("%-30s", "Error")),
                    Span.raw(" " + TuiIcons.FAIL)));
            result.add(Line.from(Span.styled("                    " + TuiHelper.truncate(e.getMessage(), 40),
                    Style.EMPTY.dim())));
        }
    }

    private void checkContainerRuntime(List<Line> result) {
        for (String cmd : new String[] { "docker", "podman" }) {
            try {
                Process p = new ProcessBuilder(cmd, "info")
                        .redirectErrorStream(true)
                        .start();
                p.getInputStream().transferTo(OutputStream.nullOutputStream());
                boolean done = p.waitFor(5, TimeUnit.SECONDS);
                if (!done) {
                    p.destroyForcibly();
                    continue;
                }
                if (p.exitValue() == 0) {
                    String name = Character.toUpperCase(cmd.charAt(0)) + cmd.substring(1);
                    result.add(Line.from(
                            Span.raw(TuiIcons.indent(TuiIcons.DOCKER)),
                            Span.styled(String.format("%-14s", "Container"), Theme.muted()),
                            Span.raw(String.format("%-30s", name + " running")),
                            Span.raw(" " + TuiIcons.OK)));
                    return;
                }
            } catch (Exception e) {
                // not found, try next
            }
        }
        result.add(Line.from(
                Span.raw(TuiIcons.indent(TuiIcons.DOCKER)),
                Span.styled(String.format("%-14s", "Container"), Theme.muted()),
                Span.raw(String.format("%-30s", "Not found (optional)")),
                Span.raw(" " + TuiIcons.WARN)));
    }

    private void checkCommonPorts(List<Line> result) {
        StringBuilder conflicts = new StringBuilder();
        for (int port : new int[] { 8080, 8443, 9090 }) {
            if (isPortInUse(port)) {
                if (!conflicts.isEmpty()) {
                    conflicts.append(", ");
                }
                conflicts.append(port);
            }
        }
        if (!conflicts.isEmpty()) {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.ENDPOINT)),
                    Span.styled(String.format("%-14s", "Ports"), Theme.muted()),
                    Span.raw(String.format("%-30s", "In use: " + conflicts)),
                    Span.raw(" " + TuiIcons.WARN)));
        } else {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.ENDPOINT)),
                    Span.styled(String.format("%-14s", "Ports"), Theme.muted()),
                    Span.raw(String.format("%-30s", "8080, 8443, 9090 free")),
                    Span.raw(" " + TuiIcons.OK)));
        }
    }

    private static boolean isPortInUse(int port) {
        try (ServerSocket ss = new ServerSocket(port)) {
            ss.setReuseAddress(true);
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    private static boolean envSet(String name) {
        String v = System.getenv(name);
        return v != null && !v.isBlank();
    }

    private void checkAiProvider(List<Line> result) {
        String provider = null;
        if (envSet("ANTHROPIC_API_KEY")) {
            provider = "Anthropic";
        } else if (envSet("CLOUD_ML_REGION") && envSet("ANTHROPIC_VERTEX_PROJECT_ID")) {
            provider = "Vertex AI";
        } else if (envSet("OPENAI_API_KEY")) {
            provider = "OpenAI";
        } else if (envSet("LLM_API_KEY")) {
            provider = "Custom (LLM_API_KEY)";
        }
        if (provider != null) {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.MCP)),
                    Span.styled(String.format("%-14s", "AI"), Theme.muted()),
                    Span.raw(String.format("%-30s", provider)),
                    Span.raw(" " + TuiIcons.OK)));
        } else {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.MCP)),
                    Span.styled(String.format("%-14s", "AI"), Theme.muted()),
                    Span.raw(String.format("%-30s", "No API key configured")),
                    Span.raw(" " + TuiIcons.WARN)));
            result.add(Line.from(Span.styled("                    Set ANTHROPIC_API_KEY or OPENAI_API_KEY",
                    Style.EMPTY.dim())));
        }
    }

    private void checkMcpConnection(List<Line> result) {
        if (mcpEnabled) {
            String client = mcpConnectedClient != null ? mcpConnectedClient.get() : null;
            if (client != null) {
                result.add(Line.from(
                        Span.raw(TuiIcons.indent(TuiIcons.MCP)),
                        Span.styled(String.format("%-14s", "MCP"), Theme.muted()),
                        Span.raw(String.format("%-30s", client + " (port " + mcpPort + ")")),
                        Span.raw(" " + TuiIcons.OK)));
            } else {
                result.add(Line.from(
                        Span.raw(TuiIcons.indent(TuiIcons.MCP)),
                        Span.styled(String.format("%-14s", "MCP"), Theme.muted()),
                        Span.raw(String.format("%-30s", "Listening on port " + mcpPort)),
                        Span.raw(" " + TuiIcons.WARN)));
                result.add(Line.from(Span.styled("                    No AI client connected",
                        Style.EMPTY.dim())));
            }
        } else {
            result.add(Line.from(
                    Span.raw(TuiIcons.indent(TuiIcons.MCP)),
                    Span.styled(String.format("%-14s", "MCP"), Theme.muted()),
                    Span.raw(String.format("%-30s", "Not enabled")),
                    Span.raw(" " + TuiIcons.WARN)));
            result.add(Line.from(Span.styled("                    Use --mcp to enable MCP server",
                    Style.EMPTY.dim())));
        }
    }

    private void checkDiskSpace(List<Line> result) {
        File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        long free = tmpDir.getFreeSpace();
        long mb = free / (1024 * 1024);
        long gb = mb / 1024;
        String emoji = mb > 500 ? TuiIcons.OK : TuiIcons.WARN;
        String unit = gb > 10 ? "GB" : "MB";
        long value = gb > 0 ? gb : mb;
        result.add(Line.from(
                Span.raw(TuiIcons.indent(TuiIcons.MEMORY)),
                Span.styled(String.format("%-14s", "Disk Space"), Theme.muted()),
                Span.raw(String.format("%-30s", value + " " + unit + " free in temp dir")),
                Span.raw(" " + emoji)));
        if (mb <= 500) {
            result.add(Line.from(Span.styled("                    Low disk space may cause issues",
                    Style.EMPTY.dim())));
        }
    }
}
