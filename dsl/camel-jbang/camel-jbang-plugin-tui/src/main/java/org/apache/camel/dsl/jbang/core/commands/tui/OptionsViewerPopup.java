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
import java.util.Set;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Overflow;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.tui.event.MouseEventKind;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.LanguageModel;
import org.apache.camel.tooling.model.OtherModel;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hint;
import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.hintLast;

class OptionsViewerPopup {

    private boolean visible;
    private String title;
    private String currentKind;
    private CamelCatalog currentCatalog;
    private String[] tabLabels;
    private List<List<Line>> tabContent;
    private int activeTab;
    private int[] tabScroll;
    private boolean wantsDoc;

    private final ScrollbarState scrollbarState = new ScrollbarState();

    boolean isVisible() {
        return visible;
    }

    boolean consumeWantsDoc() {
        boolean v = wantsDoc;
        wantsDoc = false;
        return v;
    }

    String getCurrentName() {
        return title;
    }

    String getCurrentKind() {
        return currentKind;
    }

    CamelCatalog getCurrentCatalog() {
        return currentCatalog;
    }

    void close() {
        visible = false;
    }

    void open(String name, String kind, CamelCatalog catalog) {
        if (catalog == null) {
            return;
        }
        this.title = name;
        this.currentKind = kind;
        this.currentCatalog = catalog;
        this.activeTab = 0;
        this.wantsDoc = false;

        switch (kind) {
            case "component" -> openComponent(name, catalog);
            case "dataformat" -> openDataFormat(name, catalog);
            case "language" -> openLanguage(name, catalog);
            case "eip" -> openEip(name, catalog);
            default -> openOther(name, catalog);
        }

        if (tabContent == null || tabContent.isEmpty()) {
            visible = false;
            return;
        }
        tabScroll = new int[tabLabels.length];
        visible = true;
    }

    private void openComponent(String name, CamelCatalog catalog) {
        ComponentModel model = catalog.componentModel(name);
        if (model == null) {
            return;
        }
        tabLabels = new String[] { "Component", "Endpoint", "Headers" };
        tabContent = new ArrayList<>();
        tabContent.add(buildOptionLines(model.getComponentOptions()));
        tabContent.add(buildOptionLines(model.getEndpointOptions()));
        tabContent.add(buildHeaderLines(model.getEndpointHeaders()));
    }

    private void openDataFormat(String name, CamelCatalog catalog) {
        DataFormatModel model = catalog.dataFormatModel(name);
        if (model == null) {
            return;
        }
        tabLabels = new String[] { "Options" };
        tabContent = new ArrayList<>();
        tabContent.add(buildOptionLines(model.getOptions()));
    }

    private void openLanguage(String name, CamelCatalog catalog) {
        LanguageModel model = catalog.languageModel(name);
        if (model == null) {
            return;
        }
        tabLabels = new String[] { "Options" };
        tabContent = new ArrayList<>();
        tabContent.add(buildOptionLines(model.getOptions()));
    }

    private static final Set<String> EIP_SKIP_OPTIONS = Set.of("input", "output", "outputs");

    private void openEip(String name, CamelCatalog catalog) {
        EipModel model = catalog.eipModel(name);
        if (model == null) {
            return;
        }
        List<? extends BaseOptionModel> filtered = model.getOptions().stream()
                .filter(o -> !EIP_SKIP_OPTIONS.contains(o.getName()))
                .toList();
        List<Line> optLines = buildOptionLines(filtered);
        List<Line> propLines = buildOptionLines(model.getExchangeProperties());
        if (!propLines.isEmpty()) {
            tabLabels = new String[] { "Options", "Properties" };
            tabContent = new ArrayList<>();
            tabContent.add(optLines);
            tabContent.add(propLines);
        } else {
            tabLabels = new String[] { "Options" };
            tabContent = new ArrayList<>();
            tabContent.add(optLines);
        }
    }

    private void openOther(String name, CamelCatalog catalog) {
        OtherModel model = catalog.otherModel(name);
        if (model == null) {
            return;
        }
        tabLabels = new String[] { "Options" };
        tabContent = new ArrayList<>();
        tabContent.add(buildOptionLines(model.getOptions()));
    }

    // --- Option line building ---

