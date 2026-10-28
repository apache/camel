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
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CopyableRow;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "blocked",
         description = "Get blocked messages of Camel integrations", sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel get blocked",
                 "  camel get blocked --watch" })
public class ListBlocked extends ProcessWatchCommand {

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(r -> r.pid, r -> r.name, r -> r.uptime);

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public ListBlocked(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        forEachIntegration(name, (ph, root) -> {
            // there must be a status file for the running Camel integration
            Row row = new Row();
            JsonObject context = (JsonObject) root.get("context");
            if (context == null) {
                return;
            }
            row.name = context.getString("name");
            if ("CamelJBang".equals(row.name)) {
                row.name = ProcessHelper.extractName(root, ph);
            }
            row.pid = Long.toString(ph.pid());
            row.uptime = extractSince(ph);
            row.age = TimeUtils.printSince(row.uptime);

            JsonObject jo = (JsonObject) root.get("blocked");
            if (jo != null) {
                JsonArray arr = (JsonArray) jo.get("exchanges");
                if (arr != null) {
                    for (int i = 0; i < arr.size(); i++) {
                        row = row.copy();
                        jo = (JsonObject) arr.get(i);
                        row.exchangeId = jo.getString("exchangeId");
                        row.routeId = jo.getString("routeId");
                        row.nodeId = jo.getString("nodeId");
                        row.duration = jo.getLong("duration");
                        rows.add(row);
                    }
                }
            }
        });

        // sort rows
        rows.sort(ORDER.comparator(sort));

        if (!rows.isEmpty() || jsonOutput) {
            if (jsonOutput) {
                printer().println(Jsoner.serialize(rows.stream().map(r -> {
                    JsonObject jo = new JsonObject();
                    jo.put("pid", JsonValues.pid(r.pid));
                    jo.put("name", r.name);
                    jo.put("exchangeId", r.exchangeId);
                    jo.put("routeId", r.routeId);
                    jo.put("id", r.nodeId);
                    jo.put("duration", r.duration);
                    return jo;
                }).collect(Collectors.toList())));
            } else {
                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                        new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.name),
                        new Column().header("EXCHANGE-ID").dataAlign(HorizontalAlign.LEFT).with(r -> r.exchangeId),
                        new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.routeId),
                        new Column().header("ID").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.nodeId),
                        new Column().header("DURATION").dataAlign(HorizontalAlign.RIGHT).with(this::getDuration))));
            }
        }

        return 0;
    }

    private String getDuration(Row r) {
        return TimeUtils.printDuration(r.duration);
    }

    private static class Row extends CopyableRow<Row> {
        String pid;
        String name;
        String age;
        long uptime;
        String exchangeId;
        String routeId;
        String nodeId;
        long duration;
    }

}
