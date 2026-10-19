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
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CopyableRow;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ListingFormat;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.URISupport;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.jline.jansi.Ansi;
import picocli.CommandLine;

@CommandLine.Command(name = "browse",
                     description = "Browse pending messages on endpoints", sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel cmd browse --endpoint=seda:foo",
                             "  camel cmd browse --endpoint=seda:foo --limit=10" })
public class CamelBrowseAction extends ActionBaseCommand {

    private static final RowSort<Row> ORDER = new RowSort<Row>()
            .text("uri", r -> r.uri)
            .numeric("size", r -> r.queueSize);

    public static class UriSizeCompletionCandidates implements Iterable<String> {

        public UriSizeCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("uri", "size").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--endpoint" },
                        description = "Endpoint to browse messages from (can be uri, pattern, or refer to a route id)")
    String endpoint;

    @CommandLine.Option(names = { "--short-uri" },
                        description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @CommandLine.Option(names = { "--wide-uri" },
                        description = "List endpoint URI in full details")
    boolean wideUri;

    @CommandLine.Option(names = { "--mask" },
                        description = "Whether to mask endpoint URIs to avoid printing sensitive information such as password or access keys")
    boolean mask;

    @CommandLine.Option(names = { "--dump" }, defaultValue = "false",
                        description = "Whether to include message dumps")
    boolean dump;

    @CommandLine.Option(names = { "--limit" }, defaultValue = "100",
                        description = "Limits the number of messages to dump per endpoint")
    int limit;

    @CommandLine.Option(names = { "--tail" },
                        description = "The number of messages from the end (latest) to dump")
    int tail;

    @CommandLine.Option(names = { "--fresh-size" }, defaultValue = "false",
                        description = "Whether to calculate fresh queue size information (performance overhead)")
    boolean freshSize;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = UriSizeCompletionCandidates.class,
                        description = "Sort by uri, or size", defaultValue = "uri")
    String sort;

