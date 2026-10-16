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
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.jline.jansi.Ansi;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "stub", description = "Browse stub endpoints", sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel cmd stub",
                 "  camel cmd stub --browse" })
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

    @CommandLine.Option(names = { "--top" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
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

    @CommandLine.Option(names = { "--show-headers" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Show message headers in traced messages")
    boolean showHeaders = true;

    @CommandLine.Option(names = { "--show-body" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Show message body in traced messages")
    boolean showBody = true;

    @CommandLine.Option(names = { "--compact" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Compact output (no empty line separating browsed messages)")
    boolean compact = true;

    @CommandLine.Option(names = { "--mask" },
                        description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    private volatile long pid;
    String findAnsi;
    Pattern[] grepPatterns;
    Pattern[] findPatterns;
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
        // once, as the patterns are quoted in place, and --watch calls this again
        if (find != null && findPatterns == null) {
            findAnsi = Ansi.ansi().fg(Ansi.Color.BLACK).bg(Ansi.Color.YELLOW).a("$0").reset().toString();
            findPatterns = quoteAndCompilePatterns(find);
        }
        if (grep != null && grepPatterns == null) {
            findAnsi = Ansi.ansi().fg(Ansi.Color.BLACK).bg(Ansi.Color.YELLOW).a("$0").reset().toString();
            grepPatterns = quoteAndCompilePatterns(grep);
        }

        List<Row> rows = new ArrayList<>();

        CamelProcess process = findOne(name);
        this.pid = process.pid();

        if (filter == null) {
            filter = "*";
        }

        JsonObject jo = executeAction(process, "stub", root -> {
            root.put("format", "json");
            root.put("browse", browse);
            root.put("filter", "*");
            root.put("limit", limit);
        });
        String integration = process.name();

        JsonArray arr = (JsonArray) jo.get("queues");
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = (JsonObject) arr.get(i);
                Row row = new Row();
                row.pid = pid;
                row.name = integration;
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
                if (filter != null && !matchesFilter(filter, row.queue)) {
                    add = false;
                }
                if (add) {
                    rows.add(row);
                }
            }
        }

        // sort rows
        rows.sort(this::sortRow);

        if (jsonOutput) {
            JsonOutput.printList(printer(), rows.stream().map(r -> {
                JsonObject queue = new JsonObject();
                queue.put("pid", r.pid);
                queue.put("name", r.name);
                queue.put("queue", r.queue);
                queue.put("max", r.max);
                queue.put("size", r.size);
                if (r.endpoint != null) {
                    queue.put("endpointUri", r.endpoint.getString("endpoint"));
                }
                if (r.messages != null) {
                    queue.put("messages", r.messages);
                }
                return queue;
            }).toList());
            return 0;
        }
        if (watch) {
            clearScreen();
        }
        if (!rows.isEmpty()) {
            printStub(rows);
        }

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
                String q1 = o1.queue != null ? o1.queue : "";
                String q2 = o2.queue != null ? o2.queue : "";
                return q1.compareToIgnoreCase(q2) * negate;
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
        for (Pattern p : grepPatterns) {
            if (p.matcher(line).find()) {
                return true;
            }
        }
        return false;
    }

    protected void printStub(List<Row> rows) {
        if (browse) {
            for (Row row : rows) {
                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, List.of(row), Arrays.asList(
                        new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> Long.toString(r.pid)),
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.name),
                        new Column().header("QUEUE").dataAlign(HorizontalAlign.LEFT).with(r -> r.queue),
                        new Column().header("MAX").dataAlign(HorizontalAlign.RIGHT).with(r -> Integer.toString(r.max)),
                        new Column().header("TOTAL").dataAlign(HorizontalAlign.RIGHT).with(r -> Integer.toString(r.size)))));

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
                                        null, row.endpoint, null, root, null);
                        if (data != null) {
                            String[] lines = data.split(System.lineSeparator());
                            if (lines.length > 0) {

                                boolean valid = isValidGrep(data);
                                if (!valid) {
                                    continue;
                                }

                                if (!compact && first) {
                                    printer().println();
                                }
                                for (String line : lines) {
                                    if (findPatterns != null) {
                                        for (Pattern p : findPatterns) {
                                            line = p.matcher(line).replaceAll(findAnsi);
                                        }
                                    }
                                    if (grepPatterns != null) {
                                        for (Pattern p : grepPatterns) {
                                            line = p.matcher(line).replaceAll(findAnsi);
                                        }
                                    }
                                    printer().print(" ");
                                    printer().println(line);
                                }
                                if (!compact) {
                                    printer().println();
                                }
                                first = false;
                            }
                        }
                    }
                }
            }
        } else {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> Long.toString(r.pid)),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("QUEUE").dataAlign(HorizontalAlign.LEFT).with(r -> r.queue),
                    new Column().header("MAX").dataAlign(HorizontalAlign.RIGHT).with(r -> Integer.toString(r.max)),
                    new Column().header("TOTAL").dataAlign(HorizontalAlign.RIGHT).with(r -> Integer.toString(r.size)))));
        }
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
