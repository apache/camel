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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.JSonHelper;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "trace",
         description = "Get latest traced messages of Camel integrations")
public class ListTrace extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--latest" },
                        description = "Only output traces of latest message")
    boolean latest;

    @CommandLine.Option(names = { "--brief" },
                        description = "Brief mode to only show traces of input and output (no intermediate processing steps)")
    boolean brief;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print traced message")
    boolean pretty;

    public ListTrace(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    // there must be a status file for the running Camel integration
                    if (root != null) {
                        Row row = new Row();
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        row.pid = "" + ph.pid();
                        fetchTraces(row, rows);
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            String data = AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("UID").headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.RIGHT)
                            .maxWidth(6).with(this::getUid),
                    new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.routeId),
                    new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(this::getId),
                    new Column().header("AGE").dataAlign(HorizontalAlign.RIGHT).with(this::getTimestamp),
                    new Column().header("ELAPSED").dataAlign(HorizontalAlign.RIGHT).with(this::getElapsed),
                    new Column().header("FAILED").dataAlign(HorizontalAlign.RIGHT).with(this::getFailed)));
            String[] arr = data.split(System.lineSeparator());
            // print header
            System.out.println(arr[0]);
            // mix column and message (master/detail) mode
            for (int i = 0; i < rows.size(); i++) {
                String s = arr[i + 1];
                System.out.println(s);
                String json = getMessage(rows.get(i));
                // pad with 8 spaces to indent json data
                String[] lines = json.split(System.lineSeparator());
                for (String line : lines) {
                    System.out.print("        ");
                    System.out.println(line);
                }
            }
        }

        return 0;
    }

    private void fetchTraces(Row row, List<Row> rows) {
        // load trace file if exists
        JsonObject root = loadTrace(row.pid);
        String lastId = null;
        if (root != null) {
            List<Row> local = new ArrayList<>();
            JsonArray arr = root.getCollection("traces");
            if (arr != null) {
                for (Object o : arr) {
                    row = row.copy();
                    JsonObject jo = (JsonObject) o;
                    row.uid = jo.getLong("uid");
                    row.first = jo.getBoolean("first");
                    row.last = jo.getBoolean("last");
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
                    lastId = getExchangeId(row);
                    local.add(row);
                }
            }
            if (latest && lastId != null) {
                // filter out all that does not match last exchange id
                final String target = lastId;
                local.removeIf(r -> !target.equals(getExchangeId(r)));
            }
            if (brief) {
                local.removeIf(r -> !r.first && !r.last);
            }
            rows.addAll(local);
        }
    }

    private JsonObject loadTrace(String pid) {
        try {
            File f = getTraceFile(pid);
            if (f != null && f.exists()) {
                FileInputStream fis = new FileInputStream(f);
                String text = IOHelper.loadText(fis);
                IOHelper.close(fis);
                return (JsonObject) Jsoner.deserialize(text);
            }
        } catch (Throwable e) {
            // ignore
        }
        return null;
    }

    private String getTimestamp(Row r) {
        if (r.timestamp > 0) {
            return TimeUtils.printSince(r.timestamp);
        }
        return "";
    }

    private String getElapsed(Row r) {
        if (r.elapsed > 0) {
            return TimeUtils.printDuration(r.elapsed, true);
        }
        return "";
    }

    private String getFailed(Row r) {
        if (r.failed) {
            return "1";
        }
        return "0";
    }

    private String getUid(Row r) {
        return "" + r.uid;
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

    private String getExchangeId(Row r) {
        return r.message.getString("exchangeId");
    }

    private String getMessage(Row r) {
        String s = r.message.toJson();
        if (pretty) {
            s = JSonHelper.colorPrint(s, 2);
        }
        return s;
    }

    protected int sortRow(Row o1, Row o2) {
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
                return Long.compare(o1.timestamp, o2.timestamp) * negate;
            default:
                return 0;
        }
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        boolean first;
        boolean last;
        long uid;
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