    @CommandLine.Option(names = { "--show-headers" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Show message headers in browsed messages")
    boolean showHeaders = true;

    @CommandLine.Option(names = { "--show-body" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Show message body in browsed messages")
    boolean showBody = true;

    @CommandLine.Option(names = { "--only-body" }, defaultValue = "false",
                        description = "Show only message body in browsed messages")
    boolean onlyBody;

    @CommandLine.Option(names = { "--body-max-chars" },
                        description = "Maximum size of the message body to include in the dump")
    int bodyMaxChars;

    @CommandLine.Option(names = { "--logging-color" }, defaultValue = "true", arity = "0..1", fallbackValue = "true",
                        description = "Use colored logging")
    boolean loggingColor = true;

    @CommandLine.Option(names = { "--pretty" },
                        description = "Pretty print message body when using JSon or XML format")
    boolean pretty;

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    private volatile long pid;

    public CamelBrowseAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        CamelProcess process = findOne(name);
        this.pid = process.pid();

        JsonObject jo = executeAction(process, "browse", root -> {
            root.put("filter", endpoint == null ? "*" : endpoint);
            root.put("limit", limit);
            root.put("tail", tail);
            root.put("freshSize", freshSize);
            root.put("dump", dump);
            root.put("includeBody", showBody);
            if (bodyMaxChars > 0) {
                root.put("bodyMaxChars", bodyMaxChars);
            }
        });

        List<Row> rows = new ArrayList<>();
        Row row = new Row();
        row.pid = Long.toString(this.pid);
        ProcessHandle ph = ProcessHandle.of(this.pid).orElse(null);
        row.name = process.name();
        row.uptime = extractSince(ph);
        row.ago = TimeUtils.printSince(row.uptime);
        JsonArray arr = jo.getCollection("browse");
        for (int i = 0; arr != null && i < arr.size(); i++) {
            JsonObject o = (JsonObject) arr.get(i);
            row.uri = o.getString("endpointUri");
            if (mask) {
                row.uri = URISupport.sanitizeUri(row.uri);
            }
            row.queueSize = o.getInteger("queueSize");
            row.limit = o.getIntegerOrDefault("limit", 0);
            row.position = o.getIntegerOrDefault("position", 0);
            row.firstTimestamp = o.getLongOrDefault("firstTimestamp", 0);
            row.lastTimestamp = o.getLongOrDefault("lastTimestamp", 0);
            if (dump) {
                row.messages = o.getCollection("messages");
            }
            rows.add(row);
            row = row.copy();
        }

        if (endpoint != null && rows.isEmpty()) {
            // the endpoint asked for is not one the integration can browse
            throw CliException.notFound("No browsable endpoint matches: " + endpoint);
        }

        // sort rows
        rows.sort(ORDER.comparator(sort));

        if (jsonOutput) {
            JsonOutput.printList(printer(), rows.stream().map(r -> {
                JsonObject jo2 = new JsonObject();
                jo2.put("pid", r.pid);
                jo2.put("name", r.name);
                jo2.put("age", r.ago);
                jo2.put("endpointUri", r.uri);
                jo2.put("queueSize", r.queueSize);
                jo2.put("limit", r.limit);
                jo2.put("position", r.position);
                jo2.put("firstTimestamp", r.firstTimestamp);
                jo2.put("lastTimestamp", r.lastTimestamp);
                if (r.messages != null) {
                    jo2.put("messages", r.messages);
                }
                return jo2;
            }).toList());
        } else if (dump) {
            dumpMessages(rows, onlyBody);
        } else {
            tableStatus(rows);
        }

        return 0;
    }

    protected void dumpMessages(List<Row> rows, boolean onlyBody) {
        MessageTableHelper tableHelper = new MessageTableHelper();
        tableHelper.setPretty(pretty);
        tableHelper.setLoggingColor(loggingColor);
        tableHelper.setShowExchangeProperties(false);

        for (Row row : rows) {
            if (row.messages != null) {
                for (int i = 0; i < row.messages.size(); i++) {
                    JsonObject jo = row.messages.get(i);
                    String exchangeId = jo.getString("exchangeId");
                    JsonObject message = jo.getMap("message");
                    if (onlyBody) {
                        exchangeId = null;
                        message.remove("headers");
                        message.remove("messageType");
                    } else {
                        if (!showHeaders && message != null) {
                            message.remove("headers");
                        }
                        if (!showBody && message != null) {
                            message.remove("body");
                        }
                    }
                    if (mask) {
                        row.uri = URISupport.sanitizeUri(row.uri);
                    }
                    JsonObject ep = new JsonObject();
                    ep.put("endpoint", row.uri);
                    String table = tableHelper.getDataAsTable(exchangeId, null, null, ep, null, message, null);
                    String header = String.format("Browse Message: (%s/%s)", row.position + i + 1,
                            row.position + row.messages.size());
                    if (loggingColor) {
                        printer().println(Ansi.ansi().fgGreen().a(header).reset().toString());
                    } else {
                        printer().println(header);
                    }
                    printer().println(table);
                }
            }
        }
    }

    protected void tableStatus(List<Row> rows) {
        int tw = terminalWidth();
        int fixedWidth = 10 + 40 + 10 + 8 + 15; // PID + NAME + AGE + SIZE + SINCE (approx)
        int borderOverhead = TerminalWidthHelper.noBorderOverhead(6);
        int endpointWidth = TerminalWidthHelper.flexWidth(tw, fixedWidth, borderOverhead, 20, wideUri ? 140 : 90);

        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.ago),
                new Column().header("SIZE").headerAlign(HorizontalAlign.RIGHT).with(this::getQueueSize),
                new Column().header("SINCE").headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.LEFT)
                        .with(this::getMessageAgo),
                new Column().header("ENDPOINT").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(endpointWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getEndpointUri),
                new Column().header("ENDPOINT").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(endpointWidth, OverflowBehaviour.NEWLINE)
                        .with(r -> r.uri))));
    }

    protected String getMessageAgo(Row r) {
        StringJoiner sj = new StringJoiner("/");
        if (r.firstTimestamp > 0) {
            sj.add(TimeUtils.printSince(r.firstTimestamp));
        }
        if (r.lastTimestamp > 0) {
            sj.add(TimeUtils.printSince(r.lastTimestamp));
        }
        return sj.toString();
    }

    protected String getQueueSize(Row r) {
        if (freshSize) {
            return "" + r.queueSize;
        }
        if (r.limit > 0 && r.queueSize >= r.limit) {
            return r.queueSize + "+";
        }
        return "" + r.queueSize;
    }

    protected String getEndpointUri(Row r) {
        return ListingFormat.uri(r.uri, shortUri);
    }

    private static class Row extends CopyableRow<Row> {
        String pid;
        String name;
        String ago;
        long uptime;
        String uri;
        int queueSize;
        int limit;
        int position;
        long firstTimestamp;
        long lastTimestamp;
        List<JsonObject> messages;
    }

}
