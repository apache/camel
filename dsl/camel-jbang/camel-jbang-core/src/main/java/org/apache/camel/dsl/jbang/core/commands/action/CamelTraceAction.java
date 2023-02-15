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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;

import org.apache.camel.catalog.impl.TimePatternConverter;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.JSonHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
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

    // TODO: message dump in json or not (option)
    // TODO: filter on status
    // TODO: Output and internal Output
    // TODO: filter on exchangeId
    // TODO: --last-only

    private static final int NAME_MAX_WIDTH = 25;
    private static final int NAME_MIN_WIDTH = 10;

    @CommandLine.Parameters(description = "Name or pid of running Camel integration. (default selects all)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--timestamp" }, defaultValue = "true",
                        description = "Print timestamp.")
    boolean timestamp = true;

    @CommandLine.Option(names = { "--pretty" }, defaultValue = "false",
                        description = "Pretty print traced message")
    boolean pretty;

    @CommandLine.Option(names = { "--follow" }, defaultValue = "true",
                        description = "Keep following and outputting new traces (use ctrl + c to exit).")
    boolean follow = true;

    @CommandLine.Option(names = { "--prefix" }, defaultValue = "true",
                        description = "Print prefix with running Camel integration name.")
    boolean prefix = true;

    @CommandLine.Option(names = { "--tail" },
                        description = "The number of traces from the end of the trace to show. Defaults to showing all traces.")
    int tail;

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

    @CommandLine.Option(names = { "--show-message-headers" }, defaultValue = "true",
                        description = "Show message headers in traced messages")
    boolean showMessageHeaders = true;

    @CommandLine.Option(names = { "--show-message-body" }, defaultValue = "true",
                        description = "Show message body in traced messages")
    boolean showMessageBody = true;

    @CommandLine.Option(names = { "--show-exception" }, defaultValue = "true",
                        description = "Show exception and stacktrace for failed messages")
    boolean showException = true;

    String findAnsi;

    private int nameMaxWidth;

    private final Map<String, Ansi.Color> colors = new HashMap<>();

    public CamelTraceAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        Map<Long, Pid> pids = new LinkedHashMap<>();

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
            tailTraceFiles(pids, tail);
            dumpTraceFiles(pids, tail, limit);
        }

        if (follow) {
            boolean waitMessage = true;
            StopWatch watch = new StopWatch();
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
                        dumpTraceFiles(pids, 0, null);
                    } else {
                        Thread.sleep(100);
                    }
                }
            } while (true);
        }

        return 0;
    }

    private void tailTraceFiles(Map<Long, Pid> pids, int tail) throws Exception {
        for (Pid pid : pids.values()) {
            File file = getTraceFile(pid.pid);
            if (file.exists()) {
                pid.reader = new LineNumberReader(new FileReader(file));
                String line;
                if (tail == 0) {
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
                    Row row = new Row();
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
                    row.message = jo.getMap("message");
                    row.exception = jo.getMap("exception");
                    row.exchangeId = row.message.getString("exchangeId");
                    row.message.remove("exchangeId");
                    if (!showExchangeProperties) {
                        row.message.remove("exchangeProperties");
                    }
                    if (!showMessageHeaders) {
                        row.message.remove("headers");
                    }
                    if (!showMessageBody) {
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

    private void dumpTraceFiles(Map<Long, Pid> pids, int tail, Date limit) {
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

        rows.forEach(r -> {
            printTrace(r.name, r, limit);
        });
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

    protected void printTrace(String name, Row row, Date limit) {
        if (!prefix) {
            name = null;
        }

        String json = getDataAsJSon(row);
        boolean valid = isValidSince(limit, row.timestamp) && isValidGrep(json);
        if (!valid) {
            return;
        }

        if (name != null) {
            if (loggingColor) {
                Ansi.Color color = colors.get(name);
                if (color == null) {
                    // grab a new color
                    int idx = (colors.size() % 6) + 1;
                    color = Ansi.Color.values()[idx];
                    colors.put(name, color);
                }
                String n = String.format("%-" + nameMaxWidth + "s", name);
                AnsiConsole.out().print(Ansi.ansi().fg(color).a(n).a("| ").reset());
            } else {
                String n = String.format("%-" + nameMaxWidth + "s", name);
                System.out.print(n);
                System.out.print("| ");
            }
        }
        if (timestamp) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            String ts = sdf.format(new Date(row.timestamp));
            if (loggingColor) {
                AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(ts).reset());
            } else {
                System.out.print(ts);
            }
            System.out.print("  ");
        }
        // pid
        String p = String.format("%5.5s", row.pid);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgMagenta().a(p).reset());
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(" --- ").reset());
        } else {
            System.out.print(p);
            System.out.print(" --- ");
        }
        // exchange id
        String eid = row.exchangeId;
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(eid).reset());
        } else {
            System.out.print(eid);
        }
        System.out.print(" ");
        // route/node id
        String ids = row.routeId + "/" + getId(row);
        if (ids.length() > 25) {
            ids = ids.substring(ids.length() - 25);
        }
        ids = String.format("[%25.25s]", ids);
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(ids).reset());
        } else {
            System.out.print(ids);
        }
        System.out.print(" ");
        // source location
        String code = String.format("%-35.35s", row.location != null ? row.location : "");
        if (loggingColor) {
            AnsiConsole.out().print(Ansi.ansi().fgCyan().a(code).reset());
            AnsiConsole.out().print(Ansi.ansi().fgBrightDefault().a(" : ").reset());
        } else {
            System.out.print(code);
            System.out.print(" : ");
        }
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
        // trace message as json
        String[] lines = json.split(System.lineSeparator());
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
                System.out.println(line);
            }
            System.out.println();
        }
    }

    private String getDataAsJSon(Row r) {
        String s = r.message.toJson();
        if (loggingColor) {
            s = JSonHelper.colorPrint(s, 2, pretty);
        } else if (pretty) {
            s = JSonHelper.prettyPrint(s, 2);
        }
        String st = null;
        if (r.exception != null) {
            // include stacktrace
            st = Jsoner.unescape(r.exception.getString("stackTrace"));
            if (loggingColor) {
                st = Ansi.ansi().fg(Ansi.Color.RED).bold().a(st).reset().toString();
            }
        }
        if (st != null) {
            return s + System.lineSeparator() + st;
        } else {
            return s;
        }
    }

    private String getElapsed(Row r) {
        if (!r.first) {
            return TimeUtils.printDuration(r.elapsed, true);
        }
        return null;
    }

    private String getStatus(Row r) {
        if (r.first) {
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a("Input").reset().toString();
            } else {
                return "Input";
            }
        } else if (r.last) {
            if (loggingColor) {
                return Ansi.ansi().fg(r.failed ? Ansi.Color.RED : Ansi.Color.GREEN).a("Output").reset().toString();
            } else {
                return "Output";
            }
        }
        if (!r.done) {
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.BLUE).a("Processing").reset().toString();
            } else {
                return "Processing";
            }
        } else if (r.failed) {
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.RED).a("Failed").reset().toString();
            } else {
                return "Failed";
            }
        } else {
            if (loggingColor) {
                return Ansi.ansi().fg(Ansi.Color.GREEN).a("Success").reset().toString();
            } else {
                return "Success";
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

    private static class Pid implements Cloneable {
        String pid;
        String name;
        Queue<String> fifo;
        LineNumberReader reader;
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        boolean first;
        boolean last;
        long uid;
        String exchangeId;
        String location;
        String routeId;
        String nodeId;
        long timestamp;
        long elapsed;
        boolean done;
        boolean failed;
        JsonObject message;
        JsonObject exception;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }

}
