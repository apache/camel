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
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Pattern;

import org.apache.camel.catalog.impl.TimePatternConverter;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonObject;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.AnsiConsole;
import picocli.CommandLine;

@CommandLine.Command(name = "log",
                     description = "Tail logs from running Camel integrations")
public class CamelLogAction extends ActionBaseCommand {

    private static final int NAME_MAX_WIDTH = 20;

    @CommandLine.Parameters(description = "Name or pid of running Camel integration. (default selects all)", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--follow" }, defaultValue = "true", description = "Keep following and outputting new log lines (use ctrl + c to exit).")
    boolean follow = true;

    @CommandLine.Option(names = { "--tail" },
                        description = "The number of lines from the end of the logs to show. Defaults to showing all logs.")
    int tail;

    @CommandLine.Option(names = { "--since" },
                        description = "Return logs newer than a relative duration like 5s, 2m, or 1h. The value is in seconds if no unit specified.")
    String since;

    @CommandLine.Option(names = { "--find" },
                        description = "Find and highlight matching text (ignore case).", arity = "0..*")
    String[] find;

    String findAnsi;

    private int nameMaxWidth;

    private final Map<String, Ansi.Color> colors = new HashMap<>();

    public CamelLogAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        Row row = new Row();
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
                        if (len > NAME_MAX_WIDTH) {
                            len = NAME_MAX_WIDTH;
                        }
                        if (len > nameMaxWidth) {
                            nameMaxWidth = len;
                        }
                        rows.add(row);
                    }
                });

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

            // dump existing log lines
            tailLogFiles(rows, tail, limit);
            dumpLogFiles(rows);

            if (follow) {
                do {
                    int lines = readLogFiles(rows);
                    if (lines > 0) {
                        dumpLogFiles(rows);
                    } else {
                        Thread.sleep(50);
                    }
                } while (true);
            }
        }

        return 0;
    }

    private int readLogFiles(List<Row> rows) throws Exception {
        int lines = 0;

        for (Row row : rows) {
            if (row.reader == null) {
                File log = logFile(row.pid);
                if (log.exists()) {
                    row.reader = new LineNumberReader(new FileReader(log));
                }
            }
            if (row.reader != null) {
                try {
                    String line = row.reader.readLine();
                    if (line != null) {
                        lines++;
                        // switch fifo to be unlimited as we use it for new log lines
                        if (row.fifo == null || row.fifo instanceof ArrayBlockingQueue) {
                            row.fifo = new ArrayDeque<>();
                        }
                        row.fifo.offer(line);
                    }
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return lines;
    }

    private void dumpLogFiles(List<Row> rows) {
        List<String> lines = new ArrayList<>();
        for (Row row : rows) {
            Queue<String> queue = row.fifo;
            if (queue != null) {
                for (String l : queue) {
                    lines.add(row.name + ": " + l);
                }
                row.fifo.clear();
            }
        }
        // sort lines
        lines.sort(this::compareLogLine);
        if (tail > 0) {
            // cut according to tail
            int pos = lines.size() - tail;
            if (pos > 0) {
                lines = lines.subList(pos, lines.size());
            }
        }
        lines.forEach(l -> {
            String name = StringHelper.before(l, ": ");
            String line = StringHelper.after(l, ": ");
            printLine(name, line);
        });
    }

    private int compareLogLine(String l1, String l2) {
        String t1 = StringHelper.after(l1, ": ");
        t1 = StringHelper.before(t1, "  ");
        String t2 = StringHelper.after(l2, ": ");
        t2 = StringHelper.before(t2, "  ");
        return t1.compareTo(t2);
    }

    protected void printLine(String name, String line) {
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
                AnsiConsole.out().print(Ansi.ansi().fg(color).a(n).a(": ").reset());
            }
        } else {
            line = unescapeAnsi(line);
            if (name != null) {
                String n = String.format("%-" + nameMaxWidth + "s", name);
                System.out.print(n);
                System.out.print(": ");
            }
        }
        if (find != null) {
            String before = StringHelper.before(line, "---");
            String after = StringHelper.after(line, "---");
            for (String f : find) {
                after = after.replaceAll("(?i)" + f, findAnsi);
            }
            line = before + "---" + after;
        }
        System.out.println(line);
    }

    private static File logFile(String pid) {
        File dir = new File(System.getProperty("user.home"), ".camel");
        String name = pid + ".log";
        return new File(dir, name);
    }

    private void tailLogFiles(List<Row> rows, int tail, Date limit) throws Exception {
        for (Row row : rows) {
            File log = logFile(row.pid);
            if (log.exists()) {
                row.reader = new LineNumberReader(new FileReader(log));
                String line;
                if (tail == 0) {
                    row.fifo = new ArrayDeque<>();
                } else {
                    row.fifo = new ArrayBlockingQueue<>(tail);
                }
                do {
                    line = row.reader.readLine();
                    if (line != null) {
                        boolean valid = isValidSince(limit, line);
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
