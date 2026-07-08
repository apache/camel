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

import java.util.List;

import dev.tamboui.layout.Rect;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.TableState;

import static org.apache.camel.dsl.jbang.core.commands.tui.TuiHelper.*;

abstract class AbstractTableTab extends AbstractTab {

    protected final TableState tableState = new TableState();
    protected final ScrollbarState tableScrollState = new ScrollbarState();
    protected Rect lastTableArea;

    private final String[] sortColumns;
    protected String sort;
    protected int sortIndex;
    protected boolean sortReversed;

    protected AbstractTableTab(MonitorContext ctx, String... sortColumns) {
        super(ctx);
        this.sortColumns = sortColumns.length > 0 ? sortColumns : null;
        if (this.sortColumns != null) {
            this.sort = this.sortColumns[0];
        }
    }

    protected abstract int getRowCount();

    protected abstract void renderContent(Frame frame, Rect area, IntegrationInfo info);

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (sortColumns != null) {
            if (ke.isChar('s')) {
                sortIndex = (sortIndex + 1) % sortColumns.length;
                sort = sortColumns[sortIndex];
                sortReversed = false;
                return true;
            }
            if (ke.isChar('S')) {
                sortReversed = !sortReversed;
                return true;
            }
        }
        if (handleTabKeyEvent(ke)) {
            return true;
        }
        if (ke.isPageUp() || ke.isKey(KeyCode.PAGE_UP)) {
            for (int i = 0; i < 20 && tableState.selected() != null && tableState.selected() > 0; i++) {
                tableState.selectPrevious();
            }
            return true;
        }
        if (ke.isPageDown() || ke.isKey(KeyCode.PAGE_DOWN)) {
            for (int i = 0; i < 20; i++) {
                tableState.selectNext(getRowCount());
            }
            return true;
        }
        if (ke.isHome()) {
            tableState.selectFirst();
            return true;
        }
        if (ke.isEnd()) {
            tableState.selectLast(getRowCount());
            return true;
        }
        return false;
    }

    protected boolean handleTabKeyEvent(KeyEvent ke) {
        return false;
    }

    @Override
    public void navigateUp() {
        tableState.selectPrevious();
    }

    @Override
    public void navigateDown() {
        tableState.selectNext(getRowCount());
    }

    @Override
    public boolean handleMouseEvent(MouseEvent me, Rect area) {
        return handleTableClick(me, lastTableArea, tableState, getRowCount());
    }

    @Override
    public void render(Frame frame, Rect area) {
        IntegrationInfo info = ctx.findSelectedIntegration();
        if (info == null) {
            renderNoSelection(frame, area);
            return;
        }
        renderContent(frame, area, info);
    }

    @Override
    public void renderFooter(List<Span> spans) {
        hint(spans, "Esc", "back");
        if (sortColumns != null) {
            hint(spans, "s", "sort");
        }
    }

    protected String sortLabel(String label, String column) {
        return sortLabel(label, column, sort, sortReversed);
    }

    protected Style sortStyle(String column) {
        return sortStyle(column, sort);
    }

    protected void renderScrollbar(Frame frame, int rowCount) {
        renderTableScrollbar(frame, lastTableArea, tableState, tableScrollState, rowCount);
    }
}
