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
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

class DataSourceTab extends AbstractTableTab {

    DataSourceTab(MonitorContext ctx) {
        super(ctx, "name", "pool", "active", "idle", "total", "max", "waiting");
    }

    @Override
    public void navigateUp() {
    }

    @Override
    public void navigateDown() {
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)
                || ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)
                || ke.isHome() || ke.isEnd()) {
            return false;
        }
        return super.handleKeyEvent(ke);
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        return false;
    }

    @Override
    protected int getRowCount() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        return info != null ? info.dataSources.size() : 0;
    }

    @Override
    protected void renderContent(Frame frame, Rect area, IntegrationInfo info) {
        List<DataSourceInfo> sorted = new ArrayList<>(info.dataSources);
        sorted.sort(this::sortDataSource);

        List<Row> rows = new ArrayList<>();
        for (DataSourceInfo di : sorted) {
            boolean exhausted = di.maxPoolSize > 0 && di.active >= di.maxPoolSize;
            boolean waiters = di.waiting > 0;

            Style activeStyle = exhausted
                    ? Style.EMPTY.fg(Color.LIGHT_RED)
                    : Style.EMPTY.fg(Color.CYAN);
            Style waitingStyle = waiters
                    ? Style.EMPTY.fg(Color.YELLOW)
                    : Style.EMPTY;

            String poolLabel = di.poolName != null ? di.poolName : (di.poolType != null ? di.poolType : "");

            rows.add(Row.from(
                    Cell.from(Span.styled(" " + (di.name != null ? di.name : ""), Style.EMPTY.fg(Color.CYAN))),
                    Cell.from(poolLabel),
                    rightCell(String.valueOf(di.active), 8, activeStyle),
                    rightCell(String.valueOf(di.idle), 8),
                    rightCell(String.valueOf(di.total), 8),
                    rightCell(di.maxPoolSize > 0 ? String.valueOf(di.maxPoolSize) : "", 8),
                    rightCell(String.valueOf(di.waiting), 8, waitingStyle),
                    Cell.from(Span.styled(shortType(di.type), Style.EMPTY.dim()))));
        }

        if (rows.isEmpty()) {
            rows.add(emptyRow("No DataSources", 8));
        }

        Table table = Table.builder()
                .rows(rows)
                .header(Row.from(
                        Cell.from(Span.styled(" " + sortLabel("NAME", "name"), sortStyle("name"))),
                        Cell.from(Span.styled(sortLabel("POOL", "pool"), sortStyle("pool"))),
                        rightCell(sortLabel("ACTIVE", "active"), 8, sortStyle("active")),
                        rightCell(sortLabel("IDLE", "idle"), 8, sortStyle("idle")),
                        rightCell(sortLabel("TOTAL", "total"), 8, sortStyle("total")),
                        rightCell(sortLabel("MAX", "max"), 8, sortStyle("max")),
                        rightCell(sortLabel("WAITING", "waiting"), 8, sortStyle("waiting")),
                        Cell.from(Span.styled("TYPE", Style.EMPTY.bold()))))
                .widths(
                        Constraint.length(22),
                        Constraint.length(16),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.length(8),
                        Constraint.fill())
                .block(Block.builder().borderType(BorderType.ROUNDED).borders(Borders.ALL)
                        .title(" DataSource sort:" + sort + " ").build())
                .build();

        lastTableArea = area;
        frame.renderStatefulWidget(table, area, tableState);
        renderScrollbar(frame, sorted.size());
    }

    private int sortDataSource(DataSourceInfo a, DataSourceInfo b) {
        int result = switch (sort) {
            case "pool" -> {
                String pa = a.poolType != null ? a.poolType : "";
                String pb = b.poolType != null ? b.poolType : "";
                yield pa.compareToIgnoreCase(pb);
            }
            case "active" -> Integer.compare(b.active, a.active);
            case "idle" -> Integer.compare(b.idle, a.idle);
            case "total" -> Integer.compare(b.total, a.total);
            case "max" -> Integer.compare(b.maxPoolSize, a.maxPoolSize);
            case "waiting" -> Integer.compare(b.waiting, a.waiting);
            default -> { // "name"
                String na = a.name != null ? a.name : "";
                String nb = b.name != null ? b.name : "";
                yield na.compareToIgnoreCase(nb);
            }
        };
        return sortReversed ? -result : result;
    }

    private static String shortType(String type) {
        if (type == null) {
            return "";
        }
        int dot = type.lastIndexOf('.');
        return dot >= 0 ? type.substring(dot + 1) : type;
    }

    @Override
    public SelectionContext getSelectionContext() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null || info.dataSources.isEmpty()) {
            return null;
        }
        List<DataSourceInfo> sorted = new ArrayList<>(info.dataSources);
        sorted.sort(this::sortDataSource);
        List<String> items = sorted.stream().map(d -> d.name != null ? d.name : "").toList();
        Integer sel = tableState.selected();
        return new SelectionContext("table", items, sel != null ? sel : -1, items.size(), "DataSource");
    }

    @Override
    public String description() {
        return "JDBC DataSource pool statistics (active, idle, max connections)";
    }

    @Override
    public String getHelpText() {
        return """
                # DataSource

                Displays connection pool metrics for all `javax.sql.DataSource` beans
                registered in the Camel registry. Supports **HikariCP** (Spring Boot default)
                and **Agroal** (Quarkus default) connection pools.

                ## Table Columns

                - **NAME** — The bean name of the DataSource in the registry
                - **POOL** — The pool name (HikariCP pool name) or pool type (HikariCP / Agroal / Unknown)
                - **ACTIVE** — Number of connections currently in use. Shown in red when equal to MAX (pool exhausted)
                - **IDLE** — Number of idle connections available in the pool
                - **TOTAL** — Total connections (active + idle) currently managed by the pool
                - **MAX** — Maximum pool size configured. When ACTIVE reaches MAX, new connection requests will wait
                - **WAITING** — Number of threads waiting for a connection. Shown in yellow when > 0
                - **TYPE** — The Java class of the DataSource implementation

                ## Pool Exhaustion

                When **ACTIVE = MAX**, the pool is exhausted — no idle connections remain.
                New requests must wait for a connection to be released. The ACTIVE column
                turns red to signal this condition. If WAITING also increases, the application
                may experience timeouts.

                ## Connection Pools

                ### HikariCP
                The default connection pool for Spring Boot. Known for its speed and small
                footprint. Default max pool size is 10.

                ### Agroal
                The default connection pool for Quarkus. Provides leak detection and
                connection validation. Shows additional metrics like max-used and leak count.

                ## Keys

                - `Up/Down` — select datasource
                - `s` — cycle sort column
                - `S` — reverse sort order
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            return null;
        }
        JsonObject result = new JsonObject();
        result.put("tab", "DataSource");
        JsonArray rows = new JsonArray();
        for (DataSourceInfo di : info.dataSources) {
            JsonObject row = new JsonObject();
            row.put("name", di.name);
            row.put("type", di.type);
            row.put("poolType", di.poolType);
            if (di.poolName != null) {
                row.put("poolName", di.poolName);
            }
            row.put("active", di.active);
            row.put("idle", di.idle);
            row.put("total", di.total);
            row.put("maxPoolSize", di.maxPoolSize);
            row.put("waiting", di.waiting);
            rows.add(row);
        }
        result.put("rows", rows);
        result.put("totalRows", info.dataSources.size());
        Integer sel = tableState.selected();
        result.put("selectedIndex", sel != null ? sel : -1);
        return result;
    }
}
