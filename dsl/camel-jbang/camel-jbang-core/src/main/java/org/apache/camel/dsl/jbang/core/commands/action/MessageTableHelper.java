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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.common.CamelCommandHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;

/**
 * Helper to output message details (headers, body) in a table like structure with pretty and color supported.
 */
public class MessageTableHelper {

    @FunctionalInterface
    public interface ColorChooser {
        Ansi.Color color(String value);
    }

    private boolean loggingColor;
    private boolean pretty;
    private boolean showExchangeProperties;
    private boolean showExchangeVariables;
    private ColorChooser exchangeIdColorChooser;

    public boolean isLoggingColor() {
        return loggingColor;
    }

    public void setLoggingColor(boolean loggingColor) {
        this.loggingColor = loggingColor;
    }

    public boolean isPretty() {
        return pretty;
    }

    public void setPretty(boolean pretty) {
        this.pretty = pretty;
    }

    public boolean isShowExchangeProperties() {
        return showExchangeProperties;
    }

    public void setShowExchangeProperties(boolean showExchangeProperties) {
        this.showExchangeProperties = showExchangeProperties;
    }

    public boolean isShowExchangeVariables() {
        return showExchangeVariables;
    }

    public void setShowExchangeVariables(boolean showExchangeVariables) {
        this.showExchangeVariables = showExchangeVariables;
    }

    public ColorChooser getExchangeIdColorChooser() {
        return exchangeIdColorChooser;
    }

    public void setExchangeIdColorChooser(ColorChooser exchangeIdColorChooser) {
        this.exchangeIdColorChooser = exchangeIdColorChooser;
    }

