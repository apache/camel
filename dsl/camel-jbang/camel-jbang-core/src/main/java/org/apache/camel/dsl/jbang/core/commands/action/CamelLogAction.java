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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

import org.apache.camel.catalog.impl.TimePatternConverter;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

@CommandLine.Command(name = "log",
                     description = "Tail logs from running Camel integrations", sortOptions = false)
public class CamelLogAction extends ActionBaseCommand {

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

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--timestamp" }, defaultValue = "true",
                        description = "Print timestamp.")
    boolean timestamp = true;

    @CommandLine.Option(names = { "--follow" }, defaultValue = "true",
                        description = "Keep following and outputting new log lines (use ctrl + c to exit).")
    boolean follow = true;

    @CommandLine.Option(names = { "--prefix" }, defaultValue = "auto", completionCandidates = PrefixCompletionCandidates.class,
                        description = "Print prefix with running Camel integration name. auto=only prefix when running multiple integrations. true=always prefix. false=prefix off.")
    String prefix = "auto";

    @CommandLine.Option(names = { "--tail" }, defaultValue = "-1",
                        description = "The number of lines from the end of the logs to show. Use -1 to read from the beginning. Use 0 to read only new lines. Defaults to showing all logs from beginning.")
    int tail = -1;

    @CommandLine.Option(names = { "--since" },
                        description = "Return logs newer than a relative duration like 5s, 2m, or 1h. The value is in seconds if no unit specified.")
    String since;

    @CommandLine.Option(names = { "--find" },
                        description = "Find and highlight matching text (ignore case).", arity = "0..*")
    String[] find;

    @CommandLine.Option(names = { "--grep" },
                        description = "Filter logs to only output lines matching text (ignore case).", arity = "0..*")
    String[] grep;

    String findAnsi;

    private int nameMaxWidth;
    private boolean prefixShown;

    private final Map<String, Ansi.Color> colors = new HashMap<>();

    public CamelLogAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Map<Long, Row> rows = new LinkedHashMap<>();

        // find new pids
        updatePids(rows);
        if (!rows.isEmpty()) {
            // read existing log files (skip by tail/since)
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
            if (tail != 0) {
                // dump existing log lines
                tailLogFiles(rows, tail, limit);
                dumpLogFiles(rows, tail);
            }
        }

        if (follow) {
            boolean waitMessage = true;
            StopWatch watch = new StopWatch();
            do {
                if (rows.isEmpty()) {
                    if (waitMessage) {
                        printer().println("Waiting for logs ...");
                        waitMessage = false;
                    }
                    Thread.sleep(500);
                    updatePids(rows);
                } else {
                    waitMessage = true;
                    if (watch.taken() > 500) {
                        // check for new logs
                        updatePids(rows);
                        watch.restart();
                    }
                    int lines = readLogFiles(rows);
                    if (lines > 0) {
                        dumpLogFiles(rows, 0);
                    } else {
                        Thread.sleep(100);
                    }
                }
            } while (true);
        }

        return 0;
    }

    private void updatePids(Map<Long, Row> rows) {
        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        Row row = new Row();
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

    private int readLogFiles(Map<Long, Row> rows) throws Exception {
        int lines = 0;

        for (Row row : rows.values()) {
            if (row.reader == null) {
                File file = logFile(row.pid);
                if (file.exists()) {
                    row.reader = new LineNumberReader(new FileReader(file));
                    if (tail == 0) {
                        // only read new lines so forward to end of reader
                        long size = file.length();
                        row.reader.skip(size);
                    }
                }
            }
            if (row.reader != null) {
                String line;
                do {
                    try {
                        line = row.reader.readLine();
                        if (line != null) {
                            boolean valid = true;
                            if (grep != null) {
                                valid = isValidGrep(line);
                            }
                            if (valid) {
                                lines++;
                                // switch fifo to be unlimited as we use it for new log lines
                                if (row.fifo == null || row.fifo instanceof ArrayBlockingQueue) {
                                    row.fifo = new ArrayDeque<>();
                                }
                                row.fifo.offer(line);
                            }
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

    private void dumpLogFiles(Map<Long, Row> rows, int tail) {
        Set<String> names = new HashSet<>();
        List<String> lines = new ArrayList<>();
        for (Row row : rows.values()) {
            Queue<String> queue = row.fifo;
            if (queue != null) {
                for (String l : queue) {
                    names.add(row.name);
                    lines.add(row.name + "| " + l);
                }
                row.fifo.clear();
            }
        }

        // only sort if there are multiple Camels running
        if (names.size() > 1) {
            // sort lines
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            lines.sort((l1, l2) -> {
                l1 = unescapeAnsi(l1);
                l2 = unescapeAnsi(l2);

                String n1 = StringHelper.before(l1, "| ");
                String t1 = StringHelper.after(l1, "| ");
                t1 = StringHelper.before(t1, "  ");
                String n2 = StringHelper.before(l2, "| ");
                String t2 = StringHelper.after(l2, "| ");
                t2 = StringHelper.before(t2, "  ");

                // there may be a stacktrace and no timestamps
                if (t1 != null) {
                    try {
                        sdf.parse(t1);
                    } catch (ParseException e) {
                        t1 = null;
                    }
                }
                if (t2 != null) {
                    try {
                        sdf.parse(t2);
                    } catch (ParseException e) {
                        t2 = null;
                    }
                }

                if (t1 == null && t2 == null) {
                    return 0;
                } else if (t1 == null) {
                    return -1;
                } else if (t2 == null) {
                    return 1;
                }
                return t1.compareTo(t2);
            });
        }
        if (tail > 0) {
            // cut according to tail
            int pos = lines.size() - tail;
            if (pos > 0) {
                lines = lines.subList(pos, lines.size());
            }
        }
        lines.forEach(l -> {
            String name = StringHelper.before(l, "| ");
            String line = StringHelper.after(l, "| ");
            printLine(name, rows.size(), line);
        });
    }

    protected void printLine(String name, int pids, String line) {
        if (!prefixShown) {
            // compute whether to show prefix or not
            if ("false".equals(prefix) || "auto".equals(prefix) && pids <= 1) {
                name = null;
            }
        }
        prefixShown = name != null;

        if (!timestamp) {
            // after timestamp is after 2 sine-space
            int pos = line.indexOf(' ');
            pos = line.indexOf(' ', pos + 1);
            if (pos != -1) {
                line = line.substring(pos + 1);
            }
        }
        if (loggingColor) {
            if (name != null) {
                Ansi.Color color = colors.get(name);
                if (color == null) {
                    // grab a new color
                    int idx = (colors.size() % 6) + 1;
                    color = Ansi.Color.values()[idx];
                    colors.put(name, color);
                }
                String n = String.format("%-" + nameMaxWidth + "s", name);
                AnsiConsole.out().print(Ansi.ansi().fg(color).a(n).a("| ").reset());
            }
        } else {
            line = unescapeAnsi(line);
            if (name != null) {
                String n = String.format("%-" + nameMaxWidth + "s", name);
                printer().print(n);
                printer().print("| ");
            }
        }
        if (find != null || grep != null) {
            String before = StringHelper.before(line, "---");
            String after = StringHelper.after(line, "---");
            if (find != null) {
                for (String f : find) {
                    after = after.replaceAll("(?i)" + f, findAnsi);
                }
            }
            if (grep != null) {
                for (String g : grep) {
                    after = after.replaceAll("(?i)" + g, findAnsi);
                }
            }
            line = before + "---" + after;
        }
        if (loggingColor) {
            AnsiConsole.out().println(line);
        } else {
            printer().println(line);
        }
    }

    private static File logFile(String pid) {
        String name = pid + ".log";
        return new File(CommandLineHelper.getCamelDir(), name);
    }

    private void tailLogFiles(Map<Long, Row> rows, int tail, Date limit) throws Exception {
        for (Row row : rows.values()) {
            File log = logFile(row.pid);
            if (log.exists()) {
                row.reader = new LineNumberReader(new FileReader(log));
                String line;
                if (tail <= 0) {
                    row.fifo = new ArrayDeque<>();
                } else {
                    row.fifo = new ArrayBlockingQueue<>(tail);
                }
                do {
                    line = row.reader.readLine();
                    if (line != null) {
                        boolean valid = isValidSince(limit, line);
                        if (valid && grep != null) {
                            valid = isValidGrep(line);
                        }
                        if (valid) {
                            while (!row.fifo.offer(line)) {
                                row.fifo.poll();
                            }
                        }
                    }
                } while (line != null);
            }
        }
    }

    private boolean isValidSince(Date limit, String line) {
        if (limit == null) {
            return true;
        }
        // the log can be in color or not so we need to unescape always
        line = unescapeAnsi(line);
        String ts = StringHelper.before(line, "  ");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        try {
            Date row = sdf.parse(ts);
            return row.compareTo(limit) >= 0;
        } catch (ParseException e) {
            // ignore
        }
        return false;
    }

    private boolean isValidGrep(String line) {
        if (grep == null) {
            return true;
        }
        // the log can be in color or not so we need to unescape always
        line = unescapeAnsi(line);
        String after = StringHelper.after(line, "---");
        for (String g : grep) {
            boolean m = Pattern.compile("(?i)" + g).matcher(after).find();
            if (m) {
                return true;
            }
        }
        return false;
    }

    private String unescapeAnsi(String line) {
        // unescape ANSI colors
        StringBuilder sb = new StringBuilder();
        boolean escaping = false;
        char[] arr = line.toCharArray();
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            if (escaping) {
                if (ch == 'm') {
                    escaping = false;
                }
                continue;
            }
            char ch2 = i < arr.length - 1 ? arr[i + 1] : 0;
            if (ch == 27 && ch2 == '[') {
                escaping = true;
                continue;
            }

            sb.append(ch);
        }
        return sb.toString();
    }

    private static class Row {
        String pid;
        String name;
        Queue<String> fifo;
        LineNumberReader reader;

    }

}
