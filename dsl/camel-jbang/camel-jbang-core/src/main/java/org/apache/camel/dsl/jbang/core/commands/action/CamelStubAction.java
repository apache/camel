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
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.fusesource.jansi.Ansi;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "stub", description = "Browse stub endpoints", sortOptions = false)
public class CamelStubAction extends ActionWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by name, or total", defaultValue = "name")
    String sort;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter endpoints by queue name")
    String filter;

    @CommandLine.Option(names = { "--browse" },
                        description = "Whether to browse messages queued in the stub endpoints")
    boolean browse;

    @CommandLine.Option(names = { "--top" }, defaultValue = "true",
                        description = "Whether to browse top (latest) messages queued in the stub endpoints")
    boolean top = true;

    @CommandLine.Option(names = { "--limit" }, defaultValue = "10",
                        description = "Filter browsing queues by limiting to the given latest number of messages")
    int limit = 10;

    @CommandLine.Option(names = { "--find" },
                        description = "Find and highlight matching text (ignore case).", arity = "0..*")
    String[] find;

    @CommandLine.Option(names = { "--grep" },
                        description = "Filter browsing messages to only output trace matching text (ignore case).",
                        arity = "0..*")
    String[] grep;

    @CommandLine.Option(names = { "--show-headers" }, defaultValue = "true",
                        description = "Show message headers in traced messages")
    boolean showHeaders = true;

    @CommandLine.Option(names = { "--show-body" }, defaultValue = "true",
                        description = "Show message body in traced messages")
    boolean showBody = true;

    @CommandLine.Option(names = { "--compact" }, defaultValue = "true",
                        description = "Compact output (no empty line separating browsed messages)")
    boolean compact = true;

    @CommandLine.Option(names = { "--mask" },
                        description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", description = "Use colored logging")
    boolean loggingColor = true;

    private volatile long pid;
    String findAnsi;
    private MessageTableHelper tableHelper;
    private final Map<String, Ansi.Color> exchangeIdColors = new HashMap<>();
    private int exchangeIdColorsIndex = 1;

    public CamelStubAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected Integer doWatchCall() throws Exception {
        // setup table helper
        tableHelper = new MessageTableHelper();
        tableHelper.setPretty(pretty);
        tableHelper.setLoggingColor(loggingColor);
        tableHelper.setExchangeIdColorChooser(value -> {
            Ansi.Color color = exchangeIdColors.get(value);
            if (color == null) {
                // grab a new color
                exchangeIdColorsIndex++;
                if (exchangeIdColorsIndex > 6) {
                    exchangeIdColorsIndex = 2;
                }
                color = Ansi.Color.values()[exchangeIdColorsIndex];
                exchangeIdColors.put(value, color);
            }
            return color;
        });
        if (find != null || grep != null) {
            findAnsi = Ansi.ansi().fg(Ansi.Color.BLACK).bg(Ansi.Color.YELLOW).a("$0").reset().toString();
        }

        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            System.out.println("Name or pid " + name + " matches " + pids.size()
                               + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        this.pid = pids.get(0);

        if (filter == null) {
            filter = "*";
        }

        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "stub");
        root.put("format", "json");
        root.put("browse", browse);
        root.put("filter", "*");
        root.put("limit", limit);
        File file = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), file);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            JsonObject me = loadStatus(pid);
            if (me != null) {
                me = (JsonObject) me.get("context");
            }

            JsonArray arr = (JsonArray) jo.get("queues");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = (JsonObject) arr.get(i);
                    Row row = new Row();
                    row.pid = pid;
                    row.name = me != null ? me.getString("name") : null;
                    row.queue = o.getString("name");
                    row.max = o.getInteger("max");
                    row.size = o.getInteger("size");
                    String uri = o.getString("endpointUri");
                    if (uri != null) {
                        row.endpoint = new JsonObject();
                        if (mask) {
                            uri = URISupport.sanitizeUri(uri);
                        }
                        row.endpoint.put("endpoint", uri);
                    }
                    row.messages = o.getCollection("messages");
                    boolean add = true;
                    if (filter != null) {
                        String f = filter;
                        boolean negate = filter.startsWith("-");
                        if (negate) {
                            f = f.substring(1);
                        }
                        // make filtering easier
                        if (!f.endsWith("*")) {
                            f += "*";
                        }
                        boolean match = PatternHelper.matchPattern(row.queue, f);
                        if (negate) {
                            match = !match;
                        }
                        if (!match) {
                            add = false;
                        }
                    }
                    if (add) {
                        rows.add(row);
                    }
                }
            }
        } else {
            System.out.println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        // sort rows
        rows.sort(this::sortRow);

        if (watch) {
            clearScreen();
        }
        if (!rows.isEmpty()) {
            printStub(rows);
        }

        // delete output file after use
        FileUtil.deleteFile(outputFile);

        return 0;
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            case "total":
                return Integer.compare(o1.size, o2.size) * negate;
            default:
                return 0;
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

    protected void printStub(List<Row> rows) {
        if (browse) {
            for (Row row : rows) {
                System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(row), Arrays.asList(
                        new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> "" + r.pid),
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.name),
                        new Column().header("QUEUE").dataAlign(HorizontalAlign.LEFT).with(r -> r.queue),
                        new Column().header("MAX").dataAlign(HorizontalAlign.RIGHT).with(r -> "" + r.max),
                        new Column().header("TOTAL").dataAlign(HorizontalAlign.RIGHT).with(r -> "" + r.size))));

                if (row.messages != null) {
                    List<JsonObject> list = row.messages;
                    if (top) {
                        Collections.reverse(list);
                    }
                    boolean first = true;
                    for (JsonObject jo : list) {
                        JsonObject root = (JsonObject) jo.get("message");
                        if (!showHeaders) {
                            root.remove("headers");
                        }
                        if (!showBody) {
                            root.remove("body");
                        }
                        String data
                                = tableHelper.getDataAsTable(root.getString("exchangeId"), root.getString("exchangePattern"),
                                        row.endpoint, root, null);
                        if (data != null) {
                            String[] lines = data.split(System.lineSeparator());
                            if (lines.length > 0) {

                                boolean valid = isValidGrep(data);
                                if (!valid) {
                                    continue;
                                }

                                if (!compact && first) {
                                    System.out.println();
                                }
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
                                    System.out.print(" ");
                                    System.out.println(line);
                                }
                                if (!compact) {
                                    System.out.println();
                                }
                                first = false;
                            }
                        }
                    }
                }
            }
        } else {
            System.out.println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> "" + r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("QUEUE").dataAlign(HorizontalAlign.LEFT).with(r -> r.queue),
                    new Column().header("MAX").dataAlign(HorizontalAlign.RIGHT).with(r -> "" + r.max),
                    new Column().header("TOTAL").dataAlign(HorizontalAlign.RIGHT).with(r -> "" + r.size))));
        }
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                // give time for response to be ready
                Thread.sleep(100);

                if (outputFile.exists()) {
                    FileInputStream fis = new FileInputStream(outputFile);
                    String text = IOHelper.loadText(fis);
                    IOHelper.close(fis);
                    return (JsonObject) Jsoner.deserialize(text);
                }

            } catch (Exception e) {
                // ignore
            }
        }
        return null;
    }

    private static class Row {
        long pid;
        String name;
        String queue;
        int max;
        int size;
        JsonObject endpoint;
        List<JsonObject> messages;
    }

}
