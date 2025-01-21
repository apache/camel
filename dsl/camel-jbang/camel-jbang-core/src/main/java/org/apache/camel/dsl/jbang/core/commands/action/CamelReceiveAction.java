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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.catalog.impl.TimePatternConverter;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.CommandHelper;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

@CommandLine.Command(name = "receive",
                     description = "Receive and dump messages from remote endpoints", sortOptions = false,
                     showDefaultValues = true)
public class CamelReceiveAction extends ActionBaseCommand {

    private static final int NAME_MAX_WIDTH = 25;
    private static final int NAME_MIN_WIDTH = 10;

    private CommandHelper.ReadConsoleTask waitUserTask;

    public static class PrefixCompletionCandidates implements Iterable<String> {

        public PrefixCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("auto", "true", "false").iterator();
        }
    }

    public static class ActionCompletionCandidates implements Iterable<String> {

        public ActionCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("dump", "start", "stop", "status", "clear").iterator();
        }
    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration. (default selects all)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--action" }, completionCandidates = ActionCompletionCandidates.class,
                        defaultValue = "status",
                        description = "Action to start, stop, clear, list status, or dump messages")
    String action;

    @CommandLine.Option(names = { "--endpoint" },
                        description = "Endpoint to browse messages (can be uri or pattern to refer to existing endpoint)")
    String endpoint;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age for showing status of messages", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--follow" }, defaultValue = "true",
                        description = "Keep following and outputting new messages (press enter to exit).")
    boolean follow = true;

    @CommandLine.Option(names = { "--prefix" }, defaultValue = "auto",
                        completionCandidates = PrefixCompletionCandidates.class,
                        description = "Print prefix with running Camel integration name. auto=only prefix when running multiple integrations. true=always prefix. false=prefix off.")
    String prefix = "auto";

    @CommandLine.Option(names = { "--tail" }, defaultValue = "-1",
                        description = "The number of messages from the end to show. Use -1 to read from the beginning. Use 0 to read only new lines. Defaults to showing all messages from beginning.")
    int tail = -1;

    @CommandLine.Option(names = { "--since" },
                        description = "Return messages newer than a relative duration like 5s, 2m, or 1h. The value is in seconds if no unit specified.")
    String since;

    @CommandLine.Option(names = { "--find" },
                        description = "Find and highlight matching text (ignore case).", arity = "0..*")
    String[] find;

    @CommandLine.Option(names = { "--grep" },
                        description = "Filter messages to only output matching text (ignore case).", arity = "0..*")
    String[] grep;

    @CommandLine.Option(names = { "--show-headers" }, defaultValue = "true",
                        description = "Show message headers in received messages")
    boolean showHeaders = true;

    @CommandLine.Option(names = { "--show-body" }, defaultValue = "true",
                        description = "Show message body in received messages")
    boolean showBody = true;

    @CommandLine.Option(names = { "--only-body" }, defaultValue = "false",
                        description = "Show only message body in received messages")
    boolean onlyBody;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--compact" }, defaultValue = "true",
                        description = "Compact output (no empty line separating messages)")
    boolean compact = true;

    @CommandLine.Option(names = { "--short-uri" },
                        description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @CommandLine.Option(names = { "--wide-uri" },
                        description = "List endpoint URI in full details")
    boolean wideUri;

    @CommandLine.Option(names = { "--mask" },
                        description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    String findAnsi;
    private int nameMaxWidth;
    private boolean prefixShown;
    private MessageTableHelper tableHelper;
    private final Map<String, Ansi.Color> nameColors = new HashMap<>();

    public CamelReceiveAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        boolean autoDump = false;
        if (endpoint != null) {
            // if using --endpoint then action should be start and auto-dump
            action = "start";
            autoDump = true;
        }

        if ("dump".equals(action)) {
            return doDumpCall();
        } else if ("status".equals(action)) {
            return doStatusCall();
        }

        List<Long> pids = findPids(name);
        for (long pid : pids) {
            if ("clear".equals(action)) {
                File f = getReceiveFile("" + pid);
                if (f.exists()) {
                    IOHelper.writeText("{}", f);
                }
            } else {
                // ensure output file is deleted before executing action
                File outputFile = getOutputFile(Long.toString(pid));
                FileUtil.deleteFile(outputFile);

                JsonObject root = new JsonObject();
                root.put("action", "receive");
                if ("start".equals(action)) {
                    root.put("enabled", "true");
                    if (endpoint != null) {
                        root.put("endpoint", endpoint);
                    } else {
                        root.put("endpoint", "*");
                    }
                } else if ("stop".equals(action)) {
                    root.put("enabled", "false");
                }
                File f = getActionFile(Long.toString(pid));
                IOHelper.writeText(root.toJson(), f);

                JsonObject jo = waitForOutputFile(outputFile);
                if (jo != null) {
                    String error = jo.getString("error");
                    if (error != null) {
                        error = Jsoner.unescape(error);
                        String url = jo.getString("url");
                        List<String> stackTrace = jo.getCollection("stackTrace");
                        if (url != null) {
                            printer().println("Error starting to receive messages from: " + url + " due to: " + error);

                        } else {
                            printer().println("Error starting to receive messages due to: " + error);
                        }
                        printer().println(StringHelper.fillChars('-', 120));
                        printer().println(StringHelper.padString(1, 55) + "STACK-TRACE");
                        printer().println(StringHelper.fillChars('-', 120));
                        StringBuilder sb = new StringBuilder();
                        for (String s : stackTrace) {
                            sb.append(String.format("\t%s%n", s));
                        }
                        printer().println(String.valueOf(sb));
                    }
                }
            }
        }

        if (autoDump) {
            return doDumpCall();
        }

        return 0;
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        return getJsonObject(outputFile);
    }

    protected Integer doStatusCall() {
        List<StatusRow> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        StatusRow row = new StatusRow();
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        row.pid = Long.toString(ph.pid());
                        row.uptime = extractSince(ph);
                        row.age = TimeUtils.printSince(row.uptime);
                        JsonObject jo = root.getMap("receive");
                        if (jo != null) {
                            row.enabled = jo.getBoolean("enabled");
                            row.counter = jo.getLong("total");
                            row.firstTimestamp = jo.getLongOrDefault("firstTimestamp", 0);
                            row.lastTimestamp = jo.getLongOrDefault("lastTimestamp", 0);
                            JsonArray arr = jo.getCollection("endpoints");
                            if (arr != null) {
                                for (Object e : arr) {
                                    jo = (JsonObject) e;
                                    row.uri = jo.getString("uri");
                                    if (mask) {
                                        row.uri = URISupport.sanitizeUri(row.uri);
                                    }
                                    rows.add(row);
                                    row = row.copy();
                                }
                            } else {
                                rows.add(row);
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortStatusRow);

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                    new Column().header("STATUS").with(this::getStatus),
                    new Column().header("TOTAL").with(r -> r.enabled ? "" + r.counter : ""),
                    new Column().header("SINCE").headerAlign(HorizontalAlign.CENTER)
                            .with(this::getMessageAgo),
                    new Column().header("ENDPOINT").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(90, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(this::getEndpointUri),
                    new Column().header("ENDPOINT").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(140, OverflowBehaviour.NEWLINE)
                            .with(r -> r.uri))));
        }

        return 0;
    }

    private String getStatus(StatusRow r) {
        if (r.enabled) {
            return "Enabled";
        }
        return "Disabled";
    }

    protected int sortStatusRow(StatusRow o1, StatusRow o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "pid":
                return Long.compare(Long.parseLong(o1.pid), Long.parseLong(o2.pid)) * negate;
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            case "age":
                return Long.compare(o1.uptime, o2.uptime) * negate;
            default:
                return 0;
        }
    }

    protected Integer doDumpCall() throws Exception {
        // setup table helper
        tableHelper = new MessageTableHelper();
        tableHelper.setPretty(pretty);
        tableHelper.setLoggingColor(loggingColor);

        Map<Long, Pid> pids = new LinkedHashMap<>();

        // find new pids
        updatePids(pids);
        if (!pids.isEmpty()) {
            // read existing received files (skip by tail/since)
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
            // dump existing messages
            if (tail != 0) {
                tailReceiveFiles(pids, tail);
                dumpReceiveFiles(pids, tail, limit);
            }
        }

        if (follow) {
            boolean waitMessage = true;
            final AtomicBoolean running = new AtomicBoolean(true);
            Thread t = new Thread(() -> {
                waitUserTask = new CommandHelper.ReadConsoleTask(() -> running.set(false));
                waitUserTask.run();
            }, "WaitForUser");
            t.start();
            boolean more = true;
            boolean init = true;
            StopWatch watch = new StopWatch();
            do {
                if (pids.isEmpty()) {
                    if (waitMessage) {
                        printer().println("Waiting for messages ...");
                        waitMessage = false;
                    }
                    Thread.sleep(500);
                    updatePids(pids);
                } else {
                    waitMessage = true;
                    if (watch.taken() > 500) {
                        // check for new messages
                        updatePids(pids);
                        watch.restart();
                    }
                    int lines = readReceiveFiles(pids);
                    if (lines > 0) {
                        more = dumpReceiveFiles(pids, 0, null);
                        init = false;
                    } else if (lines == 0) {
                        if (init) {
                            printer().println("Waiting for messages ...");
                            init = false;
                        }
                        Thread.sleep(100);
                    } else {
                        break;
                    }
                }
            } while (more && running.get());
        }

        return 0;
    }

    private void tailReceiveFiles(Map<Long, Pid> pids, int tail) throws Exception {
        for (Pid pid : pids.values()) {
            File file = getReceiveFile(pid.pid);
            if (file.exists() && file.length() > 0) {
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
                        row.pid = Long.toString(ph.pid());
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

    private int readReceiveFiles(Map<Long, Pid> pids) throws Exception {
        int lines = 0;

        for (Pid pid : pids.values()) {
            if (pid.reader == null) {
                File file = getReceiveFile(pid.pid);
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
                            // switch fifo to be unlimited as we use it for new messages
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

    private List<Row> parseReceiveLine(Pid pid, String line) {
        JsonObject root = null;
        try {
            root = (JsonObject) Jsoner.deserialize(line);
        } catch (Exception e) {
            // ignore
        }
        if (root != null) {
            List<Row> rows = new ArrayList<>();
            JsonArray arr = root.getCollection("messages");
            if (arr != null) {
                for (Object o : arr) {
                    Row row = new Row(pid);
                    row.pid = pid.pid;
                    row.name = pid.name;
                    JsonObject jo = (JsonObject) o;
                    row.uid = jo.getLong("uid");
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
                    row.message = jo.getMap("message");
                    row.message.remove("exchangeId");
                    row.message.remove("exchangePattern");
                    row.message.remove("exchangeProperties");
                    if (onlyBody) {
                        row.message.remove("headers");
                        row.message.remove("messageType");
                    } else {
                        if (!showHeaders) {
                            row.message.remove("headers");
                        }
                        if (!showBody) {
                            row.message.remove("body");
                        }
                    }
                    rows.add(row);
                }
            }
            return rows;
        }
        return null;
    }

    private boolean dumpReceiveFiles(Map<Long, Pid> pids, int tail, Date limit) {
        Set<String> names = new HashSet<>();
        List<Row> rows = new ArrayList<>();
        for (Pid pid : pids.values()) {
            Queue<String> queue = pid.fifo;
            if (queue != null) {
                for (String l : queue) {
                    names.add(pid.name);
                    List<Row> parsed = parseReceiveLine(pid, l);
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

        for (Row r : rows) {
            printDump(r.name, pids.size(), r, limit);
        }
        return true;
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

    protected void printDump(String name, int pids, Row row, Date limit) {
        if (!prefixShown) {
            // compute whether to show prefix or not
            if ("false".equals(prefix) || "auto".equals(prefix) && pids <= 1) {
                name = null;
            }
        }
        prefixShown = name != null;

        String data = getDataAsTable(row);
        boolean valid = isValidSince(limit, row.timestamp) && isValidGrep(data);
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
            printer().print(nameWithPrefix);
        }
        // header
        String header = String.format("Received Message: (%s)", row.uid);
        if (loggingColor) {
            printer().println(Ansi.ansi().fgGreen().a(header).reset().toString());
        } else {
            printer().println(header);
        }
        String[] lines = data.split(System.lineSeparator());
        if (lines.length > 0) {
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
                    printer().print(nameWithPrefix);
                }
                printer().print(" ");
                printer().println(line);
            }
            if (!compact) {
                if (nameWithPrefix != null) {
                    printer().println(nameWithPrefix);
                } else {
                    // empty line
                    printer().println();
                }
            }
        }
    }

    private String getDataAsTable(Row r) {
        return tableHelper.getDataAsTable(null, null, r.endpoint, r.endpointService, r.message, null);
    }

    protected String getEndpointUri(StatusRow r) {
        String u = r.uri;
        if (shortUri) {
            int pos = u.indexOf('?');
            if (pos > 0) {
                u = u.substring(0, pos);
            }
        }
        return u;
    }

    protected String getMessageAgo(StatusRow r) {
        if (r.lastTimestamp > 0) {
            return TimeUtils.printSince(r.lastTimestamp);
        }
        return "";
    }

    private static class Pid {
        String pid;
        String name;
        Queue<String> fifo;
        LineNumberReader reader;
    }

    private static class Row {
        Pid parent;
        String pid;
        String name;
        long uid;
        long timestamp;
        JsonObject endpoint;
        JsonObject endpointService;
        JsonObject message;

        Row(Pid parent) {
            this.parent = parent;
        }

    }

    private static class StatusRow {
        String pid;
        String name;
        String age;
        long uptime;
        boolean enabled;
        long counter;
        long firstTimestamp;
        long lastTimestamp;
        String uri;

        StatusRow copy() {
            try {
                return (StatusRow) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }

}
