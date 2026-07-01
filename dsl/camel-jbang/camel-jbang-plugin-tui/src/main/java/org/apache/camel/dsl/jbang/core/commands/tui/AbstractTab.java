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
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Span;
import dev.tamboui.tui.event.MouseEvent;
import dev.tamboui.widgets.scrollbar.Scrollbar;
import dev.tamboui.widgets.scrollbar.ScrollbarState;
import dev.tamboui.widgets.table.TableState;

abstract class AbstractTab implements MonitorTab {

    protected final MonitorContext ctx;

    protected AbstractTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public boolean handleEscape() {
        return false;
    }

    @Override
    public void navigateUp() {
    }

    @Override
    public void navigateDown() {
    }

    @Override
    public void renderFooter(List<Span> spans) {
    }

    protected static void renderTableScrollbar(
            Frame frame, Rect tableArea, TableState tableState, ScrollbarState scrollState, int rowCount) {
        if (tableArea == null || tableState == null || scrollState == null) {
            return;
        }
        int visibleRows = tableArea.height() - 3;
        if (visibleRows <= 0 || rowCount <= visibleRows) {
            return;
        }
        Rect scrollRect = new Rect(
                tableArea.x() + tableArea.width() - 1,
                tableArea.y() + 2,
                1,
                visibleRows);
        scrollState.contentLength(rowCount);
        scrollState.viewportContentLength(visibleRows);
        scrollState.position(tableState.offset());
        frame.renderStatefulWidget(Scrollbar.builder().build(), scrollRect, scrollState);
    }

    protected static int compareStr(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareToIgnoreCase(b);
    }

    protected static boolean contains(Rect rect, int x, int y) {
        return rect != null
                && x >= rect.x() && x < rect.x() + rect.width()
                && y >= rect.y() && y < rect.y() + rect.height();
    }

    protected static boolean handleTableClick(MouseEvent me, Rect tableArea, TableState tableState, int rowCount) {
        if (tableArea == null || tableState == null || rowCount <= 0) {
            return false;
        }
        if (!me.isClick()) {
            return false;
        }
        if (!contains(tableArea, me.x(), me.y())) {
            return false;
        }
        int rowIndex = tableState.offset() + (me.y() - tableArea.y() - 2);
        if (rowIndex >= 0 && rowIndex < rowCount) {
            tableState.select(rowIndex);
            return true;
        }
        return false;
    }
}
