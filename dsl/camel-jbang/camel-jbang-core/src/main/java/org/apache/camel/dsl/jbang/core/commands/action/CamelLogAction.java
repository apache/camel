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
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

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

    @CommandLine.Option(names = {"--logging-color"}, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--tail" },
                        description = "The number of lines from the end of the logs to show. Defaults to showing all logs.")
    int tail;

    private int nameMaxWidth;

    private final Map<String, Ansi.Color> colors = new HashMap<>();

    public CamelLogAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids("*");
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
            if (tail > 0) {
                tailLogFiles(rows);
                dumpLogFiles(rows);
            }
            // read new log lines from multiple files
        }

        // continue read new log lines
//        do {
//            String line = lnr.readLine();
//            if (line != null) {
//                printLine(line);
//            } else {
//                Thread.sleep(50);
//            }
//        } while (true);
        return 0;
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
            System.out.println(line);
        } else {
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
            line = sb.toString();
            if (name != null) {
                String n = String.format("%-" + nameMaxWidth + "s", name);
                System.out.print(n);
                System.out.print(": ");
            }
            System.out.println(line);
        }
    }

    private static File logFile(String pid) {
        File dir = new File(System.getProperty("user.home"), ".camel");
        String name = pid + ".log";
        return new File(dir, name);
    }

    private void tailLogFiles(List<Row> rows) throws Exception {
        for (Row row : rows) {
            File log = logFile(row.pid);
            if (log.exists()) {
                LineNumberReader lnr = new LineNumberReader(new FileReader(log));
                String line;
                if (tail > 0) {
                    row.fifo = new ArrayBlockingQueue<>(tail);
                    do {
                        line = lnr.readLine();
                        if (line != null) {
                            while (!row.fifo.offer(line)) {
                                row.fifo.poll();
                            }
                        }
                    } while (line != null);
                }
            }
        }
    }

    private static class Row {
        String pid;
        String name;
        Queue<String> fifo;
    }

}
