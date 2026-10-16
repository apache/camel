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

import dev.tamboui.style.Style;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;

/**
 * Go to Route of the source editor: the routes of the project with their endpoint and file. Also lists other places
 * under another name, such as the usages of an endpoint.
 */
class GotoRoutePopup extends FilterListPopup<GotoRoutePopup.RouteItem> {

    private List<RouteItem> allEntries;
    private RouteItem selectedEntry;
    private String name = "Go to Route";

    record RouteItem(String routeId, String fromUri, String filePath, int fromLine) {
    }

    void open(List<SourceTab.RouteEntry> routeIndex) {
        List<RouteItem> items = new ArrayList<>();
        for (SourceTab.RouteEntry re : routeIndex) {
            items.add(new RouteItem(re.routeId(), re.fromUri(), re.filePath(), re.fromLine()));
        }
        openItems(items, "Go to Route");
    }

    /** Opens the popup with the given places, such as the usages of an endpoint, under the given name. */
    void openItems(List<RouteItem> items, String name) {
        this.name = name;
        allEntries = new ArrayList<>(items);
        show();
    }

    /** The place chosen with Enter, or null; cleared by the call. */
    RouteItem consumeSelection() {
        RouteItem entry = selectedEntry;
        selectedEntry = null;
        return entry;
    }

    @Override
    protected List<RouteItem> entries() {
        return allEntries;
    }

    @Override
    protected String name() {
        return name;
    }

    @Override
    protected String matchText(RouteItem entry) {
        return entry.routeId() + " " + entry.fromUri();
    }

    @Override
    protected boolean matches(RouteItem entry, String typed) {
        return matchesText(matchText(entry), typed);
    }

    @Override
    protected void chosen(RouteItem entry) {
        selectedEntry = entry;
    }

    @Override
    protected List<Line> rows(List<RouteItem> entries, int width) {
        // the route ids and endpoints take the width of the longest, so the columns line up
        int maxIdW = 0;
        int maxUriW = 0;
        for (RouteItem entry : entries) {
            maxIdW = Math.max(maxIdW, entry.routeId().length());
            maxUriW = Math.max(maxUriW, entry.fromUri().equals(entry.routeId()) ? 0 : entry.fromUri().length());
        }

        Style matchStyle = Theme.label().bold();
        Style dimStyle = Style.EMPTY.dim();
        List<Line> rows = new ArrayList<>();
        for (RouteItem entry : entries) {
            List<Span> spans = new ArrayList<>();
            spans.add(Span.raw(" "));
            String routeId = entry.routeId();
            spans.addAll(highlighted(routeId, Style.EMPTY, matchStyle));
            spans.add(Span.raw(" ".repeat(Math.max(0, maxIdW - routeId.length()))));

            // a route without an id is named by its endpoint: no need to show it twice
            String uri = entry.fromUri().equals(entry.routeId()) ? "" : entry.fromUri();
            String paddedUri = uri + " ".repeat(Math.max(0, maxUriW - uri.length()));
            spans.add(Span.styled("  " + paddedUri, dimStyle));

            String fileName = entry.filePath();
            int lastSep = fileName.lastIndexOf('/');
            if (lastSep >= 0) {
                fileName = fileName.substring(lastSep + 1);
            }
            spans.add(Span.styled("  " + fileName + ":" + (entry.fromLine() + 1), dimStyle));
            rows.add(Line.from(spans));
        }
        return rows;
    }
}
