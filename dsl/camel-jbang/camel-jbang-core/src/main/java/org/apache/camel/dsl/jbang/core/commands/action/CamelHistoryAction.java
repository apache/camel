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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;

@CommandLine.Command(name = "history",
                     description = "History of latest completed exchange", sortOptions = false, showDefaultValues = true)
public class CamelHistoryAction extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--source" },
                        description = "Prefer to display source filename/code instead of IDs")
    boolean source;

    @CommandLine.Option(names = { "--mask" },
                        description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    // TODO: option to max deep level (like trace)
    // TODO: option to collapse split (to cut very large split)

    private final Set<String> usedImportant = new HashSet<>();

    public CamelHistoryAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doWatchCall() throws Exception {
        if (name == null) {
            name = "*";
        }

        List<List<Row>> pids = loadRows();

        if (!pids.isEmpty()) {
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
        if (r.first || r.last) {
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

        Map<String, String> map = extractImportant(r);
        if (map.isEmpty()) {
            return null;
        }
        StringJoiner sj = new StringJoiner(" ");

        // special for split
        String si = map.remove("CamelSplitIndex");
        String sv = map.remove("CamelSplitSize");
        if (si != null && sv != null) {
            int num = Integer.parseInt(sv) - 1;
            sj.add("Split (" + si + "/" + num + ")");
        } else if (si != null) {
            sj.add("Split (" + si + ")");
        }
        // special for file
        String fn = map.remove("CamelFileName");
        String fs = map.remove("CamelFileLength");
        if (fn != null && fs != null) {
            String line = "File: " + fn + " (" + fs + " bytes)";
            if (usedImportant.add(line)) {
                sj.add(line);
            }
        } else if (fn != null) {
            String line = "File: " + fn;
            if (usedImportant.add(line)) {
                sj.add(line);
            }
        }

        map.forEach((k,v) -> {
            String line = k + "=" + v;
            if (usedImportant.add(line)) {
                // avoid duplicates so only show unique
                sj.add(k + "=" + v);
            }
        });

        return sj.toString();
    }

    private Map<String, String> extractImportant(Row r) {
        Map<String, String> answer = new LinkedHashMap<>();

        JsonArray arr = r.message.getCollection("exchangeProperties");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonObject jo = (JsonObject) arr.get(i);
                if (jo.getBooleanOrDefault("important", false)) {
                    answer.put(jo.getString("key"), jo.getString("value"));
                }
            }
        }
        arr = r.message.getCollection("headers");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonObject jo = (JsonObject) arr.get(i);
                if (jo.getBooleanOrDefault("important", false)) {
                    answer.put(jo.getString("key"), jo.getString("value"));
                }
            }
        }
        return answer;
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
                    row.nodeShortName = jo.getString("nodeShortName");
                    row.nodeLabel = jo.getString("nodeLabel");
                    if (mask) {
                        row.nodeLabel = URISupport.sanitizeUri(row.nodeLabel);
                    }
                    row.nodeLevel = jo.getIntegerOrDefault("nodeLevel", 0);
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
                    rows.add(row);
                }
            }
            return rows;
        }
        return null;
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
        String nodeShortName;
        String nodeLabel;
        int nodeLevel;
        long timestamp;
        long elapsed;
        boolean done;
        boolean failed;
        JsonObject endpoint;
        JsonObject endpointService;
        JsonObject message;
        JsonObject exception;
    }

}
