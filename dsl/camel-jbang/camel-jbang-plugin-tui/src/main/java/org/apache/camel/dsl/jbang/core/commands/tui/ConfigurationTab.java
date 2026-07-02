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

import java.util.ArrayList;
import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class ConfigurationTab extends AbstractTab {

    private static final int MOUSE_SCROLL_LINES = 3;
    private static final Style KEY_STYLE = Style.EMPTY.fg(Color.CYAN);
    private static final Style VALUE_STYLE = Style.EMPTY.fg(Color.WHITE);
    private static final Style SECRET_STYLE = Style.EMPTY.fg(Color.DARK_GRAY);
    private static final Style SOURCE_STYLE = Style.EMPTY.dim();

    private final ScrollbarState scrollbarState = new ScrollbarState();
    private int scrollOffset;

    ConfigurationTab(MonitorContext ctx) {
        super(ctx);
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isUp()) {
            scrollOffset = Math.max(0, scrollOffset - 1);
            return true;
        }
        if (ke.isDown()) {
            scrollOffset++;
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            scrollOffset = Math.max(0, scrollOffset - 20);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            scrollOffset += 20;
            return true;
        }
        if (ke.isHome()) {
            scrollOffset = 0;
            return true;
        }
        if (ke.isEnd()) {
            scrollOffset = Integer.MAX_VALUE;
            return true;
        }
        return false;
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            scrollOffset = Math.max(0, scrollOffset - MOUSE_SCROLL_LINES);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            scrollOffset += MOUSE_SCROLL_LINES;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        scrollOffset = Math.max(0, scrollOffset - 1);
    }

    @Override
    public void navigateDown() {
        scrollOffset++;
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }

        List<ConfigProperty> props = info.configProperties;
        if (props.isEmpty()) {
            frame.renderWidget(
                    Paragraph.builder()
                            .text(Text.from(Line.from(
                                    Span.styled("  No configuration properties available.", Style.EMPTY.dim()))))
                            .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                                    .title(" Configuration ").build())
                            .build(),
                    area);
            return;
        }

        // Find divider position: index of first non-camel property
        int dividerIndex = -1;
        for (int i = 0; i < props.size(); i++) {
            if (!props.get(i).key.startsWith("camel.")) {
                dividerIndex = i;
                break;
            }
        }
        boolean hasDivider = dividerIndex > 0 && dividerIndex < props.size();
        int totalLines = props.size() + (hasDivider ? 1 : 0);

        String title = String.format(" Configuration — %d properties ", props.size());
        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(title)
                .build();
        Rect inner = block.inner(area);
        frame.renderWidget(block, area);

        if (inner.height() < 1 || inner.width() < 10) {
            return;
        }

        int visibleLines = inner.height();
        int maxScroll = Math.max(0, totalLines - visibleLines);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        // Compute max key length across visible properties for alignment
        int maxKeyLen = 0;
        for (ConfigProperty p : props) {
            maxKeyLen = Math.max(maxKeyLen, p.key.length());
        }
        int keyWidth = Math.min(maxKeyLen, inner.width() / 2);

        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);
        int lineWidth = hChunks.get(0).width();

        // Build visible lines, inserting divider at the right display position
        List<Line> lines = new ArrayList<>();
        int displayRow = 0;
        for (int i = 0; i < props.size() && lines.size() < visibleLines; i++) {
            if (hasDivider && i == dividerIndex) {
                if (displayRow >= scrollOffset) {
                    String divText = "─".repeat(Math.max(1, inner.width() - 2));
                    lines.add(Line.from(Span.styled(" " + divText, Style.EMPTY.dim())));
                }
                displayRow++;
                if (lines.size() >= visibleLines) {
                    break;
                }
            }
            if (displayRow >= scrollOffset) {
                lines.add(renderProperty(props.get(i), keyWidth, lineWidth));
            }
            displayRow++;
        }

        frame.renderWidget(Paragraph.builder().text(Text.from(lines)).build(), hChunks.get(0));

        if (totalLines > visibleLines) {
            scrollbarState
                    .contentLength(totalLines)
                    .viewportContentLength(visibleLines)
                    .position(scrollOffset);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), scrollbarState);
        }
    }

    private Line renderProperty(ConfigProperty prop, int keyWidth, int lineWidth) {
        String key = prop.key;
        if (key.length() > keyWidth) {
            key = key.substring(0, keyWidth - 1) + "…";
        } else {
            key = String.format("%-" + keyWidth + "s", key);
        }

        boolean secret = "xxxxxx".equals(prop.value);
        Style valStyle = secret ? SECRET_STYLE : VALUE_STYLE;
        String value = prop.value != null ? prop.value : "";

        List<Span> spans = new ArrayList<>();
        String keyPart = "  " + key + "  ";
        spans.add(Span.styled(keyPart, KEY_STYLE));
        spans.add(Span.styled(value, valStyle));
        if (prop.source != null && !prop.source.isEmpty()) {
            String displaySource = FileUtil.stripPath(prop.source);
            String sourceText = "[" + displaySource + "]";
            int used = keyPart.length() + value.length();
            int gap = Math.max(2, lineWidth - used - sourceText.length());
            spans.add(Span.styled(" ".repeat(gap) + sourceText, SOURCE_STYLE));
        }

        return Line.from(spans);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "scroll");
        hintLast(spans, "PgUp/Dn", "page");
    }

    @Override
    public SelectionContext getSelectionContext() {
        return null;
    }

    static int compareCamelFirst(ConfigProperty a, ConfigProperty b) {
        boolean aCamel = a.key.startsWith("camel.");
        boolean bCamel = b.key.startsWith("camel.");
        if (aCamel != bCamel) {
            return aCamel ? -1 : 1;
        }
        return a.key.compareToIgnoreCase(b.key);
    }

    static class ConfigProperty {
        String key;
        String value;
        String defaultValue;
        String source;
        String location;
    }

    @Override
    public String getHelpText() {
        return """
                # Configuration

                The Configuration tab shows all configuration properties of the running
                integration. This provides a complete view of how the integration is
                configured at runtime — including Camel settings, component options,
                and application properties.

                Properties can come from multiple sources and Camel merges them with
                a defined priority order.

                ## Table Columns

                - **KEY** — Property name following Camel's naming convention (e.g., `camel.main.name`, `camel.component.kafka.brokers`, `greeting.message`)
                - **VALUE** — Current resolved property value. Sensitive values (passwords, tokens) are masked as `xxxxxx` for security
                - **DEFAULT** — Default value (shown only if different from current value). Helps identify which properties were explicitly configured vs using defaults
                - **SOURCE** — Where the property was set:
                  - `application.properties` — from the main properties file
                  - `ENV` — from an environment variable
                  - `SYS` — from a Java system property (`-D`)
                  - `camel-component` — default from a Camel component
                  - `override` — set programmatically in code
                  - `initial` — set during context initialization

                ## Example Screen

                ```
                 KEY                              VALUE              DEFAULT      SOURCE
                 camel.main.name                  camel-demo                      application.properties
                 camel.main.shutdownTimeout       30                 300          application.properties
                 camel.component.kafka.brokers    localhost:9092                   application.properties
                 greeting.message                 Hello World                     application.properties
                ```

                ## Property Namespaces

                Common property prefixes and what they configure:

                - `camel.main.*` — Core Camel settings (name, shutdown timeout, tracing, etc.)
                - `camel.component.*` — Component-level defaults (applied to all endpoints of that component)
                - `camel.dataformat.*` — Data format defaults (JSON, XML, CSV serialization options)
                - `camel.language.*` — Expression language settings
                - `camel.server.*` — Embedded HTTP server configuration (port, host, CORS)

                ## Use Cases

                - **Verify configuration**: Confirm that properties from files, environment variables, and system properties are being applied correctly
                - **Debug overrides**: When a property has an unexpected value, check the SOURCE column to see where it was set
                - **Audit security**: Verify that sensitive properties (passwords, API keys) are properly masked

                ## Keys

                - `Up/Down` — navigate properties
                - `s` — cycle sort column
                - `S` — reverse sort order
                - `Esc` — back
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "Configuration");
        JsonArray rows = new JsonArray();
        for (ConfigProperty cp : info.configProperties) {
            JsonObject row = new JsonObject();
            row.put("key", cp.key);
            row.put("value", cp.value);
            if (cp.defaultValue != null) {
                row.put("defaultValue", cp.defaultValue);
            }
            if (cp.source != null) {
                row.put("source", cp.source);
            }
            if (cp.location != null) {
                row.put("location", cp.location);
            }
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.configProperties.size());
        return result;
    }
}
