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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "thread-dump", description = "List threads in a running Camel integration", sortOptions = false)
public class CamelThreadDump extends ActionWatchCommand {

    public static class IdNameStateCompletionCandidates implements Iterable<String> {

        public IdNameStateCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("id", "name", "state").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = IdNameStateCompletionCandidates.class,
                        description = "Sort by id, name or state", defaultValue = "id")
    String sort;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter thread names (use all to include all threads)", defaultValue = "Camel")
    String filter;

    @CommandLine.Option(names = { "--trace" },
                        description = "Include stack-traces", defaultValue = "false")
    boolean trace;

    @CommandLine.Option(names = { "--depth" },
                        description = "Max depth of stack-trace", defaultValue = "1")
    int depth;

    private volatile long pid;

    public CamelThreadDump(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        // include stack-traces
        if (trace && depth <= 1) {
            depth = Integer.MAX_VALUE;
        }

        this.pid = pids.get(0);

        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "thread-dump");
        File f = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), f);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            JsonArray arr = (JsonArray) jo.get("threads");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject jt = (JsonObject) arr.get(i);

                Row row = new Row();
                row.id = jt.getLong("id");
                row.name = jt.getString("name");

                // filter
                boolean match
                        = "all".equals(filter) || row.name.contains(filter) || PatternHelper.matchPattern(row.name, filter);
                if (!match) {
                    continue;
                }

                row.state = jt.getString("state");
                row.waited = jt.getLong("waitedCount");
                row.waitedTime = jt.getLong("waitedTime");
                row.blocked = jt.getLong("blockedCount");
                row.blockedTime = jt.getLong("blockedTime");
                row.lock = jt.getString("lockName");
                row.stackTrace = jt.getCollection("stackTrace");
                rows.add(row);
            }
        } else {
            printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        // sort rows
        rows.sort(this::sortRow);

        if (watch) {
            clearScreen();
        }
        if (!rows.isEmpty()) {
            int total = jo.getInteger("threadCount");
            int peak = jo.getInteger("peakThreadCount");
            printer().printf("PID: %s\tThreads: %d\tPeak: %d\t\tDisplay: %d/%d\n", pid, total, peak, rows.size(), total);

            if (depth == 1) {
                singleTable(rows);
            } else {
                tableAndStackTrace(rows);
            }
        }

        // delete output file after use
        FileUtil.deleteFile(outputFile);

        return 0;
    }

    protected void singleTable(List<Row> rows) {
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("ID").headerAlign(HorizontalAlign.CENTER).with(r -> Long.toString(r.id)),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(60, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("STATE").headerAlign(HorizontalAlign.RIGHT).with(r -> r.state),
                new Column().header("BLOCK").with(this::getBlocked),
                new Column().header("WAIT").with(this::getWaited),
                new Column().header("STACKTRACE").headerAlign(HorizontalAlign.RIGHT)
                        .maxWidth(70, OverflowBehaviour.ELLIPSIS_LEFT).with(this::getStackTrace))));
    }

    protected void tableAndStackTrace(List<Row> rows) {
        for (Row row : rows) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(row), Arrays.asList(
                    new Column().header("ID").headerAlign(HorizontalAlign.CENTER).with(r -> Long.toString(r.id)),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(60, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("STATE").headerAlign(HorizontalAlign.RIGHT).with(r -> r.state),
                    new Column().header("BLOCK").with(this::getBlocked),
                    new Column().header("WAIT").with(this::getWaited))));
            for (int i = 0; i < depth && i < row.stackTrace.size(); i++) {
                printer().println("\t" + row.stackTrace.get(i));
            }
        }
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "id":
                return Long.compare(o1.id, o2.id) * negate;
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            case "state":
                return o1.state.compareToIgnoreCase(o2.state) * negate;
            default:
                return 0;
        }
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        return getJsonObject(outputFile);
    }

    private String getBlocked(Row r) {
        if (r.blockedTime > 0) {
            return r.blocked + "(" + r.blockedTime + "ms)";
        } else {
            return Long.toString(r.blocked);
        }
    }

    private String getWaited(Row r) {
        if (r.waitedTime > 0) {
            return r.waited + "(" + r.waitedTime + "ms)";
        } else {
            return Long.toString(r.waited);
        }
    }

    private String getStackTrace(Row r) {
        if (r.stackTrace == null || r.stackTrace.isEmpty()) {
            return "";
        }
        return r.stackTrace.get(0);
    }

    private static class Row {
        long id;
        String name;
        String state;
        long waited;
        long waitedTime;
        long blocked;
        long blockedTime;
        String lock;
        List<String> stackTrace;
    }

}
