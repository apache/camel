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
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "datasource", description = "Get status of DataSource connection pools", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel get datasource",
                 "  camel get datasource --watch" })
public class ListDataSource extends ProcessWatchCommand {

    private static final RowSort<Row> ORDER = RowSort.<Row> integration(r -> r.pid, r -> r.name, r -> r.uptime);

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public ListDataSource(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        forEachIntegration(name, (ph, root) -> {
            JsonObject context = (JsonObject) root.get("context");
            JsonObject ds = (JsonObject) root.get("dataSources");
            if (context != null && ds != null) {
                JsonArray array = (JsonArray) ds.get("dataSources");
                if (array != null) {
                    for (int i = 0; i < array.size(); i++) {
                        JsonObject o = (JsonObject) array.get(i);
                        Row row = new Row();
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        row.pid = Long.toString(ph.pid());
                        row.uptime = extractSince(ph);
                        row.age = TimeUtils.printSince(row.uptime);
                        row.dsName = o.getString("name");
                        row.type = o.getString("type");
                        row.poolType = o.getString("poolType");
                        row.poolName = o.getString("poolName");
                        row.active = o.getIntegerOrDefault("active", 0);
                        row.idle = o.getIntegerOrDefault("idle", 0);
                        row.total = o.getIntegerOrDefault("total", 0);
                        row.maxPoolSize = o.getIntegerOrDefault("maxPoolSize", 0);
                        row.waiting = o.getIntegerOrDefault("waiting", 0);
                        rows.add(row);
                    }
                }
            }
        });

        // sort rows
        rows.sort(ORDER.comparator(sort));

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
                jo.put("dsName", r.dsName);
                jo.put("poolType", r.poolType);
                if (r.poolName != null) {
                    jo.put("poolName", r.poolName);
                }
                jo.put("active", r.active);
                jo.put("idle", r.idle);
                jo.put("total", r.total);
                jo.put("maxPoolSize", r.maxPoolSize);
                jo.put("waiting", r.waiting);
                jo.put("type", r.type);
                return jo;
            }).collect(Collectors.toList())));
            return;
        }
        int tw = terminalWidth();
        int typeW = Math.max(20, Math.min(60, tw - 100));
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.name),
                new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.age),
                new Column().header("DS-NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.dsName),
                new Column().header("POOL").dataAlign(HorizontalAlign.LEFT)
                        .with(r -> r.poolName != null ? r.poolName : (r.poolType != null ? r.poolType : "")),
                new Column().header("ACTIVE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Integer.toString(r.active)),
                new Column().header("IDLE").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Integer.toString(r.idle)),
                new Column().header("TOTAL").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Integer.toString(r.total)),
                new Column().header("MAX").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> r.maxPoolSize > 0 ? Integer.toString(r.maxPoolSize) : ""),
                new Column().header("WAITING").headerAlign(HorizontalAlign.RIGHT).dataAlign(HorizontalAlign.RIGHT)
                        .with(r -> Integer.toString(r.waiting)),
                new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT)
                        .maxWidth(typeW, OverflowBehaviour.ELLIPSIS_RIGHT)
                        .with(r -> r.type))));
    }

    static class Row {
        String pid;
        String name;
        long uptime;
        String age;
        String dsName;
        String type;
        String poolType;
        String poolName;
        int active;
        int idle;
        int total;
        int maxPoolSize;
        int waiting;
    }
}
