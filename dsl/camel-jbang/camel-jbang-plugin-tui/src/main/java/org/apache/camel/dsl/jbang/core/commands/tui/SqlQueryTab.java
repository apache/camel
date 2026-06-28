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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Title;
import dev.tamboui.widgets.input.TextArea;
import dev.tamboui.widgets.input.TextAreaState;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

import static org.apache.camel.dsl.jbang.core.commands.tui.MonitorContext.*;

class SqlQueryTab implements MonitorTab {

    private final MonitorContext ctx;
    private final TextAreaState sqlInput = new TextAreaState();
    private final TableState tableState = new TableState();
    private final AtomicBoolean executing = new AtomicBoolean();
    private final InputHistory sqlHistory = new InputHistory();

    // datasource selection
    private List<String> dsNames = new ArrayList<>();
    private int selectedDs;
    private boolean focusOnInput = true;

    // results
    private String[] columnNames;
    private boolean[] columnIsPk;
    private List<JsonObject> resultRows;
    private int rowCount;
    private boolean truncated;
    private long elapsed;
    private String errorMessage;
    private Integer updateCount;

    // editability metadata from response
    private String tableName;
    private String[] primaryKeys;

    // edit row state
    private boolean editMode;
    private int editField;
    private int editScrollOffset;
    private TextInputState[] editInputs;
    private String[] editOriginalValues;
    private int editRowIndex;
    private String editUpdateMessage;

    // store last query for re-execution after update
    private String lastSql;
    private String lastDsName;

    SqlQueryTab(MonitorContext ctx) {
        this.ctx = ctx;
    }

    boolean isInputActive() {
        return editMode || focusOnInput;
    }

    void handlePaste(String text) {
        if (editMode) {
            if (editInputs != null && editField >= 0 && editField < editInputs.length
                    && !columnIsPk[editField]) {
                FormHelper.handlePaste(text, editInputs[editField]);
            }
        } else if (focusOnInput) {
            sqlInput.insert(text);
        }
    }

    @Override
    public void onIntegrationChanged() {
        IntegrationInfo info = ctx.findSelectedIntegration();
        dsNames.clear();
        selectedDs = 0;
        if (info != null) {
            for (DataSourceInfo ds : info.dataSources) {
                dsNames.add(ds.name);
            }
        }
        clearResults();
        closeEditMode();
    }

    @Override
    public void onTabSelected() {
        focusOnInput = true;
        onIntegrationChanged();
    }

    @Override
    public boolean handleKeyEvent(KeyEvent ke) {
        if (executing.get()) {
            return true;
        }

        // edit mode takes priority
        if (editMode) {
            return handleEditKeyEvent(ke);
        }

        // history popup takes priority
        if (sqlHistory.isPopupVisible()) {
            sqlHistory.handleKeyEvent(ke);
            String selected = sqlHistory.takeSelected();
            if (selected != null) {
                sqlInput.clear();
                sqlInput.insert(selected);
            }
            return true;
        }

        if (ke.isCancel()) {
            if (!focusOnInput && resultRows != null) {
                focusOnInput = true;
                return true;
            }
            return false;
        }

        // F5 to execute query
        if (focusOnInput && ke.code() == KeyCode.F5) {
            executeQuery();
            return true;
        }

        // Tab to toggle focus between input and results
        if (ke.code() == KeyCode.TAB && resultRows != null && !resultRows.isEmpty()) {
            focusOnInput = !focusOnInput;
            return true;
        }

        // Ctrl+E to open history popup
        if (focusOnInput && ke.hasCtrl() && ke.isCharIgnoreCase('e') && !sqlHistory.isEmpty()) {
            sqlHistory.showPopup();
            return true;
        }

        // Enter: newline in input, or open edit in results
        if (ke.isConfirm()) {
            if (focusOnInput) {
                sqlInput.insert('\n');
                return true;
            }
            // results mode: open edit if editable
            if (isEditable()) {
                openEditMode();
            }
            return true;
        }

        if (focusOnInput) {
            // datasource cycling with Ctrl+Left/Right
            if (ke.hasCtrl() && ke.isLeft() && dsNames.size() > 1) {
                selectedDs = (selectedDs - 1 + dsNames.size()) % dsNames.size();
                return true;
            }
            if (ke.hasCtrl() && ke.isRight() && dsNames.size() > 1) {
                selectedDs = (selectedDs + 1) % dsNames.size();
                return true;
            }

            // cursor movement
            if (ke.isUp()) {
                sqlInput.moveCursorUp();
                return true;
            }
            if (ke.isDown()) {
                sqlInput.moveCursorDown();
                return true;
            }
            if (ke.isLeft()) {
                sqlInput.moveCursorLeft();
                return true;
            }
            if (ke.isRight()) {
                sqlInput.moveCursorRight();
                return true;
            }
            if (ke.isHome()) {
                sqlInput.moveCursorToLineStart();
                return true;
            }
            if (ke.isEnd()) {
                sqlInput.moveCursorToLineEnd();
                return true;
            }
            if (ke.isDeleteBackward()) {
                sqlInput.deleteBackward();
                return true;
            }
            if (ke.isDeleteForward()) {
                sqlInput.deleteForward();
                return true;
            }

            // typed character
            if (ke.code() == KeyCode.CHAR) {
                sqlInput.insert(ke.character());
                return true;
            }

            return true;
        }

        // results navigation
        if (ke.isUp()) {
            navigateUp();
            return true;
        }
        if (ke.isDown()) {
            navigateDown();
            return true;
        }

        return true;
    }

