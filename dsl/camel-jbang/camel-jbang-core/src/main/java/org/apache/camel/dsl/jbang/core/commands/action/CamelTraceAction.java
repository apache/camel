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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.catalog.impl.TimePatternConverter;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.JSonHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.XmlHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

@CommandLine.Command(name = "trace",
                     description = "Tail message traces from running Camel integrations")
public class CamelTraceAction extends ActionBaseCommand {

    private static final int NAME_MAX_WIDTH = 25;
    private static final int NAME_MIN_WIDTH = 10;

    public static class PrefixCompletionCandidates implements Iterable<String> {

        public PrefixCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("auto", "true", "false").iterator();
        }
    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration. (default selects all)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--timestamp" }, defaultValue = "true",
                        description = "Print timestamp.")
    boolean timestamp = true;

    @CommandLine.Option(names = { "--ago" },
                        description = "Use ago instead of yyyy-MM-dd HH:mm:ss in timestamp.")
    boolean ago;

    @CommandLine.Option(names = { "--follow" }, defaultValue = "true",
                        description = "Keep following and outputting new traces (use ctrl + c to exit).")
    boolean follow = true;

    @CommandLine.Option(names = { "--prefix" }, defaultValue = "auto", completionCandidates = PrefixCompletionCandidates.class,
                        description = "Print prefix with running Camel integration name. auto=only prefix when running multiple integrations. true=always prefix. false=prefix off.")
    String prefix = "auto";

    @CommandLine.Option(names = { "--source" },
                        description = "Prefer to display source filename/code instead of IDs")
    boolean source;

    @CommandLine.Option(names = { "--depth" }, defaultValue = "9",
                        description = "Depth of tracing. 0=Created+Completed. 1=All events on 1st route, 2=All events on 1st+2nd depth, and so on. 9 = all events on every depth.")
    int depth;

    @CommandLine.Option(names = { "--tail" }, defaultValue = "-1",
                        description = "The number of traces from the end of the trace to show. Use -1 to read from the beginning. Use 0 to read only new lines. Defaults to showing all traces from beginning.")
    int tail = -1;

    @CommandLine.Option(names = { "--since" },
                        description = "Return traces newer than a relative duration like 5s, 2m, or 1h. The value is in seconds if no unit specified.")
    String since;

    @CommandLine.Option(names = { "--find" },
                        description = "Find and highlight matching text (ignore case).", arity = "0..*")
    String[] find;

    @CommandLine.Option(names = { "--grep" },
                        description = "Filter traces to only output trace matching text (ignore case).", arity = "0..*")
    String[] grep;

    @CommandLine.Option(names = { "--show-exchange-properties" }, defaultValue = "false",
                        description = "Show exchange properties in traced messages")
    boolean showExchangeProperties;

    @CommandLine.Option(names = { "--show-headers" }, defaultValue = "true",
                        description = "Show message headers in traced messages")
    boolean showHeaders = true;

    @CommandLine.Option(names = { "--show-body" }, defaultValue = "true",
                        description = "Show message body in traced messages")
    boolean showBody = true;

    @CommandLine.Option(names = { "--show-exception" }, defaultValue = "true",
                        description = "Show exception and stacktrace for failed messages")
    boolean showException = true;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--compact" }, defaultValue = "true",
                        description = "Compact output (no empty line separating traced messages)")
    boolean compact = true;

    @CommandLine.Option(names = { "--latest" },
                        description = "Only output traces from the latest (follow if necessary until complete and exit)")
    boolean latest;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    String findAnsi;

    private int nameMaxWidth;
    private boolean prefixShown;

    private final Map<String, Ansi.Color> nameColors = new HashMap<>();
    private final Map<String, Ansi.Color> exchangeIdColors = new HashMap<>();
    private int exchangeIdColorsIndex = 1;

    public CamelTraceAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Map<Long, Pid> pids = new LinkedHashMap<>();

        if (latest) {
            // turn of tail/since when in latest mode
            tail = 0;
            since = null;
        }

        // find new pids
        updatePids(pids);
        if (!pids.isEmpty()) {
            // read existing trace files (skip by tail/since)
            if (find != null) {
                findAnsi = Ansi.ansi().fg(Ansi.Color.BLACK).bg(Ansi.Color.YELLOW).a("$0").reset().toString();
                for (int i = 0; i < find.length; i++) {
                    String f = find[i];
                    f = Pattern.quote(f);
                    find[i] = f;
                }
            }
            if (grep != null) {
                findAnsi = Ansi.ansi().fg(Ansi.Color.BLACK).bg(Ansi.Color.YELLOW).a("$0").reset().toString();
                for (int i = 0; i < grep.length; i++) {
                    String f = grep[i];
                    f = Pattern.quote(f);
                    grep[i] = f;
                }
            }
            Date limit = null;
            if (since != null) {
                long millis;
                if (StringHelper.isDigit(since)) {
                    // is in seconds by default
                    millis = TimePatternConverter.toMilliSeconds(since) * 1000;
                } else {
                    millis = TimePatternConverter.toMilliSeconds(since);
                }
                limit = new Date(System.currentTimeMillis() - millis);
            }
            // dump existing traces
            if (tail != 0) {
                tailTraceFiles(pids, tail);
                dumpTraceFiles(pids, tail, limit);
            } else if (latest) {
                // position until latest start and let follow dump
                positionTraceLatest(pids);
            }
        }

        if (follow) {
            boolean waitMessage = true;
            StopWatch watch = new StopWatch();
            boolean more = true;
            do {
                if (pids.isEmpty()) {
                    if (waitMessage) {
                        System.out.println("Waiting for traces ...");
                        waitMessage = false;
                    }
                    Thread.sleep(500);
                    updatePids(pids);
                } else {
                    waitMessage = true;
                    if (watch.taken() > 500) {
                        // check for new traces
                        updatePids(pids);
                        watch.restart();
                    }
                    int lines = readTraceFiles(pids);
                    if (lines > 0) {
                        more = dumpTraceFiles(pids, 0, null);
                    } else if (lines == 0) {
                        Thread.sleep(100);
                    } else {
                        break;
                    }
                }
            } while (more);
        }

        return 0;
    }

    private void positionTraceLatest(Map<Long, Pid> pids) throws Exception {
        for (Pid pid : pids.values()) {
            File file = getTraceFile(pid.pid);
            boolean marked = false;
            if (file.exists()) {
                pid.reader = new LineNumberReader(new FileReader(file));
                String line;
                do {
                    line = pid.reader.readLine();
                    if (line != null) {
                        List<Row> rows = parseTraceLine(pid, line);
                        for (Row r : rows) {
                            if (r.first) {
                                pid.reader.mark(8192);
                                marked = true;
                            }
                        }
                    }
                } while (line != null);
            }
            if (marked) {
                pid.reader.reset();
            }
        }
    }

    private void tailTraceFiles(Map<Long, Pid> pids, int tail) throws Exception {
        for (Pid pid : pids.values()) {
            File file = getTraceFile(pid.pid);
            if (file.exists()) {
                pid.reader = new LineNumberReader(new FileReader(file));
                String line;
                if (tail <= 0) {
                    pid.fifo = new ArrayDeque<>();
                } else {
                    pid.fifo = new ArrayBlockingQueue<>(tail);
                }
                do {
                    line = pid.reader.readLine();
                    if (line != null) {
                        while (!pid.fifo.offer(line)) {
                            pid.fifo.poll();
                        }
                    }
                } while (line != null);
            }
        }
    }

    private void updatePids(Map<Long, Pid> rows) {
        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        Pid row = new Pid();
                        row.pid = "" + ph.pid();
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        int len = row.name.length();
                        if (len < NAME_MIN_WIDTH) {
                            len = NAME_MIN_WIDTH;
                        }
                        if (len > NAME_MAX_WIDTH) {
                            len = NAME_MAX_WIDTH;
                        }
                        if (len > nameMaxWidth) {
                            nameMaxWidth = len;
                        }
                        if (!rows.containsKey(ph.pid())) {
                            rows.put(ph.pid(), row);
                        }
                    }
                });

        // remove pids that are no long active from the rows
        Set<Long> remove = new HashSet<>();
        for (long pid : rows.keySet()) {
            if (!pids.contains(pid)) {
                remove.add(pid);
            }
        }
        for (long pid : remove) {
            rows.remove(pid);
        }
    }

    private int readTraceFiles(Map<Long, Pid> pids) throws Exception {
        int lines = 0;

        for (Pid pid : pids.values()) {
            if (pid.reader == null) {
                File file = getTraceFile(pid.pid);
                if (file.exists()) {
                    pid.reader = new LineNumberReader(new FileReader(file));
                    if (tail == 0) {
                        // only read new lines so forward to end of reader
                        long size = file.length();
                        pid.reader.skip(size);
                    }
                }
            }
            if (pid.reader != null) {
                String line;
                do {
                    try {
                        line = pid.reader.readLine();
                        if (line != null) {
                            lines++;
                            // switch fifo to be unlimited as we use it for new traces
                            if (pid.fifo == null || pid.fifo instanceof ArrayBlockingQueue) {
                                pid.fifo = new ArrayDeque<>();
                            }
                            pid.fifo.offer(line);
                        }
                    } catch (IOException e) {
                        // ignore
                        line = null;
                    }
                } while (line != null);
            }
        }

        return lines;
    }

    private List<Row> parseTraceLine(Pid pid, String line) {
        JsonObject root = null;
        try {
            root = (JsonObject) Jsoner.deserialize(line);
        } catch (Exception e) {
            // ignore
        }
        if (root != null) {
            List<Row> rows = new ArrayList<>();
            JsonArray arr = root.getCollection("traces");
            if (arr != null) {
                for (Object o : arr) {
                    Row row = new Row(pid);
                    row.pid = pid.pid;
                    row.name = pid.name;
                    JsonObject jo = (JsonObject) o;
                    row.uid = jo.getLong("uid");
                    row.first = jo.getBoolean("first");
                    row.last = jo.getBoolean("last");
                    row.location = jo.getString("location");
                    row.routeId = jo.getString("routeId");
                    row.nodeId = jo.getString("nodeId");
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
                    row.exchangeId = row.message.getString("exchangeId");
                    row.exchangePattern = row.message.getString("exchangePattern");
                    // we should exchangeId/pattern elsewhere
                    row.message.remove("exchangeId");
                    row.message.remove("exchangePattern");
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
                    rows.add(row);
                }
            }
            return rows;
        }
        return null;
    }

    private boolean dumpTraceFiles(Map<Long, Pid> pids, int tail, Date limit) {
        Set<String> names = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (Pid pid : pids.values()) {
            Queue<String> queue = pid.fifo;
            if (queue != null) {
                for (String l : queue) {
                    names.add(pid.name);
                    List<Row> parsed = parseTraceLine(pid, l);
                    if (parsed != null && !parsed.isEmpty()) {
                        rows.addAll(parsed);
                    }
                }
                pid.fifo.clear();
            }
        }

        // only sort if there are multiple Camels running
        if (names.size() > 1) {
            // sort lines
            final Map<String, Long> lastTimestamp = new HashMap<>();
            rows.sort((r1, r2) -> {
                long t1 = r1.timestamp;
                long t2 = r2.timestamp;
                if (t1 == 0) {
                    t1 = lastTimestamp.get(r1.name);
                }
                if (t1 == 0) {
                    t1 = lastTimestamp.get(r2.name);
                }
                if (t1 == 0 && t2 == 0) {
                    return 0;
                } else if (t1 == 0) {
                    return -1;
                } else if (t2 == 0) {
                    return 1;
                }
                lastTimestamp.put(r1.name, t1);
                lastTimestamp.put(r2.name, t2);
                return Long.compare(t1, t2);
            });
        }
        if (tail > 0) {
            // cut according to tail
            int pos = rows.size() - tail;
            if (pos > 0) {
                rows = rows.subList(pos, rows.size());
            }
        }

        int doneTraces = 0;
        for (Row r : rows) {
            printTrace(r.name, pids.size(), r, limit);
            if (r.done) {
                doneTraces++;
            }
        }

        if (latest) {
            // in latest mode we continue until we have first done
            return doneTraces == 0;
        } else {
            return true;
        }
    }

    private boolean isValidGrep(String line) {
        if (grep == null) {
            return true;
        }
        for (String g : grep) {
            boolean m = Pattern.compile("(?i)" + g).matcher(line).find();
            if (m) {
                return true;
            }
        }
        return false;
    }

    private boolean isValidSince(Date limit, long timestamp) {
        if (limit == null || timestamp == 0) {
            return true;
        }
        Date row = new Date(timestamp);
        return row.compareTo(limit) >= 0;
    }

    protected void printTrace(String name, int pids, Row row, Date limit) {
        if (!prefixShown) {
            // compute whether to show prefix or not
            if ("false".equals(prefix) || "auto".equals(prefix) && pids <= 1) {
                name = null;
            }
        }
        prefixShown = name != null;

        if (row.first) {
            row.parent.depth++;
        } else if (row.last) {
            row.parent.depth--;
        }

        String data = getDataAsTable(row);
        boolean valid = filterDepth(row) && isValidSince(limit, row.timestamp) && isValidGrep(data);
        if (!valid) {
            return;
        }

        String nameWithPrefix = null;
        if (name != null) {
            if (loggingColor) {
                Ansi.Color color = nameColors.get(name);
                if (color == null) {
                    // grab a new color
                    int idx = (nameColors.size() % 6) + 1;
                    color = Ansi.Color.values()[idx];
                    nameColors.put(name, color);
                }
                String n = String.format("%-" + nameMaxWidth + "s", name);
                nameWithPrefix = Ansi.ansi().fg(color).a(n).a("| ").reset().toString();
            } else {
                nameWithPrefix = String.format("%-" + nameMaxWidth + "s", name) + "| ";
            }
            System.out.print(nameWithPrefix);
        }
        if (timestamp) {
            String ts;
            if (ago) {
                ts = String.format("%12s", TimeUtils.printSince(row.timestamp) + " ago");
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
                ts = sdf.format(new Date(row.timestamp));
            }
            if (loggingColor) {
                AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(ts).reset());
            } else {
                System.out.print(ts);
            }
            System.out.print("  ");
        }
        // pid
        String p = String.format("%5.5s", row.pid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(p).reset());
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(" --- ").reset());
        } else {
            System.out.print(p);
            System.out.print(" --- ");
        }
        // thread name
        String tn = row.threadName;
        if (tn.length() > 25) {
            tn = tn.substring(tn.length() - 25);
        }
        tn = String.format("[%25.25s]", tn);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(Ansi.Attribute.INTENSITY_FAINT).a(tn).reset());
        } else {
            System.out.print(tn);
        }
        System.out.print(" ");
        // node ids or source location
        String ids;
        if (source) {
            ids = row.location;
        } else {
            ids = row.routeId + "/" + getId(row);
        }
        if (ids.length() > 40) {
            ids = ids.substring(ids.length() - 40);
        }
        ids = String.format("%40.40s", ids);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgCyan().a(ids).reset());
        } else {
            System.out.print(ids);
        }
        System.out.print(" : ");
        // uuid
        String u = String.format("%5.5s", row.uid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(u).reset());
        } else {
            System.out.print(u);
        }
        System.out.print(" - ");
        // status
        System.out.print(getStatus(row));
        // elapsed
        String e = getElapsed(row);
        if (e != null) {
            if (loggingColor) {
                AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(" (" + e + ")").reset());
            } else {
                System.out.print("(" + e + ")");
            }
        }
        // trace message
        String[] lines = data.split(System.lineSeparator());
        if (lines.length > 0) {
            System.out.println();
            for (String line : lines) {
                if (find != null) {
                    for (String f : find) {
                        line = line.replaceAll("(?i)" + f, findAnsi);
                    }
                }
                if (grep != null) {
                    for (String g : grep) {
                        line = line.replaceAll("(?i)" + g, findAnsi);
                    }
                }
                if (nameWithPrefix != null) {
                    System.out.print(nameWithPrefix);
                }
                System.out.print(" ");
                System.out.println(line);
            }
            if (!compact) {
                System.out.println(nameWithPrefix);
            }
        }

        if (row.parent.depth <= 0 && row.last) {
            exchangeIdColors.remove(row.exchangeId);
        }
    }

    private boolean filterDepth(Row row) {
        if (depth >= 9) {
            return true;
        }
        if (depth == 0) {
            // special with only created/completed
            return row.parent.depth == 1 && row.first || row.parent.depth == 0 && row.last;
        }
        return row.parent.depth <= depth;
    }

    private String getDataAsTable(Row r) {
        List<TableRow> rows = new ArrayList<>();

        TableRow eRow = new TableRow("Exchange", r.message.getString("exchangeType"), r.exchangePattern, r.exchangeId);
        String tab1 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .minWidth(showExchangeProperties ? 12 : 10).with(TableRow::kindAsString),
                new Column().dataAlign(HorizontalAlign.LEFT).with(TableRow::typeAsString)));
        String tab1b = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                new Column().dataAlign(HorizontalAlign.CENTER)
                        .minWidth(18).maxWidth(18).with(TableRow::mepAsKey),
                new Column().dataAlign(HorizontalAlign.RIGHT)
                        .maxWidth(80).with(TableRow::exchangeIdAsValue)));
        // exchange properties
        JsonArray arr = r.message.getCollection("exchangeProperties");
        if (arr != null) {
            for (Object o : arr) {
                JsonObject jo = (JsonObject) o;
                rows.add(new TableRow("Property", jo.getString("type"), jo.getString("key"), jo.get("value")));
            }
        }
        // internal exchange properties
        arr = r.message.getCollection("internalExchangeProperties");
        if (arr != null) {
            for (Object o : arr) {
                JsonObject jo = (JsonObject) o;
                rows.add(new TableRow("Property", jo.getString("type"), jo.getString("key"), jo.get("value")));
            }
        }
        String tab2 = AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .minWidth(showExchangeProperties ? 12 : 10).with(TableRow::kindAsString),
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .minWidth(25).maxWidth(50, OverflowBehaviour.CLIP_LEFT).with(TableRow::typeAsString),
                new Column().dataAlign(HorizontalAlign.RIGHT)
                        .minWidth(25).maxWidth(40, OverflowBehaviour.NEWLINE).with(TableRow::keyAsString),
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(80, OverflowBehaviour.NEWLINE).with(TableRow::valueAsString)));
        rows.clear();

        // message type before headers
        TableRow msgRow = new TableRow("Message", r.message.getString("messageType"), null, null);
        String tab3 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(msgRow), Arrays.asList(
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .minWidth(showExchangeProperties ? 12 : 10).with(TableRow::kindAsString),
                new Column().dataAlign(HorizontalAlign.LEFT).with(TableRow::typeAsString)));
        arr = r.message.getCollection("headers");
        if (arr != null) {
            for (Object o : arr) {
                JsonObject jo = (JsonObject) o;
                rows.add(new TableRow("Header", jo.getString("type"), jo.getString("key"), jo.get("value")));
            }
        }
        // headers
        String tab4 = AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .minWidth(showExchangeProperties ? 12 : 10).with(TableRow::kindAsString),
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .minWidth(25).maxWidth(50, OverflowBehaviour.CLIP_LEFT).with(TableRow::typeAsString),
                new Column().dataAlign(HorizontalAlign.RIGHT)
                        .minWidth(25).maxWidth(40, OverflowBehaviour.NEWLINE).with(TableRow::keyAsString),
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(80, OverflowBehaviour.NEWLINE).with(TableRow::valueAsString)));

        // body and type
        JsonObject jo = r.message.getMap("body");
        TableRow bodyRow = new TableRow("Body", jo.getString("type"), null, jo.get("value"));
        String tab5 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(bodyRow), Arrays.asList(
                new Column().dataAlign(HorizontalAlign.LEFT)
                        .minWidth(showExchangeProperties ? 12 : 10).with(TableRow::kindAsString),
                new Column().dataAlign(HorizontalAlign.LEFT).with(TableRow::typeAndLengthAsString)));
        // body value only (span)
        String tab6 = null;
        if (bodyRow.value != null) {
            tab6 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(bodyRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT).maxWidth(160, OverflowBehaviour.NEWLINE)
                            .with(b -> pretty ? bodyRow.valueAsStringPretty() : bodyRow.valueAsString())));
        }
        String tab7 = null;
        jo = r.exception;
        if (jo != null) {
            eRow = new TableRow("Exception", jo.getString("type"), null, jo.get("message"));
            tab7 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .minWidth(showExchangeProperties ? 12 : 10)
                            .with(TableRow::kindAsStringRed),
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(40, OverflowBehaviour.CLIP_LEFT).with(TableRow::typeAsString),
                    new Column().dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(80, OverflowBehaviour.NEWLINE).with(TableRow::valueAsStringRed)));
        }
        // stacktrace only (span)
        String tab8 = null;
        if (jo != null) {
            eRow = new TableRow("Stacktrace", null, null, jo.get("stackTrace"));
            tab8 = AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(eRow), Arrays.asList(
                    new Column().dataAlign(HorizontalAlign.LEFT).maxWidth(160, OverflowBehaviour.NEWLINE)
                            .with(TableRow::valueAsStringRed)));
        }
        String answer = "";
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

    private String getElapsed(Row r) {
        if (!r.first) {
            return TimeUtils.printDuration(r.elapsed, true);
        }
        return null;
    }

    private String getStatus(Row r) {
        if (r.first) {
            String s = r.parent.depth == 1 ? "Created" : "Routing to " + r.routeId;
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a(s).reset().toString();
            } else {
                return "Input";
            }
        } else if (r.last) {
            String done = r.exception != null ? "Completed (exception)" : "Completed (success)";
            String s = r.parent.depth == 0 ? done : "Returning from " + r.routeId;
            if (loggingColor) {
                return Ansi.ansi().fg(r.failed ? Ansi.Color.RED : Ansi.Color.GREEN).a(s).reset().toString();
            } else {
                return s;
            }
        }
        if (!r.done) {
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.BLUE).a("Processing").reset().toString();
            } else {
                return "Processing";
            }
        } else if (r.failed) {
            String fail = r.exception != null ? "Exception" : "Failed";
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.RED).a(fail).reset().toString();
            } else {
                return fail;
            }
        } else {
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a("Processed").reset().toString();
            } else {
                return "Processed";
            }
        }
    }

    private String getId(Row r) {
        if (r.first) {
            return "*-->";
        } else if (r.last) {
            return "*<--";
        } else {
            return "" + r.nodeId;
        }
    }

    private static class Pid {
        String pid;
        String name;
        Queue<String> fifo;
        int depth;
        LineNumberReader reader;
    }

    private static class Row {
        Pid parent;
        String pid;
        String name;
        boolean first;
        boolean last;
        long uid;
        String exchangeId;
        String exchangePattern;
        String threadName;
        String location;
        String routeId;
        String nodeId;
        long timestamp;
        long elapsed;
        boolean done;
        boolean failed;
        JsonObject message;
        JsonObject exception;

        Row(Pid parent) {
            this.parent = parent;
        }

    }

    private class TableRow {
        String kind;
        String type;
        String key;
        Object value;

        TableRow(String kind, String type, String key, Object value) {
            this.kind = kind;
            this.type = type;
            this.key = key;
            this.value = value;
        }

        String valueAsString() {
            return value != null ? value.toString() : "null";
        }

        String valueAsStringPretty() {
            if (value == null) {
                return "null";
            }
            boolean json = false;
            String s = value.toString();
            if (!s.isEmpty()) {
                try {
                    s = Jsoner.unescape(s);
                    if (loggingColor) {
                        s = JSonHelper.colorPrint(s, 2, true);
                    } else {
                        s = JSonHelper.prettyPrint(s, 2);
                    }
                    if (s != null && !s.isEmpty()) {
                        json = true;
                    }
                } catch (Throwable e) {
                    // ignore as not json
                }
                if (s == null || s.isEmpty()) {
                    s = value.toString();
                }
                if (!json) {
                    // try with xml
                    try {
                        s = Jsoner.unescape(s);
                        if (loggingColor) {
                            s = XmlHelper.colorPrint(s, 2, true);
                        } else {
                            s = XmlHelper.prettyPrint(s, 2);
                        }
                    } catch (Throwable e) {
                        // ignore as not xml
                    }
                }
                if (s == null || s.isEmpty()) {
                    s = value.toString();
                }
            }
            if (s == null) {
                return "null";
            }
            return s;
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
            } else {
                s = type;
            }
            s = "(" + s + ")";
            int l = valueLength();
            if (l != -1) {
                s = s + " (length: " + l + ")";
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
            String s = value.toString();
            if (loggingColor) {
                Ansi.Color color = exchangeIdColors.get(s);
                if (color == null) {
                    // grab a new color
                    exchangeIdColorsIndex++;
                    if (exchangeIdColorsIndex > 6) {
                        exchangeIdColorsIndex = 2;
                    }
                    color = Ansi.Color.values()[exchangeIdColorsIndex];
                    exchangeIdColors.put(s, color);
                }
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