    private List<Line> buildOptionLines(List<? extends BaseOptionModel> options) {
        List<Line> lines = new ArrayList<>();
        if (options == null || options.isEmpty()) {
            lines.add(Line.from(Span.styled("  No options", Style.EMPTY.dim())));
            return lines;
        }
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                lines.add(Line.from(""));
            }
            BaseOptionModel opt = options.get(i);
            buildSingleOption(lines, opt);
        }
        return lines;
    }

    private void buildSingleOption(List<Line> lines, BaseOptionModel opt) {
        List<Span> header = new ArrayList<>();
        header.add(Span.styled("  " + opt.getName(), Style.EMPTY.fg(Theme.accent())));

        String type = opt.getShortJavaType();
        if (type != null) {
            header.add(Span.styled(" (" + type + ")", Style.EMPTY.dim()));
        }

        if (opt.isRequired()) {
            header.add(Span.styled(" [required]", Theme.warning()));
        }
        if (opt.isDeprecated()) {
            header.add(Span.styled(" [deprecated]", Theme.error()));
        }
        if (opt.isSecret()) {
            header.add(Span.styled(" [secret]", Style.EMPTY.fg(Theme.accent()).dim()));
        }

        lines.add(Line.from(header));

        Object dv = opt.getDefaultValue();
        if (dv != null) {
            String dvStr = dv.toString();
            if (!dvStr.isEmpty()) {
                lines.add(Line.from(
                        Span.styled("    Default: ", Style.EMPTY.dim()),
                        Span.raw(dvStr)));
            }
        }

        List<String> enums = opt.getEnums();
        if (enums != null && !enums.isEmpty()) {
            String enumStr = String.join(", ", enums);
            lines.add(Line.from(
                    Span.styled("    Enum: ", Style.EMPTY.dim()),
                    Span.raw(enumStr)));
        }

        String group = opt.getGroup();
        if (group != null && !group.isEmpty()) {
            lines.add(Line.from(
                    Span.styled("    Group: ", Style.EMPTY.dim()),
                    Span.raw(group)));
        }

        String desc = opt.getDescription();
        if (desc != null && !desc.isEmpty()) {
            lines.add(Line.from(
                    Span.styled("    ", Style.EMPTY),
                    Span.styled(desc, Style.EMPTY.dim())));
        }
    }

    private List<Line> buildHeaderLines(List<ComponentModel.EndpointHeaderModel> headers) {
        List<Line> lines = new ArrayList<>();
        if (headers == null || headers.isEmpty()) {
            lines.add(Line.from(Span.styled("  No headers", Style.EMPTY.dim())));
            return lines;
        }
        for (int i = 0; i < headers.size(); i++) {
            if (i > 0) {
                lines.add(Line.from(""));
            }
            ComponentModel.EndpointHeaderModel h = headers.get(i);
            buildSingleHeader(lines, h);
        }
        return lines;
    }

    private void buildSingleHeader(List<Line> lines, ComponentModel.EndpointHeaderModel h) {
        List<Span> header = new ArrayList<>();
        header.add(Span.styled("  " + h.getName(), Style.EMPTY.fg(Theme.accent())));

        String type = h.getShortJavaType();
        if (type != null) {
            header.add(Span.styled(" (" + type + ")", Style.EMPTY.dim()));
        }

        String group = h.getGroup();
        if (group != null && !group.isEmpty()) {
            header.add(Span.styled("  [" + group + "]", Theme.info()));
        }

        lines.add(Line.from(header));

        String constant = h.getConstantName();
        if (constant != null && !constant.isEmpty()) {
            lines.add(Line.from(
                    Span.styled("    Constant: ", Style.EMPTY.dim()),
                    Span.raw(constant)));
        }

        String desc = h.getDescription();
        if (desc != null && !desc.isEmpty()) {
            lines.add(Line.from(
                    Span.styled("    ", Style.EMPTY),
                    Span.styled(desc, Style.EMPTY.dim())));
        }
    }

    // --- Key handling ---

    boolean handleKeyEvent(KeyEvent ke) {
        if (!visible) {
            return false;
        }
        if (ke.isCancel()) {
            visible = false;
            return true;
        }
        if (ke.isCharIgnoreCase('d')) {
            wantsDoc = true;
            visible = false;
            return true;
        }
        if (ke.isUp() || ke.isChar('k')) {
            tabScroll[activeTab] = Math.max(0, tabScroll[activeTab] - 1);
            return true;
        }
        if (ke.isDown() || ke.isChar('j')) {
            tabScroll[activeTab]++;
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            tabScroll[activeTab] = Math.max(0, tabScroll[activeTab] - 10);
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            tabScroll[activeTab] += 10;
            return true;
        }
        if (ke.isHome() || ke.isKey(KeyCode.HOME)) {
            tabScroll[activeTab] = 0;
            return true;
        }
        if (ke.isEnd() || ke.isKey(KeyCode.END)) {
            tabScroll[activeTab] = Integer.MAX_VALUE;
            return true;
        }
        if (tabLabels.length > 1) {
            if (ke.isKey(KeyCode.TAB) || ke.isRight()) {
                activeTab = (activeTab + 1) % tabLabels.length;
                return true;
            }
            if (ke.isLeft()) {
                activeTab = (activeTab - 1 + tabLabels.length) % tabLabels.length;
                return true;
            }
        }
        return true;
    }

    boolean handleMouseEvent(MouseEvent me) {
        if (!visible) {
            return false;
        }
        if (me.kind() == MouseEventKind.SCROLL_UP) {
            tabScroll[activeTab] = Math.max(0, tabScroll[activeTab] - 3);
            return true;
        }
        if (me.kind() == MouseEventKind.SCROLL_DOWN) {
            tabScroll[activeTab] += 3;
            return true;
        }
        return true;
    }

    // --- Rendering ---

    void render(Frame frame, Rect area) {
        if (!visible) {
            return;
        }

        frame.renderWidget(Clear.INSTANCE, area);

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED).borders(Borders.ALL)
                .title(buildTitle())
                .titleBottom(buildFooter())
                .build();
        frame.renderWidget(block, area);

        Rect inner = block.inner(area);
        List<Rect> hChunks = Layout.horizontal()
                .constraints(Constraint.fill(), Constraint.length(1))
                .split(inner);

        List<Line> content = tabContent.get(activeTab);
        int visibleLines = hChunks.get(0).height();
        int totalLines = content.size();
        int clampedScroll = Math.min(tabScroll[activeTab], Math.max(0, totalLines - visibleLines));
        tabScroll[activeTab] = clampedScroll;
        int end = Math.min(clampedScroll + visibleLines, totalLines);

        List<Line> visible = new ArrayList<>(content.subList(clampedScroll, end));
        while (visible.size() < visibleLines) {
            visible.add(Line.from(""));
        }

        frame.renderWidget(
                Paragraph.builder().text(Text.from(visible.toArray(Line[]::new)))
                        .overflow(Overflow.CLIP).build(),
                hChunks.get(0));

        if (totalLines > visibleLines) {
            scrollbarState
                    .contentLength(totalLines)
                    .viewportContentLength(visibleLines)
                    .position(clampedScroll);
            frame.renderStatefulWidget(Scrollbar.builder().build(), hChunks.get(1), scrollbarState);
        }
    }

    private Title buildTitle() {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.raw(" " + title + " Options  "));

        for (int i = 0; i < tabLabels.length; i++) {
            if (i > 0) {
                spans.add(Span.styled(" │ ", Style.EMPTY.dim()));
            }
            if (i == activeTab) {
                spans.add(Span.styled(tabLabels[i], Style.EMPTY.bold()));
            } else {
                spans.add(Span.styled(tabLabels[i], Style.EMPTY.dim()));
            }
        }
        spans.add(Span.raw(" "));

        return Title.from(Line.from(spans));
    }

    private Title buildFooter() {
        List<Span> spans = new ArrayList<>();
        spans.add(Span.styled(" Esc ", Theme.hintKey()));
        spans.add(Span.raw(" back  "));
        spans.add(Span.styled(" ↑↓ ", Theme.hintKey()));
        spans.add(Span.raw(" scroll  "));
        if (tabLabels != null && tabLabels.length > 1) {
            spans.add(Span.styled(" ←→ ", Theme.hintKey()));
            spans.add(Span.raw(" tab  "));
        }
        spans.add(Span.styled(" d ", Theme.hintKey()));
        spans.add(Span.raw(" doc "));
        return Title.from(Line.from(spans));
    }

    void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        hint(spans, "↑↓", "scroll");
        if (tabLabels != null && tabLabels.length > 1) {
            hint(spans, "←→", "tab");
        }
        hintLast(spans, "d", "doc");
    }
}