    private boolean handleEditKeyEvent(KeyEvent ke) {
        if (ke.isCancel()) {
            closeEditMode();
            return true;
        }

        // F5 to save changes
        if (ke.code() == KeyCode.F5) {
            saveEditedRow();
            return true;
        }

        // navigate fields
        if (ke.isUp()) {
            moveEditField(-1);
            return true;
        }
        if (ke.isDown() || ke.code() == KeyCode.TAB) {
            moveEditField(1);
            return true;
        }

        // edit current field (only non-PK)
        if (editField >= 0 && editField < editInputs.length && !columnIsPk[editField]) {
            FormHelper.handleTextInput(ke, editInputs[editField]);
        }
        return true;
    }

    private void moveEditField(int direction) {
        int count = columnNames.length;
        int next = editField;
        for (int i = 0; i < count; i++) {
            next = (next + direction + count) % count;
            if (!columnIsPk[next]) {
                editField = next;
                return;
            }
        }
    }

    @Override
    public boolean handleEscape() {
        if (editMode) {
            closeEditMode();
            return true;
        }
        if (sqlHistory.isPopupVisible()) {
            sqlHistory.hidePopup();
            return true;
        }
        if (!focusOnInput && resultRows != null) {
            focusOnInput = true;
            return true;
        }
        return false;
    }

    @Override
    public void navigateUp() {
        if (!focusOnInput) {
            tableState.selectPrevious();
        }
    }

    @Override
    public void navigateDown() {
        if (!focusOnInput && resultRows != null) {
            tableState.selectNext(resultRows.size());
        }
    }

    @Override
    public void render(Frame frame, Rect area) {
        if (area.height() < 4) {
            return;
        }

        // layout: datasource bar (1 line) + SQL input (5 lines) + results (rest)
        int inputH = 5;
        int dsBarH = dsNames.size() > 1 ? 1 : 0;
        int topH = dsBarH + inputH;

        List<Rect> parts = Layout.vertical()
                .constraints(Constraint.length(topH), Constraint.min(3))
                .split(area);

        renderInputArea(frame, parts.get(0), dsBarH);
        renderResults(frame, parts.get(1));

        sqlHistory.renderPopup(frame, area, "Query History");

        if (editMode) {
            renderEditPopup(frame, area);
        }
    }

