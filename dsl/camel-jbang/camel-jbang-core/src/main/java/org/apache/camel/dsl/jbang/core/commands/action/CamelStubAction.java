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
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.aesh.command.option.OptionList;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.jline.jansi.Ansi;

@CommandDefinition(name = "stub", description = "Browse stub endpoints", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel cmd stub",
                           "  camel cmd stub --browse" })
public class CamelStubAction extends ActionWatchCommand {

    private static final RowSort<Row> ORDER = new RowSort<Row>()
            .text("name", r -> r.queue)
            .numeric("total", r -> r.size);

    @Argument(description = "Name or pid of running Camel integration")
    String name = "*";

    @Option(name = "sort", description = "Sort by name, or total", defaultValue = "name")
    String sort;

    @Option(name = "filter", description = "Filter endpoints by queue name")
    String filter;

    @Option(name = "browse", hasValue = false, description = "Whether to browse messages queued in the stub endpoints")
    boolean browse;

    @Option(name = "top", hasValue = false,
            description = "Whether to browse top (latest) messages queued in the stub endpoints", defaultValue = "true")
    boolean top = true;

    @Option(name = "limit", description = "Filter browsing queues by limiting to the given latest number of messages",
            defaultValue = "10")
    int limit = 10;

    @OptionList(name = "find", valueSeparator = OptionList.NO_SEPARATOR, optionalValue = true,
                description = "Find and highlight matching text (ignore case).")
    String[] find;

    @OptionList(name = "grep", valueSeparator = OptionList.NO_SEPARATOR, optionalValue = true,
                description = "Filter browsing messages to only output trace matching text (ignore case).")
    String[] grep;

    @Option(name = "show-headers", hasValue = false, description = "Show message headers in traced messages",
            defaultValue = "true")
    boolean showHeaders = true;

    @Option(name = "show-body", hasValue = false, description = "Show message body in traced messages", defaultValue = "true")
    boolean showBody = true;

    @Option(name = "compact", hasValue = false, description = "Compact output (no empty line separating browsed messages)",
            defaultValue = "true")
    boolean compact = true;

    @Option(name = "mask", hasValue = false,
            description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @Option(name = "pretty", hasValue = false, description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    @Option(name = "logging-color", hasValue = false, description = "Use colored logging", defaultValue = "true")
    boolean loggingColor = true;

    @Option(name = "json", hasValue = false, description = "Output in JSON Format")
    boolean jsonOutput;

    @Mixin
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
        // no ANSI codes of colors in a file or a pipe, unless --logging-color is given
        loggingColor = colorOutput(loggingColor);
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
        rows.sort(ORDER.comparator(sort));

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
