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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ListingFormat;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@CommandDefinition(name = "producer", description = "Get status of Camel producers", generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel get producer",
                           "  camel get producer --watch" })
public class ListProducer extends ProcessWatchCommand {

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(r -> r.pid, r -> r.name, r -> r.uptime);

    @Argument(description = "Name or pid of running Camel integration")
    String name = "*";

    @Option(name = "sort", description = "Sort by pid, name or age", defaultValue = "pid",
            completer = PidNameAgeCompletionCandidates.class)
    String sort;

    @Option(name = "limit", description = "Filter producers by limiting to the given number of rows")
    int limit;

    @Option(name = "filter", description = "Filter producers by URI")
    String filter;

    @Option(name = "short-uri", hasValue = false, description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @Option(name = "wide-uri", hasValue = false, description = "List endpoint URI in full details")
    boolean wideUri;

    public ListProducer(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        // make it easier to filter
        if (filter != null && !filter.endsWith("*")) {
            filter += "*";
        }

        forEachIntegration(name, (ph, root) -> {
            JsonObject context = (JsonObject) root.get("context");
            JsonObject jo = (JsonObject) root.get("producers");
            if (context != null && jo != null) {
                JsonArray array = (JsonArray) jo.get("producers");
                for (int i = 0; i < array.size(); i++) {
                    JsonObject o = (JsonObject) array.get(i);
                    Row row = new Row();
                    row.name = context.getString("name");
                    if ("CamelJBang".equals(row.name)) {
                        row.name = ProcessHelper.extractName(root, ph);
                    }
                    row.pid = Long.toString(ph.pid());
                    row.uri = o.getString("uri");
                    row.id = o.getString("routeId");
                    row.state = o.getString("state");
                    // the console says clazz (class before Camel 4.23)
                    row.className = o.getStringOrDefault("clazz", o.getString("class"));
                    row.singleton = o.getBoolean("singleton");
                    row.remote = o.getBoolean("remote");
                    row.uptime = extractSince(ph);
                    row.age = TimeUtils.printSince(row.uptime);
                    boolean add = true;
                    if (filter != null) {
                        String f = filter;
                        boolean negate = filter.startsWith("-");
                        if (negate) {
                            f = f.substring(1);
                        }
                        boolean match = PatternHelper.matchPattern(row.uri, f);
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
        });

        // sort rows
        rows.sort(ORDER.comparator(sort));

        // the first rows after sorting
        if (limit > 0 && rows.size() > limit) {
            rows.subList(limit, rows.size()).clear();
        }

        if (!rows.isEmpty() || jsonOutput) {
            printTable(rows);
        }

        return 0;
    }

    protected void printTable(List<Row> rows) {
        if (jsonOutput) {
            printer().println(Jsoner.serialize(rows.stream().map(r -> {
                JsonObject jo = new JsonObject();
                jo.put("pid", JsonValues.pid(r.pid));
                jo.put("name", r.name);
                jo.put("age", r.age);
                jo.put("uptime", r.uptime);
                jo.put("id", r.id);
                jo.put("status", getState(r));
                jo.put("type", getType(r));
                jo.put("uri", r.uri);
                return jo;
            }).collect(Collectors.toList())));
            return;
        }
        // Flexible column: URI (90/140)
        // Fixed columns: PID(8)+NAME(30)+AGE(8)+ID(20)+STATUS(8)+TYPE(20) ~= 94
        int tw = terminalWidth();
        int uriW = TerminalWidthHelper.flexWidth(tw, 94, TerminalWidthHelper.noBorderOverhead(8), 20, 90);
        int uriWideW = TerminalWidthHelper.flexWidth(tw, 94, TerminalWidthHelper.noBorderOverhead(8), 20, 140);
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("ID").dataAlign(HorizontalAlign.LEFT).maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.id),
                new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER).with(this::getState),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(20, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getType),
                new Column().header("URI").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(uriW, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(this::getUri),
                new Column().header("URI").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(uriWideW, OverflowBehaviour.NEWLINE)
                        .with(this::getUri))));
    }

    private String getUri(Row r) {
        return ListingFormat.uri(r.uri, shortUri);
    }

    private String getState(Row r) {
        return r.state;
    }

    private String getType(Row r) {
        String s = r.className;
        if (s == null) {
            return "";
        }
        if (s.endsWith("Producer")) {
            s = s.substring(0, s.length() - 8);
        }
        return s;
    }

    static class Row {
        String pid;
        String name;
        long uptime;
        String age;
        String id;
        String uri;
        String state;
        String className;
        boolean singleton;
        boolean remote;
    }

}
