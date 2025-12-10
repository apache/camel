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

import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.support.LoggerHelper;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import org.jline.keymap.KeyMap;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp;
import picocli.CommandLine;

@CommandLine.Command(name = "history",
                     description = "History of latest completed exchange", sortOptions = false, showDefaultValues = true)
public class CamelHistoryAction extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--it" },
                        description = "Interactive mode for enhanced history information")
    boolean it;

    @CommandLine.Option(names = { "--source" },
                        description = "Prefer to display source filename/code instead of IDs")
    boolean source;

    @CommandLine.Option(names = { "--mask" },
                        description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @CommandLine.Option(names = { "--depth" }, defaultValue = "9",
                        description = "Depth of tracing. 0=Created+Completed. 1=All events on 1st route, 2=All events on 1st+2nd depth, and so on. 9 = all events on every depth.")
    int depth;

    @CommandLine.Option(names = { "--limit-split" },
                        description = "Limit Split to a maximum number of entries to be displayed")
    int limitSplit;

    @CommandLine.Option(names = { "--timestamp" }, defaultValue = "true",
                        description = "Print timestamp.")
    boolean timestamp = true;

    @CommandLine.Option(names = { "--ago" },
                        description = "Use ago instead of yyyy-MM-dd HH:mm:ss in timestamp.")
    boolean ago;

    @CommandLine.Option(names = { "--show-exchange-properties" }, defaultValue = "false",
                        description = "Show exchange properties in debug messages")
    boolean showExchangeProperties;

    @CommandLine.Option(names = { "--show-exchange-variables" }, defaultValue = "true",
                        description = "Show exchange variables in debug messages")
    boolean showExchangeVariables = true;

    @CommandLine.Option(names = { "--show-headers" }, defaultValue = "true",
                        description = "Show message headers in debug messages")
    boolean showHeaders = true;

    @CommandLine.Option(names = { "--show-body" }, defaultValue = "true",
                        description = "Show message body in debug messages")
    boolean showBody = true;

    @CommandLine.Option(names = { "--show-exception" }, defaultValue = "true",
                        description = "Show exception and stacktrace for failed messages")
    boolean showException = true;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    private MessageTableHelper tableHelper;
    private final CamelCatalog camelCatalog = new DefaultCamelCatalog(true);

    public CamelHistoryAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        if (it) {
            // cannot run in watch mode for interactive
            watch = false;
        }
        return super.doCall();
    }

    @Override
    public Integer doWatchCall() throws Exception {
        if (name == null) {
            name = "*";
        }

        List<List<Row>> pids = loadRows();

        if (!pids.isEmpty()) {
            if (it) {
                if (pids.size() > 1) {
                    printer().println("Interactive mode only operate on a single Camel application");
                    return 0;
                }
                return doInteractiveCall(pids.get(0));
            }
            if (watch) {
                clearScreen();
            }
            for (List<Row> rows : pids) {
                Row first = rows.get(0);
                String ago = TimeUtils.printSince(first.timestamp);
                Row last = rows.get(rows.size() - 1);
                String status = last.failed ? "failed" : "success";
                String s = String.format("Message History of last completed (id:%s status:%s ago:%s pid:%d name:%s)",
                        first.exchangeId, status, ago, first.pid, first.name);
                printer().println(s);

                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                        new Column().header("").dataAlign(HorizontalAlign.LEFT)
                                .minWidth(6).maxWidth(6)
                                .with(this::getDirection),
                        new Column().header("ID").dataAlign(HorizontalAlign.LEFT)
                                .minWidth(10).maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(this::getId),
                        new Column().header("PROCESSOR").dataAlign(HorizontalAlign.LEFT)
                                .minWidth(40).maxWidth(55, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(this::getProcessor),
                        new Column().header("ELAPSED").dataAlign(HorizontalAlign.RIGHT)
                                .maxWidth(10, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> "" + r.elapsed),
                        new Column().header("EXCHANGE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                                .maxWidth(12, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(this::getExchangeId),
                        new Column().header("").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(60, OverflowBehaviour.NEWLINE)
                                .with(this::getMessage))));

                JsonObject cause = last.exception;
                if (cause != null) {
                    String st = cause.getString("stackTrace");
                    if (st != null) {
                        st = Jsoner.unescape(st);
                    }
                    if (st != null) {
                        printer().println();
                        printer().println("Stacktrace");
                        printer().println("-".repeat(80));
                        String text = Ansi.ansi().fgRed().a(st).reset().toString();
                        printer().println(text);
                        printer().println();
                    }
                }
                printer().println();
            }
        }

        return 0;
    }

    private Integer doInteractiveCall(List<Row> rows) throws Exception {
        AtomicInteger index = new AtomicInteger();
        AtomicBoolean quit = new AtomicBoolean();

        tableHelper = new MessageTableHelper();
        tableHelper.setPretty(pretty);
        tableHelper.setLoggingColor(loggingColor);
        tableHelper.setShowExchangeProperties(showExchangeProperties);
        tableHelper.setShowExchangeVariables(showExchangeVariables);

        try (InteractiveTerminal t = new InteractiveTerminal()) {
            t.sigint(() -> quit.set(true));
            t.addKeyBinding("quit", KeyMap.ctrl('c'), "q");
            t.addKeyBinding("up", InfoCmp.Capability.key_up);
            t.addKeyBinding("down", InfoCmp.Capability.key_down);
            t.addKeyBinding("refresh", InfoCmp.Capability.key_f5);
            t.start();

            t.clearDisplay();
            t.updateDisplay(interactiveContent(rows, index));
            t.flush();

            do {
                String operation = t.readNextKeyBinding();
                if (operation != null) {
                    if ("quit".equals(operation)) {
                        quit.set(true);
                    } else if ("up".equals(operation)) {
                        if (index.get() > 0) {
                            index.addAndGet(-1);
                        }
                    } else if ("down".equals(operation)) {
                        if (index.get() < rows.size() - 1) {
                            index.addAndGet(1);
                        }
                    } else if ("refresh".equals(operation)) {
                        var reloaded = loadRows();
                        if (reloaded.size() == 1) {
                            rows = reloaded.get(0);
                        }
                        index.set(0);
                    }
                    t.clearDisplay();
                    t.updateDisplay(interactiveContent(rows, index));
                    t.flush();
                }
            } while (!quit.get());
        }

        return 0;
    }

    private List<AttributedString> interactiveContent(List<Row> rows, AtomicInteger index) {
        List<AttributedString> answer = new ArrayList<>();

        Row first = rows.get(0);
        String ago = TimeUtils.printSince(first.timestamp);
        Row last = rows.get(rows.size() - 1);
        String status = last.failed ? "failed" : "success";
        String s = String.format("    Message History of last completed (id:%s status:%s ago:%s pid:%d name:%s)",
                first.exchangeId, status, ago, first.pid, first.name);
        answer.add(new AttributedString(""));
        answer.add(new AttributedString(s));
        answer.add(new AttributedString(""));

        String table = AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("").dataAlign(HorizontalAlign.LEFT)
                        .minWidth(6).maxWidth(6)
                        .with(this::getDirection),
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT)
                        .minWidth(10).maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getId),
                new Column().header("PROCESSOR").dataAlign(HorizontalAlign.LEFT)
                        .minWidth(40).maxWidth(55, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getProcessor),
                new Column().header("ELAPSED").dataAlign(HorizontalAlign.RIGHT)
                        .maxWidth(10, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> "" + r.elapsed),
                new Column().header("EXCHANGE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .maxWidth(12, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getExchangeId),
                new Column().header("").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(60, OverflowBehaviour.NEWLINE)
                        .with(this::getMessage)));

        var normal = AttributedStyle.DEFAULT;
        var select = AttributedStyle.DEFAULT
                .background(AttributedStyle.YELLOW)
                .bold();
        String[] lines = table.split(System.lineSeparator());
        int pos = index.get() + 1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            answer.add(new AttributedString(line, i == pos ? select : normal));
        }
        answer.add(new AttributedString(""));

        // load data for current pos
        Row r = rows.get(index.get());
        String header = rowDetailedHeader(r);
        answer.add(AttributedString.fromAnsi(header));
        answer.add(new AttributedString(""));
        // table with message details
        table = getDataAsTable(r);
        lines = table.split(System.lineSeparator());
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            answer.add(AttributedString.fromAnsi(line));
        }
        answer.add(new AttributedString(""));

        return answer;
    }

    private String getDataAsTable(Row r) {
        return tableHelper.getDataAsTable(r.exchangeId, r.exchangePattern, r.aggregate, r.endpoint, r.endpointService,
                r.message, r.exception);
    }

    private String rowDetailedHeader(Row row) {
        StringBuilder sb = new StringBuilder();

        if (timestamp) {
            String ts;
            if (ago) {
                ts = String.format("%12s", TimeUtils.printSince(row.timestamp) + " ago");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                ts = sdf.format(new Date(row.timestamp));
            }
            if (loggingColor) {
                sb.append(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(ts).reset());
            } else {
                sb.append(ts);
            }
            sb.append("  ");
        }
        // pid
        String p = String.format("%5.5s", row.pid);
        if (loggingColor) {
            sb.append(Ansi.ansi().fgMagenta().a(p).reset());
            sb.append(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(" --- ").reset());
        } else {
            sb.append(p);
            sb.append(" --- ");
        }
        // thread name
        String tn = row.threadName;
        if (tn.length() > 25) {
            tn = tn.substring(tn.length() - 25);
        }
        tn = String.format("[%25.25s]", tn);
        if (loggingColor) {
            sb.append(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(tn).reset());
        } else {
            sb.append(tn);
        }
        sb.append(" ");
        // node ids or source location
        String ids;
        if (source) {
            ids = locationAndLine(row.location, -1);
        } else {
            ids = row.routeId + "/" + getId(row);
        }
        if (ids.length() > 40) {
            ids = ids.substring(ids.length() - 40);
        }
        ids = String.format("%40.40s", ids);
        if (loggingColor) {
            sb.append(Ansi.ansi().fgCyan().a(ids).reset());
        } else {
            sb.append(ids);
        }
        sb.append(" : ");
        // uuid
        String u = String.format("%5.5s", row.uid);
        if (loggingColor) {
            sb.append(Ansi.ansi().fgMagenta().a(u).reset());
        } else {
            sb.append(u);
        }
        sb.append(" - ");
        // status
        sb.append(getStatus(row));
        // elapsed
        String e = getElapsed(row);
        if (e != null) {
            if (loggingColor) {
                sb.append(Ansi.ansi().fgBrightDefault().a(" (" + e + ")").reset());
            } else {
                sb.append("(" + e + ")");
            }
        }
        return sb.toString();
    }

    private String getElapsed(Row r) {
        if (!r.first) {
            return TimeUtils.printDuration(r.elapsed, true);
        }
        return null;
    }

    private String getStatus(Row r) {
        boolean remote = r.endpoint != null && r.endpoint.getBooleanOrDefault("remote", false);

        if (r.first) {
            String s = "Created";
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
            } else {
                return s;
            }
        } else if (r.last) {
            String done = r.exception != null ? "Completed (exception)" : "Completed (success)";
            if (loggingColor) {
                return Ansi.ansi().fg(r.failed ? Ansi.Color.RED : Ansi.Color.GREEN).a(done).reset().toString();
            } else {
                return done;
            }
        }
        if (!r.done) {
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.BLUE).a("Breakpoint").reset().toString();
            } else {
                return "Breakpoint";
            }
        } else if (r.failed) {
            String fail = r.exception != null ? "Exception" : "Failed";
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.RED).a(fail).reset().toString();
            } else {
                return fail;
            }
        } else {
            String s = remote ? "Sent" : "Processed";
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
            } else {
                return s;
            }
        }
    }

    private static String locationAndLine(String loc, int line) {
        // shorten path as there is no much space (there are no scheme as add fake)
        loc = LoggerHelper.sourceNameOnly("file:" + FileUtil.stripPath(loc));
        return line == -1 ? loc : loc + ":" + line;
    }

    private boolean filterDepth(Row r) {
        if (depth >= 9) {
            return true;
        }
        if (depth == 0) {
            // special with only created/completed
            return r.nodeLevel == 1 && (r.first || r.last);
        }
        return r.nodeLevel <= depth;
    }

    private boolean filterSplit(Row r) {
        if (limitSplit <= 0) {
            return true;
        }
        JsonArray arr = r.message.getCollection("exchangeProperties");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonObject jo = (JsonObject) arr.get(i);
                if ("CamelSplitIndex".equals(jo.getString("key"))) {
                    long idx = jo.getLongOrDefault("value", Long.MAX_VALUE);
                    return idx < limitSplit;
                }
            }
        }
        return true;
    }

    private String getId(Row r) {
        String answer;
        if (source && r.location != null) {
            answer = r.location;
        } else {
            if (r.nodeId == null) {
                answer = r.routeId;
            } else {
                answer = r.nodeId;
            }
        }
        return answer;
    }

    private String getProcessor(Row r) {
        if (r.endpoint != null && (r.first || r.last)) {
            return "from[" + r.endpoint.getString("endpoint") + "]";
        } else if (r.nodeLabel != null) {
            return StringHelper.padString(r.nodeLevel, 2) + r.nodeLabel;
        } else {
            return r.nodeId;
        }
    }

    private String getDirection(Row r) {
        if (r.first) {
            return "*-->";
        } else if (r.last) {
            return "*<--";
        } else {
            return "";
        }
    }

    private String getExchangeId(Row r) {
        String id = r.exchangeId.substring(r.exchangeId.length() - 4);
        String cid = r.correlationExchangeId;
        if (cid != null) {
            cid = cid.substring(cid.length() - 4);
            return String.format("%s/%s", id, cid);
        } else {
            return String.format("%s", id);
        }
    }

    private String getMessage(Row r) {
        if (r.failed && !r.last) {
            return "Exception: " + r.exception.getString("message");
        }
        if (r.last) {
            return r.failed ? "Failed" : "Success";
        }
        return r.summary;
    }

    protected List<List<Row>> loadRows() throws Exception {
        List<List<Row>> answer = new ArrayList<>();
        List<Long> pids = findPids(name);
        for (long pid : pids) {
            Path p = getMessageHistoryFile(Long.toString(pid));
            if (Files.exists(p)) {
                String line;
                LineNumberReader reader = new LineNumberReader(Files.newBufferedReader(p));
                try {
                    List<Row> rows = new ArrayList<>();
                    do {
                        line = reader.readLine();
                        List<Row> load = parseTraceLine(pid, line);
                        if (load != null) {
                            rows.addAll(load);
                        }
                    } while (line != null);
                    if (!rows.isEmpty()) {
                        answer.add(rows);
                    }
                } catch (Exception e) {
                    // ignore
                } finally {
                    IOHelper.close(reader);
                }
            }
        }
        return answer;
    }

    private List<Row> parseTraceLine(long pid, String line) {
        JsonObject root = null;
        try {
            root = (JsonObject) Jsoner.deserialize(line);
        } catch (Exception e) {
            // ignore
        }
        if (root != null) {
            List<Row> rows = new ArrayList<>();
            String name = root.getString("name");
            JsonArray arr = root.getCollection("traces");
            if (arr != null) {
                for (Object o : arr) {
                    Row row = new Row();
                    row.pid = pid;
                    row.name = name;
                    JsonObject jo = (JsonObject) o;
                    row.uid = jo.getLong("uid");
                    row.first = jo.getBoolean("first");
                    row.last = jo.getBoolean("last");
                    row.location = jo.getString("location");
                    row.routeId = jo.getString("routeId");
                    row.nodeId = jo.getString("nodeId");
                    row.nodeParentId = jo.getString("nodeParentId");
                    row.nodeParentWhenId = jo.getString("nodeParentWhenId");
                    row.nodeParentWhenLabel = jo.getString("nodeParentWhenLabel");
                    row.nodeShortName = jo.getString("nodeShortName");
                    row.nodeLabel = jo.getString("nodeLabel");
                    if (mask) {
                        row.nodeLabel = URISupport.sanitizeUri(row.nodeLabel);
                    }
                    row.nodeLevel = jo.getIntegerOrDefault("nodeLevel", 0);
                    if ("aggregate".equals(jo.getString("nodeShortName"))) {
                        row.aggregate = new JsonObject();
                        row.aggregate.put("nodeLabel", jo.getString("nodeLabel"));
                    }
                    String uri = jo.getString("endpointUri");
                    if (uri != null) {
                        row.endpoint = new JsonObject();
                        if (mask) {
                            uri = URISupport.sanitizeUri(uri);
                        }
                        row.endpoint.put("endpoint", uri);
                        row.endpoint.put("remote", jo.getBooleanOrDefault("remoteEndpoint", true));
                    }
                    JsonObject es = jo.getMap("endpointService");
                    if (es != null) {
                        row.endpointService = es;
                    }
                    Long ts = jo.getLong("timestamp");
                    if (ts != null) {
                        row.timestamp = ts;
                    }
                    row.elapsed = jo.getLong("elapsed");
                    row.failed = jo.getBoolean("failed");
                    row.done = jo.getBoolean("done");
                    row.threadName = jo.getString("threadName");
                    row.message = jo.getMap("message");
                    row.exception = jo.getMap("exception");
                    row.exchangeId = jo.getString("exchangeId");
                    row.correlationExchangeId = jo.getString("correlationExchangeId");
                    row.exchangePattern = row.message.getString("exchangePattern");
                    // we should exchangeId/pattern elsewhere
                    row.message.remove("exchangeId");
                    row.message.remove("exchangePattern");
                    if (!showExchangeVariables) {
                        row.message.remove("exchangeVariables");
                    }
                    if (!showExchangeProperties) {
                        row.message.remove("exchangeProperties");
                    }
                    if (!showHeaders) {
                        row.message.remove("headers");
                    }
                    if (!showBody) {
                        row.message.remove("body");
                    }
                    if (!showException) {
                        row.exception = null;
                    }
                    List<JsonObject> codeLines = jo.getCollection("code");
                    if (codeLines != null) {
                        for (JsonObject cl : codeLines) {
                            Code code = new Code();
                            code.line = cl.getInteger("line");
                            code.match = cl.getBooleanOrDefault("match", false);
                            code.code = cl.getString("code");
                            row.code.add(code);
                        }
                    }
                    rows.add(row);
                }

                // enhance rows by analysis the history to enrich endpoints and EIPs with summary
                analyseRows(rows);
            }

            List<Row> answer = new ArrayList<>();
            for (Row r : rows) {
                if (filterDepth(r) && filterSplit(r)) {
                    answer.add(r);
                }
            }
            return answer;
        }
        return null;
    }

    private void analyseRows(List<Row> rows) {
        for (int i = 0; i < rows.size() - 1; i++) {
            Row r = rows.get(i);
            Row next = i > 0 && i < rows.size() + 2 ? rows.get(i + 1) : null;

            String uri = r.endpoint != null ? r.endpoint.getString("endpoint") : null;
            Row t = uri != null && r.first ? r : next; // if sending to endpoint then we should find details in the next step as they are response
            if (uri != null && t != null) {
                StringJoiner sj = new StringJoiner(" ");
                var map = extractComponentModel(uri, t);
                // special for file / http
                String fn = map.remove("CamelFileName");
                String fs = map.remove("CamelFileLength");
                String hn = map.remove("CamelHttpResponseCode");
                String hs = map.remove("CamelHttpResponseText");
                if (fn != null && fs != null) {
                    sj.add("File: " + fn + " (" + fs + " bytes)");
                } else if (fn != null) {
                    sj.add("File: " + fn);
                } else if (hn != null && hs != null) {
                    sj.add(hn + "=" + hs);
                }
                map.forEach((k, v) -> {
                    String line = k + "=" + v;
                    sj.add(line);
                });
                r.summary = sj.toString();
            } else if ("filter".equals(r.nodeShortName)) {
                if (next != null && r.nodeId != null && r.nodeId.equals(next.nodeParentId)) {
                    r.summary = "Filter: true";
                } else {
                    r.summary = "Filter: false";
                }
            } else if ("choice".equals(r.nodeShortName)) {
                if (next != null && r.nodeId != null && r.nodeId.equals(next.nodeParentId)) {
                    if (next.nodeParentWhenLabel != null) {
                        r.summary = "Choice: " + next.nodeParentWhenLabel;
                    }
                }
            } else if ("split".equals(r.nodeShortName)) {
                if (next != null && r.nodeId != null && r.nodeId.equals(next.nodeParentId)) {
                    var map = extractEipModel("split", next);
                    String sv = map.remove("CamelSplitSize");
                    r.summary = "Split (" + sv + ")";
                }
            } else if ("aggregate".equals(r.nodeShortName)) {
                if (r.first) {
                    var map = extractEipModel("aggregate", r);
                    String ak = map.remove("CamelAggregatedCorrelationKey");
                    String as = map.remove("CamelAggregatedSize");
                    r.summary = "Aggregate (key:" + ak + " size:" + as + ")";
                }
            }
        }
        for (int i = 1; i < rows.size() - 1; i++) {
            Row cur = rows.get(i);

            cur.history = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                Row r = rows.get(j);
                History h = new History();
                h.index = j;
                h.nodeId = r.nodeId;
                h.routeId = r.routeId;
                h.nodeShortName = r.nodeShortName;
                h.nodeLabel = r.nodeLabel;
                h.location = r.location;
                h.elapsed = r.elapsed;
                h.level = r.nodeLevel;
                if (r.code != null) {
                    for (var c : r.code) {
                        if (c.match) {
                            h.code = c.code;
                            h.line = c.line;
                        }
                    }
                }
                cur.history.add(h);
            }
        }
    }

    private Map<String, String> extractComponentModel(String uri, Row r) {
        Map<String, String> answer = new LinkedHashMap<>();

        String scheme = StringHelper.before(uri, ":");
        if (scheme != null) {
            ComponentModel cm = camelCatalog.componentModel(scheme);
            if (cm != null) {
                for (var eh : cm.getEndpointHeaders()) {
                    if (eh.isImportant()) {
                        JsonArray arr = r.message.getCollection("headers");
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                JsonObject jo = (JsonObject) arr.get(i);
                                String key = jo.getString("key");
                                if (key.equals(eh.getName())) {
                                    answer.put(key, jo.getString("value"));
                                }
                            }
                        }
                    }
                }
            }
        }

        return answer;
    }

    private Map<String, String> extractEipModel(String eip, Row r) {
        Map<String, String> answer = new LinkedHashMap<>();

        EipModel em = camelCatalog.eipModel(eip);
        if (em != null) {
            for (var ep : em.getExchangeProperties()) {
                if (ep.isImportant()) {
                    JsonArray arr = r.message.getCollection("exchangeProperties");
                    if (arr != null) {
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject jo = (JsonObject) arr.get(i);
                            String key = jo.getString("key");
                            if (key.equals(ep.getName())) {
                                answer.put(key, jo.getString("value"));
                            }
                        }
                    }
                    arr = r.message.getCollection("headers");
                    if (arr != null) {
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject jo = (JsonObject) arr.get(i);
                            String key = jo.getString("key");
                            if (key.equals(ep.getName())) {
                                answer.put(key, jo.getString("value"));
                            }
                        }
                    }
                }
            }
        }
        return answer;
    }

    private static class Row {
        long pid;
        String name;
        boolean first;
        boolean last;
        long uid;
        String exchangeId;
        String correlationExchangeId;
        String exchangePattern;
        String threadName;
        String location;
        String routeId;
        String nodeId;
        String nodeParentId;
        String nodeParentWhenId;
        String nodeParentWhenLabel;
        String nodeShortName;
        String nodeLabel;
        int nodeLevel;
        long timestamp;
        long elapsed;
        boolean done;
        boolean failed;
        JsonObject aggregate;
        JsonObject endpoint;
        JsonObject endpointService;
        JsonObject message;
        JsonObject exception;
        String summary;
        List<Code> code = new ArrayList<>();
        List<History> history = new ArrayList<>();
    }

    private static class History {
        int index;
        String routeId;
        String nodeId;
        String nodeShortName;
        String nodeLabel;
        int level;
        long elapsed;
        String location;
        int line;
        String code;
    }

    private static class Code {
        int line;
        String code;
        boolean match;
    }

    private static class Panel {
        String code = "";
        String history = "";
        int codeLength;
        int historyLength;

        static Panel withCode(String code) {
            return withCode(code, code.length());
        }

        static Panel withCode(String code, int length) {
            Panel p = new Panel();
            p.code = code;
            p.codeLength = length;
            return p;
        }

        Panel andHistory(String history) {
            return andHistory(history, history.length());
        }

        Panel andHistory(String history, int length) {
            this.history = history;
            this.historyLength = length;
            return this;
        }

    }

}