    public String getDataAsTable(
            String exchangeId, String exchangePattern,
            JsonObject endpoint, JsonObject root, JsonObject cause) {

        List<TableRow> rows = new ArrayList<>();
        TableRow eRow;
        String tab0 = null;
        String tab1 = null;
        String tab1b = null;
        String tab2 = null;
        String tab3 = null;
        String tab4 = null;
        String tab5 = null;
        String tab6 = null;

        if (endpoint != null) {
            eRow = new TableRow("Endpoint", null, null, endpoint.getString("endpoint"));
            tab0 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(showExchangeProperties || showExchangeVariables ? 12 : 10).with(TableRow::kindAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT).with(TableRow::valueAsString)));
        }

        if (root != null) {
            eRow = new TableRow("Exchange", root.getString("exchangeType"), exchangePattern, exchangeId);
            tab1 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(showExchangeProperties || showExchangeVariables ? 12 : 10).with(TableRow::kindAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT).with(TableRow::typeAsString)));
            tab1b = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.CENTER)
                            .minWidth(18).maxWidth(18).with(TableRow::mepAsKey),
                    new Column().dataAlign(HorizontalAlign.RIGHT)
                            .maxWidth(80).with(TableRow::exchangeIdAsValue)));
            // exchange variables
            JsonArray arr = root.getCollection("exchangeVariables");
            if (arr != null) {
                for (Object o : arr) {
                    JsonObject jo = (JsonObject) o;
                    rows.add(new TableRow("Variable", jo.getString("type"), jo.getString("key"), jo.get("value")));
                }
            }
            // exchange properties
            arr = root.getCollection("exchangeProperties");
            if (arr != null) {
                for (Object o : arr) {
                    JsonObject jo = (JsonObject) o;
                    rows.add(new TableRow("Property", jo.getString("type"), jo.getString("key"), jo.get("value")));
                }
            }
            // internal exchange properties
            arr = root.getCollection("internalExchangeProperties");
            if (arr != null) {
                for (Object o : arr) {
                    JsonObject jo = (JsonObject) o;
                    rows.add(new TableRow("Property", jo.getString("type"), jo.getString("key"), jo.get("value")));
                }
            }
            tab2 = AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(showExchangeProperties || showExchangeVariables ? 12 : 10).with(TableRow::kindAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(25).maxWidth(50, OverflowBehaviour.CLIP_LEFT).with(TableRow::typeAsString),
                    new Column().dataAlign(HorizontalAlign.RIGHT)
                            .minWidth(25).maxWidth(40, OverflowBehaviour.NEWLINE).with(TableRow::keyAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.NEWLINE).with(TableRow::valueAsString)));
            rows.clear();

            // message type before headers
            TableRow msgRow = new TableRow("Message", root.getString("messageType"), null, null);
            tab3 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(msgRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(showExchangeProperties || showExchangeVariables ? 12 : 10).with(TableRow::kindAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT).with(TableRow::typeAsString)));
            arr = root.getCollection("headers");
            if (arr != null) {
                for (Object o : arr) {
                    JsonObject jo = (JsonObject) o;
                    rows.add(new TableRow("Header", jo.getString("type"), jo.getString("key"), jo.get("value")));
                }
            }
            // headers
            tab4 = AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(showExchangeProperties || showExchangeVariables ? 12 : 10).with(TableRow::kindAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(25).maxWidth(50, OverflowBehaviour.CLIP_LEFT).with(TableRow::typeAsString),
                    new Column().dataAlign(HorizontalAlign.RIGHT)
                            .minWidth(25).maxWidth(40, OverflowBehaviour.NEWLINE).with(TableRow::keyAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.NEWLINE).with(TableRow::valueAsString)));

            // body and type
            JsonObject jo = root.getMap("body");
            if (jo != null) {
                TableRow bodyRow = new TableRow(
                        "Body", jo.getString("type"), null, jo.get("value"), jo.getLong("size"), jo.getLong("position"));
                tab5 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(bodyRow), Arrays.asList(
                        new Column().dataAlign(HorizontalAlign.LEFT)
                                .minWidth(showExchangeProperties || showExchangeVariables ? 12 : 10)
                                .with(TableRow::kindAsString),
                        new Column().dataAlign(HorizontalAlign.LEFT).with(TableRow::typeAndLengthAsString)));
                // body value only (span)
                if (bodyRow.value != null) {
                    tab6 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(bodyRow), Arrays.asList(
                            new Column().dataAlign(HorizontalAlign.LEFT).maxWidth(160, OverflowBehaviour.NEWLINE)
                                    .with(b -> pretty ? bodyRow.valueAsStringPretty() : bodyRow.valueAsString())));
                }
            }
        }

        String tab7 = null;
        if (cause != null) {
            eRow = new TableRow("Exception", cause.getString("type"), null, cause.get("message"));
            tab7 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(showExchangeProperties || showExchangeVariables ? 12 : 10)
                            .with(TableRow::kindAsStringRed),
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(40, OverflowBehaviour.CLIP_LEFT).with(TableRow::typeAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.NEWLINE).with(TableRow::valueAsStringRed)));
        }
        // stacktrace only (span)
        String tab8 = null;
        if (cause != null) {
            String value = cause.getString("stackTrace");
            value = Jsoner.unescape(value);
            eRow = new TableRow("Stacktrace", null, null, value);
            tab8 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT).maxWidth(160, OverflowBehaviour.NEWLINE)
                            .with(TableRow::valueAsStringRed)));
        }
        String answer = "";
        if (tab0 != null && !tab0.isEmpty()) {
            answer = answer + tab0 + System.lineSeparator();
        }
        if (tab1 != null && tab1b != null && !tab1.isEmpty()) {
            answer = answer + tab1 + tab1b + System.lineSeparator();
        }
        if (tab2 != null && !tab2.isEmpty()) {
            answer = answer + tab2 + System.lineSeparator();
        }
        if (tab3 != null && !tab3.isEmpty()) {
            answer = answer + tab3 + System.lineSeparator();
        }
        if (tab4 != null && !tab4.isEmpty()) {
            answer = answer + tab4 + System.lineSeparator();
        }
        if (tab5 != null && !tab5.isEmpty()) {
            answer = answer + tab5 + System.lineSeparator();
        }
        if (tab6 != null && !tab6.isEmpty()) {
            answer = answer + tab6 + System.lineSeparator();
        }
        if (tab7 != null && !tab7.isEmpty()) {
            answer = answer + tab7 + System.lineSeparator();
        }
        if (tab8 != null && !tab8.isEmpty()) {
            answer = answer + tab8 + System.lineSeparator();
        }
        return answer;
    }

    private class TableRow {
        String kind;
        String type;
        String key;
        Object value;
        Long position;
        Long size;

        TableRow(String kind, String type, String key, Object value) {
            this(kind, type, key, value, null, null);
        }

        TableRow(String kind, String type, String key, Object value, Long size, Long position) {
            this.kind = kind;
            this.type = type;
            this.key = key;
            this.value = value;
            this.position = position;
            this.size = size;
        }

        String valueAsString() {
            return value != null ? value.toString() : "null";
        }

        String valueAsStringPretty() {
            return CamelCommandHelper.valueAsStringPretty(value, loggingColor);
        }

        String valueAsStringRed() {
            if (value != null) {
                if (loggingColor) {
                    return Ansi.ansi().fgRed().a(value).reset().toString();
                } else {
                    return value.toString();
                }
            }
            return "";
        }

        String keyAsString() {
            if (key == null) {
                return "";
            }
            return key;
        }

        String kindAsString() {
            return kind;
        }

        String kindAsStringRed() {
            if (loggingColor) {
                return Ansi.ansi().fgRed().a(kind).reset().toString();
            } else {
                return kind;
            }
        }

        String typeAsString() {
            String s;
            if (type == null) {
                s = "null";
            } else if (type.startsWith("java.util.concurrent")) {
                s = type.substring(21);
            } else if (type.startsWith("java.lang.") || type.startsWith("java.util.")) {
                s = type.substring(10);
            } else if (type.startsWith("org.apache.camel.support.")) {
                s = type.substring(25);
            } else if (type.equals("org.apache.camel.converter.stream.CachedOutputStream.WrappedInputStream")) {
                s = "WrappedInputStream";
            } else if (type.startsWith("org.apache.camel.converter.stream.")) {
                s = type.substring(34);
            } else if (type.length() > 34) {
                // type must not be too long
                int pos = type.lastIndexOf('.');
                if (pos == -1) {
                    pos = type.length() - 34;
                }
                s = type.substring(pos + 1);
            } else {
                s = type;
            }
            s = "(" + s + ")";
            if (loggingColor) {
                s = Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(s).reset().toString();
            }
            return s;
        }

        String typeAndLengthAsString() {
            String s;
            if (type == null) {
                s = "null";
            } else if (type.startsWith("java.util.concurrent")) {
                s = type.substring(21);
            } else if (type.startsWith("java.lang.") || type.startsWith("java.util.")) {
                s = type.substring(10);
            } else if (type.startsWith("org.apache.camel.support.")) {
                s = type.substring(25);
            } else if (type.equals("org.apache.camel.converter.stream.CachedOutputStream.WrappedInputStream")) {
                s = "WrappedInputStream";
            } else if (type.startsWith("org.apache.camel.converter.stream.")) {
                s = type.substring(34);
            } else {
                s = type;
            }
            s = "(" + s + ")";
            int l = valueLength();
            long sz = size != null ? size : -1;
            long p = position != null ? position : -1;
            StringBuilder sb = new StringBuilder();
            if (sz != -1) {
                sb.append(" size: ").append(sz);
            }
            if (p != -1) {
                sb.append(" pos: ").append(p);
            }
            if (l != -1) {
                sb.append(" bytes: ").append(l);
            }
            if (!sb.isEmpty()) {
                s = s + " (" + sb.toString().trim() + ")";
            }
            if (loggingColor) {
                s = Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(s).reset().toString();
            }
            return s;
        }

        String mepAsKey() {
            String s = key;
            if (loggingColor) {
                s = Ansi.ansi().fgBrightMagenta().a(Ansi.Attribute.INTENSITY_FAINT).a(s).reset().toString();
            }
            return s;
        }

        String exchangeIdAsValue() {
            if (value == null) {
                return "";
            }
            String s = value.toString();
            if (loggingColor) {
                Ansi.Color color = exchangeIdColorChooser != null ? exchangeIdColorChooser.color(s) : Ansi.Color.DEFAULT;
                s = Ansi.ansi().fg(color).a(s).reset().toString();
            }
            return s;
        }

        int valueLength() {
            if (value == null) {
                return -1;
            } else {
                return valueAsString().length();
            }
        }

    }

}