    private void renderInputArea(Frame frame, Rect area, int dsBarH) {
        if (dsBarH > 0 && area.height() > 0) {
            Rect dsBar = new Rect(area.x(), area.y(), area.width(), 1);
            StringBuilder sb = new StringBuilder(" DataSource: ");
            for (int i = 0; i < dsNames.size(); i++) {
                if (i == selectedDs) {
                    sb.append("[").append(dsNames.get(i)).append("]");
                } else {
                    sb.append(" ").append(dsNames.get(i)).append(" ");
                }
                if (i < dsNames.size() - 1) {
                    sb.append("  ");
                }
            }
            Style style = focusOnInput ? Style.EMPTY.fg(Color.CYAN) : Style.EMPTY.fg(Color.DARK_GRAY);
            Paragraph dsLabel = Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled(sb.toString(), style))))
                    .build();
            frame.renderWidget(dsLabel, dsBar);
        }

        Rect inputRect = new Rect(area.x(), area.y() + dsBarH, area.width(), area.height() - dsBarH);

        String title;
        if (executing.get()) {
            title = " Executing... ";
        } else if (errorMessage != null) {
            title = " SQL Query (error) ";
        } else if (updateCount != null) {
            title = String.format(" SQL Query (%d updated, %dms) ", updateCount, elapsed);
        } else if (resultRows != null) {
            title = String.format(" SQL Query (%d row(s), %dms) ", rowCount, elapsed);
        } else {
            title = " SQL Query (F5 to execute) ";
        }
        Style borderStyle = focusOnInput ? Style.EMPTY.fg(Color.CYAN) : Style.EMPTY.fg(Color.DARK_GRAY);
        Block inputBlock = Block.builder()
                .title(Title.from(title))
                .borderType(BorderType.ROUNDED)
                .borderStyle(borderStyle)
                .build();
        Rect inner = inputBlock.inner(inputRect);
        frame.renderWidget(inputBlock, inputRect);

        TextArea textArea = TextArea.builder()
                .cursorStyle(Style.EMPTY.reversed())
                .placeholder("Type SQL query here...")
                .build();
        if (focusOnInput) {
            textArea.renderWithCursor(inner, frame.buffer(), sqlInput, frame);
        } else {
            textArea.render(inner, frame.buffer(), sqlInput);
        }
    }

    private void renderResults(Frame frame, Rect area) {
        if (errorMessage != null) {
            Block errBlock = Block.builder()
                    .title(Title.from(" Error "))
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(Color.RED))
                    .build();
            Rect inner = errBlock.inner(area);
            frame.renderWidget(errBlock, area);
            Paragraph errText = Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled(errorMessage, Style.EMPTY.fg(Color.RED)))))
                    .build();
            frame.renderWidget(errText, inner);
            return;
        }

        if (updateCount != null) {
            Block ucBlock = Block.builder()
                    .title(Title.from(" Result "))
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(Color.GREEN))
                    .build();
            Rect inner = ucBlock.inner(area);
            frame.renderWidget(ucBlock, area);
            String msg = String.format("Update count: %d  (%dms)", updateCount, elapsed);
            Paragraph ucText = Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled(msg, Style.EMPTY.fg(Color.GREEN)))))
                    .build();
            frame.renderWidget(ucText, inner);
            return;
        }

        if (columnNames == null || resultRows == null) {
            Block emptyBlock = Block.builder()
                    .title(Title.from(" Results "))
                    .borderType(BorderType.ROUNDED)
                    .borderStyle(Style.EMPTY.fg(Color.DARK_GRAY))
                    .build();
            Rect inner = emptyBlock.inner(area);
            frame.renderWidget(emptyBlock, area);

            String hint = dsNames.isEmpty()
                    ? "No DataSource available"
                    : "Type a SQL query and press F5 to execute";
            Paragraph hintText = Paragraph.builder()
                    .text(Text.from(Line.from(Span.styled(hint, Style.EMPTY.fg(Color.DARK_GRAY)))))
                    .build();
            frame.renderWidget(hintText, inner);
            return;
        }

        // build result table
        String resultTitle = String.format(" %d row(s)%s  %dms ",
                rowCount, truncated ? " (truncated)" : "", elapsed);
        Style tableBorderStyle = !focusOnInput ? Style.EMPTY.fg(Color.CYAN) : Style.EMPTY.fg(Color.DARK_GRAY);
        Block tableBlock = Block.builder()
                .title(Title.from(resultTitle))
                .borderType(BorderType.ROUNDED)
                .borderStyle(tableBorderStyle)
                .build();

        int[] widths = computeColumnWidths(area.width() - 2);

        Row header = Row.from(buildHeaderCells());
        header.style(Style.EMPTY.fg(Color.YELLOW));

        List<Row> dataRows = new ArrayList<>();
        for (JsonObject row : resultRows) {
            List<Cell> cells = new ArrayList<>();
            for (String col : columnNames) {
                Object val = row.get(col);
                String s = val != null ? String.valueOf(val) : "null";
                Style style = val == null ? Style.EMPTY.fg(Color.DARK_GRAY) : Style.EMPTY.fg(Color.WHITE);
                cells.add(Cell.from(Span.styled(s, style)));
            }
            dataRows.add(Row.from(cells));
        }

        Constraint[] colConstraints = new Constraint[widths.length];
        for (int i = 0; i < widths.length; i++) {
            colConstraints[i] = Constraint.length(widths[i]);
        }

        Table table = Table.builder()
                .header(header)
                .rows(dataRows)
                .widths(colConstraints)
                .block(tableBlock)
                .highlightStyle(Style.EMPTY.bg(Color.DARK_GRAY))
                .build();
        frame.renderStatefulWidget(table, area, tableState);
    }

    private Cell[] buildHeaderCells() {
        Cell[] cells = new Cell[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            String label = columnNames[i];
            if (columnIsPk != null && columnIsPk[i]) {
                label = label + " 🔑";
            }
            cells[i] = Cell.from(Span.styled(label, Style.EMPTY.fg(Color.YELLOW)));
        }
        return cells;
    }

    private int[] computeColumnWidths(int availableWidth) {
        int colCount = columnNames.length;
        int[] widths = new int[colCount];

        for (int i = 0; i < colCount; i++) {
            widths[i] = columnNames[i].length();
            if (columnIsPk != null && columnIsPk[i]) {
                widths[i] += 3;
            }
        }
        if (resultRows != null) {
            for (JsonObject row : resultRows) {
                for (int i = 0; i < colCount; i++) {
                    Object val = row.get(columnNames[i]);
                    int len = val != null ? String.valueOf(val).length() : 4;
                    widths[i] = Math.max(widths[i], len);
                }
            }
        }

        // cap each column to reasonable max
        int maxColWidth = Math.max(10, availableWidth / Math.max(1, colCount));
        for (int i = 0; i < colCount; i++) {
            widths[i] = Math.min(widths[i] + 2, maxColWidth);
        }
        return widths;
    }

    // ---- Edit row popup ----

    private boolean isEditable() {
        return tableName != null && primaryKeys != null && primaryKeys.length > 0
                && resultRows != null && !resultRows.isEmpty();
    }

    private void openEditMode() {
        Integer sel = tableState.selected();
        if (sel == null || sel < 0 || sel >= resultRows.size()) {
            return;
        }
        editRowIndex = sel;
        JsonObject row = resultRows.get(sel);

        editInputs = new TextInputState[columnNames.length];
        editOriginalValues = new String[columnNames.length];
        for (int i = 0; i < columnNames.length; i++) {
            Object val = row.get(columnNames[i]);
            String strVal = val != null ? String.valueOf(val) : "";
            editOriginalValues[i] = strVal;
            editInputs[i] = new TextInputState(strVal);
        }

        // focus on first non-PK field
        editField = 0;
        for (int i = 0; i < columnNames.length; i++) {
            if (!columnIsPk[i]) {
                editField = i;
                break;
            }
        }
        editScrollOffset = 0;
        editUpdateMessage = null;
        editMode = true;
    }

    private void closeEditMode() {
        editMode = false;
        editInputs = null;
        editOriginalValues = null;
        editUpdateMessage = null;
    }

    private void saveEditedRow() {
        if (!editMode || editInputs == null || executing.get()) {
            return;
        }

        // build changed columns
        JsonObject colValues = new JsonObject();
        for (int i = 0; i < columnNames.length; i++) {
            if (columnIsPk[i]) {
                continue;
            }
            String newVal = editInputs[i].text();
            if (!newVal.equals(editOriginalValues[i])) {
                if (newVal.isEmpty()) {
                    colValues.put(columnNames[i], null);
                } else {
                    colValues.put(columnNames[i], newVal);
                }
            }
        }

        if (colValues.isEmpty()) {
            editUpdateMessage = "No changes";
            return;
        }

        // build PK values from the original row
        JsonObject pkValues = new JsonObject();
        JsonObject row = resultRows.get(editRowIndex);
        for (String pk : primaryKeys) {
            Object val = row.get(pk);
            if (val != null) {
                pkValues.put(pk, val);
            } else {
                pkValues.put(pk, null);
            }
        }

        if (!executing.compareAndSet(false, true)) {
            return;
        }

        String dsName = dsNames.isEmpty() ? null : dsNames.get(selectedDs);
        String pkJson = pkValues.toJson();
        String colJson = colValues.toJson();
        String savedSql = lastSql;
        String savedDs = lastDsName;

        ctx.runner.scheduler().execute(() -> {
            try {
                Path outputFile = ctx.getOutputFile(ctx.selectedPid);
                PathUtils.deleteFile(outputFile);

                JsonObject action = new JsonObject();
                action.put("action", "sql-update-row");
                action.put("table", tableName);
                if (dsName != null) {
                    action.put("datasource", dsName);
                }
                action.put("primaryKeyValues", pkJson);
                action.put("columnValues", colJson);

                Path actionFile = ctx.getActionFile(ctx.selectedPid);
                PathUtils.writeTextSafely(action.toJson(), actionFile);

                JsonObject jo = pollJsonResponse(outputFile, 15000);
                PathUtils.deleteFile(outputFile);

                if (jo == null) {
                    if (ctx.runner != null) {
                        ctx.runner.runOnRenderThread(() -> editUpdateMessage = "Timeout");
                    }
                    return;
                }

                String status = jo.getString("status");
                if ("error".equals(status)) {
                    String msg = jo.getString("message");
                    if (ctx.runner != null) {
                        ctx.runner.runOnRenderThread(() -> editUpdateMessage = "Error: " + msg);
                    }
                    return;
                }

                int uc = jo.getIntegerOrDefault("updateCount", 0);
                if (ctx.runner != null) {
                    ctx.runner.runOnRenderThread(() -> {
                        editUpdateMessage = uc + " row(s) updated";
                        closeEditMode();
                    });
                }

                // re-execute the original query to refresh results
                if (savedSql != null) {
                    executeInBackground(ctx.selectedPid, savedSql, savedDs);
                }
            } finally {
                executing.set(false);
            }
        });
    }

    private void renderEditPopup(Frame frame, Rect area) {
        int colCount = columnNames.length;
        int labelW = 0;
        for (String col : columnNames) {
            labelW = Math.max(labelW, col.length());
        }
        labelW += 4;

        int popupW = Math.min(area.width() - 4, Math.max(50, labelW + 30));
        int visibleRows = Math.min(colCount, area.height() - 6);
        int popupH = visibleRows + 4;
        int x = area.left() + Math.max(0, (area.width() - popupW) / 2);
        int y = area.top() + Math.max(1, (area.height() - popupH) / 2);
        Rect popup = new Rect(x, y, popupW, popupH);

        frame.renderWidget(Clear.INSTANCE, popup);

        String title = " Edit Row — " + tableName + " ";
        if (editUpdateMessage != null) {
            title = " " + editUpdateMessage + " ";
        }

        Block block = Block.builder()
                .borderType(BorderType.ROUNDED)
                .title(Title.from(Line.from(Span.styled(title, Style.EMPTY.fg(Color.YELLOW).bold()))))
                .build();
        Rect inner = block.inner(popup);
        frame.renderWidget(block, popup);

        // adjust scroll to keep focused field visible
        if (editField < editScrollOffset) {
            editScrollOffset = editField;
        } else if (editField >= editScrollOffset + visibleRows) {
            editScrollOffset = editField - visibleRows + 1;
        }

        int fieldW = inner.width() - labelW - 1;
        int end = Math.min(editScrollOffset + visibleRows, colCount);
        for (int i = editScrollOffset; i < end; i++) {
            int row = inner.top() + (i - editScrollOffset);
            boolean isPk = columnIsPk[i];
            boolean isFocused = (i == editField);

            // column label
            String label = columnNames[i] + (isPk ? " *" : "  ");
            Style labelStyle;
            if (isPk) {
                labelStyle = Style.EMPTY.fg(Color.DARK_GRAY);
            } else if (isFocused) {
                labelStyle = Style.EMPTY.fg(Color.CYAN).bold();
            } else {
                labelStyle = Style.EMPTY;
            }
            Rect labelArea = new Rect(inner.left(), row, labelW, 1);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(String.format("%" + (labelW - 1) + "s", label) + " ", labelStyle))), labelArea);

            // value field
            Rect valArea = new Rect(inner.left() + labelW, row, fieldW, 1);
            if (isPk) {
                String val = editOriginalValues[i];
                frame.renderWidget(Paragraph.from(Line.from(
                        Span.styled(val.isEmpty() ? "null" : val, Style.EMPTY.fg(Color.DARK_GRAY)))), valArea);
            } else if (isFocused) {
                boolean changed = !editInputs[i].text().equals(editOriginalValues[i]);
                Style cursorStyle = changed ? Style.EMPTY.reversed().fg(Color.GREEN) : Style.EMPTY.reversed();
                TextInput input = TextInput.builder()
                        .cursorStyle(cursorStyle)
                        .build();
                frame.renderStatefulWidget(input, valArea, editInputs[i]);
            } else {
                String val = editInputs[i].text();
                boolean changed = !val.equals(editOriginalValues[i]);
                Style valStyle = changed ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY;
                frame.renderWidget(Paragraph.from(Line.from(
                        Span.styled(val.isEmpty() ? "null" : val, valStyle))), valArea);
            }
        }

        // footer hint inside popup
        int footerY = inner.top() + visibleRows + 1;
        if (footerY < popup.bottom() - 1) {
            Rect footerArea = new Rect(inner.left(), footerY, inner.width(), 1);
            frame.renderWidget(Paragraph.from(Line.from(
                    Span.styled(" F5", Style.EMPTY.fg(Color.YELLOW)),
                    Span.styled("=Save  ", Style.EMPTY.fg(Color.DARK_GRAY)),
                    Span.styled("Esc", Style.EMPTY.fg(Color.YELLOW)),
                    Span.styled("=Cancel  ", Style.EMPTY.fg(Color.DARK_GRAY)),
                    Span.styled("*", Style.EMPTY.fg(Color.DARK_GRAY)),
                    Span.styled("=Primary Key", Style.EMPTY.fg(Color.DARK_GRAY)))), footerArea);
        }
    }

    @Override
    public void renderFooter(List<Span> spans) {
        if (editMode) {
            hint(spans, "F5", "save");
            hint(spans, "Esc", "cancel");
        } else if (focusOnInput) {
            hint(spans, "F5", "execute");
            if (!sqlHistory.isEmpty()) {
                hint(spans, "C-e", "history");
            }
            if (dsNames.size() > 1) {
                hint(spans, "C-←→", "datasource");
            }
            if (resultRows != null && !resultRows.isEmpty()) {
                hint(spans, "Tab", "results");
            }
        } else {
            hint(spans, "Tab", "input");
            hint(spans, "↑↓", "navigate");
            if (isEditable()) {
                hint(spans, "Enter", "edit");
            }
        }
    }

    @Override
    public String getHelpText() {
        return """
                # SQL Query

                Execute SQL queries against DataSource beans registered in the Camel application.

                ## Usage
                - Type a SQL query in the input field and press **F5** to execute
                - Use **Enter** for new lines in the query
                - Use **Up/Down** arrows to move cursor within the query
                - Paste multi-line queries from clipboard
                - Use **Ctrl+E** to open query history (select with Enter, dismiss with Esc)
                - Use **Tab** to toggle focus between input and results table
                - Use **Esc** to return focus to the input field from results
                - Use **Ctrl+Left/Right** to switch between DataSources (when multiple exist)

                ## Inline Editing
                - For simple single-table SELECT queries, press **Enter** on a result row to edit
                - Primary key columns (marked with *) are read-only
                - Changed values are highlighted in green
                - Press **F5** to save changes (executes an UPDATE statement)
                - Press **Esc** to cancel editing
                - The query is automatically re-executed after a successful update

                ## Supported Queries
                - SELECT queries return a result table
                - INSERT, UPDATE, DELETE return an update count
                - Any valid SQL supported by the underlying database

                ## Safety
                - Results are limited to 100 rows by default
                - Query timeout is 30 seconds by default
                - This feature is only available when dev console is enabled (dev profile)
                """;
    }

    @Override
    public JsonObject getTableDataAsJson() {
        JsonObject root = new JsonObject();

        // current input state
        String sql = sqlInput.text().trim();
        if (!sql.isEmpty()) {
            root.put("sql", sql);
        }
        if (!dsNames.isEmpty()) {
            root.put("datasource", dsNames.get(selectedDs));
        }

        if (columnNames != null && resultRows != null) {
            JsonArray cols = new JsonArray();
            for (String col : columnNames) {
                cols.add(col);
            }
            root.put("columns", cols);
            root.put("rows", new JsonArray(resultRows));
            root.put("rowCount", rowCount);
            root.put("truncated", truncated);
            root.put("elapsed", elapsed);
            Integer sel = tableState.selected();
            if (sel != null && sel >= 0 && sel < resultRows.size()) {
                root.put("selectedIndex", sel);
            }
            if (tableName != null) {
                root.put("tableName", tableName);
                root.put("editable", true);
                if (primaryKeys != null) {
                    JsonArray pkArr = new JsonArray();
                    for (String pk : primaryKeys) {
                        pkArr.add(pk);
                    }
                    root.put("primaryKeys", pkArr);
                }
            }
        }
        if (errorMessage != null) {
            root.put("error", errorMessage);
        }
        if (updateCount != null) {
            root.put("updateCount", updateCount);
        }
        if (executing.get()) {
            root.put("executing", true);
        }
        return root;
    }

    private void executeQuery() {
        String sql = sqlInput.text().trim();
        if (sql.isEmpty() || ctx.selectedPid == null || ctx.runner == null) {
            return;
        }
        if (!executing.compareAndSet(false, true)) {
            return;
        }

        clearResults();

        sqlHistory.add(sql);
        String pid = ctx.selectedPid;
        String dsName = dsNames.isEmpty() ? null : dsNames.get(selectedDs);
        lastSql = sql;
        lastDsName = dsName;

        ctx.runner.scheduler().execute(() -> {
            try {
                executeInBackground(pid, sql, dsName);
            } finally {
                executing.set(false);
            }
        });
    }

    private void executeInBackground(String pid, String sql, String dsName) {
        Path outputFile = ctx.getOutputFile(pid);
        PathUtils.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "sql-query");
        root.put("sql", sql);
        if (dsName != null) {
            root.put("datasource", dsName);
        }
        root.put("maxRows", 100);
        root.put("queryTimeout", 30);

        Path actionFile = ctx.getActionFile(pid);
        PathUtils.writeTextSafely(root.toJson(), actionFile);

        JsonObject jo = pollJsonResponse(outputFile, 35000);
        PathUtils.deleteFile(outputFile);

        if (jo == null) {
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> {
                    errorMessage = "Timeout waiting for query response";
                });
            }
            return;
        }

        String status = jo.getString("status");
        if ("error".equals(status)) {
            String msg = jo.getString("message");
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> {
                    errorMessage = msg;
                    elapsed = jo.getLongOrDefault("elapsed", 0);
                });
            }
            return;
        }

        // update count result
        if (jo.containsKey("updateCount")) {
            int uc = jo.getInteger("updateCount");
            long el = jo.getLongOrDefault("elapsed", 0);
            if (ctx.runner != null) {
                ctx.runner.runOnRenderThread(() -> {
                    updateCount = uc;
                    elapsed = el;
                    focusOnInput = true;
                });
            }
            return;
        }

        // SELECT result
        JsonArray columns = jo.getCollection("columns");
        JsonArray rows = jo.getCollection("rows");
        if (columns == null || rows == null) {
            return;
        }

        String[] cols = new String[columns.size()];
        boolean[] isPk = new boolean[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            JsonObject col = (JsonObject) columns.get(i);
            cols[i] = col.getString("name");
            isPk[i] = col.getBooleanOrDefault("primaryKey", false);
        }

        List<JsonObject> parsedRows = new ArrayList<>();
        for (Object rowObj : rows) {
            parsedRows.add((JsonObject) rowObj);
        }

        int rc = jo.getIntegerOrDefault("rowCount", parsedRows.size());
        boolean trunc = jo.getBooleanOrDefault("truncated", false);
        long el = jo.getLongOrDefault("elapsed", 0);

        // editability metadata
        String tblName = jo.getString("tableName");
        String[] pks = null;
        if (tblName != null) {
            JsonArray pkArr = jo.getCollection("primaryKeys");
            if (pkArr != null && !pkArr.isEmpty()) {
                pks = new String[pkArr.size()];
                for (int i = 0; i < pkArr.size(); i++) {
                    pks[i] = String.valueOf(pkArr.get(i));
                }
            }
        }

        String[] finalPks = pks;
        if (ctx.runner != null) {
            ctx.runner.runOnRenderThread(() -> {
                columnNames = cols;
                columnIsPk = isPk;
                resultRows = parsedRows;
                rowCount = rc;
                truncated = trunc;
                elapsed = el;
                tableName = tblName;
                primaryKeys = finalPks;
                tableState.select(0);
                focusOnInput = true;
            });
        }
    }

    private void clearResults() {
        columnNames = null;
        columnIsPk = null;
        resultRows = null;
        rowCount = 0;
        truncated = false;
        elapsed = 0;
        errorMessage = null;
        updateCount = null;
        tableName = null;
        primaryKeys = null;
        tableState.select(0);
    }
}
