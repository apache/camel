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
import java.util.Locale;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "integration", aliases = { "int", "integration", "integrations", "context" },
         description = "Get status of Camel integrations")
public class CamelContextStatus extends ProcessBaseCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" },
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    public CamelContextStatus(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer call() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .sorted((o1, o2) -> {
                    switch (sort) {
                        case "pid":
                            return Long.compare(o1.pid(), o2.pid());
                        case "name":
                            return extractName(o1).compareTo(extractName(o2));
                        case "age":
                            // we want newest in top
                            return Long.compare(extractSince(o1), extractSince(o2)) * -1;
                        default:
                            return 0;
                    }
                })
                .forEach(ph -> {
                    Row row = new Row();
                    row.name = extractName(ph);
                    if (ObjectHelper.isNotEmpty(row.name)) {
                        row.pid = "" + ph.pid();
                        row.ago = TimeUtils.printSince(extractSince(ph));
                        JsonObject status = loadStatus(ph.pid());
                        if (status != null) {
                            status = (JsonObject) status.get("context");
                            row.state = status.getString("state").toLowerCase(Locale.ROOT);
                            Map<String, ?> stats = status.getMap("statistics");
                            if (stats != null) {
                                row.total = stats.get("exchangesTotal").toString();
                                row.inflight = stats.get("exchangesInflight").toString();
                                row.failed = stats.get("exchangesFailed").toString();
                                Object last = stats.get("sinceLastExchange");
                                if (last != null) {
                                    row.sinceLast = last.toString();
                                }
                            }
                        }
                        rows.add(row);
                    }
                });

        if (!rows.isEmpty()) {
            System.out.println(AsciiTable.getTable(AsciiTable.BASIC_ASCII_NO_DATA_SEPARATORS, rows, Arrays.asList(
                    new Column().header("PID").with(r -> r.pid),
                    new Column().header("Name").dataAlign(HorizontalAlign.LEFT).maxColumnWidth(30)
                            .with(r -> maxWidth(r.name, 28)),
                    new Column().header("State").with(r -> r.state),
                    new Column().header("Uptime").with(r -> r.ago),
                    new Column().header("Total #").with(r -> r.total),
                    new Column().header("Failed #").with(r -> r.failed),
                    new Column().header("Inflight #").with(r -> r.inflight),
                    new Column().header("Since Last").with(r -> r.sinceLast))));
        }

        return 0;
    }

    private JsonObject loadStatus(long pid) {
        try {
            File f = getStatusFile("" + pid);
            if (f != null) {
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

    private static class Row {
        String pid;
        String name;
        String ago;
        String state;
        String total;
        String failed;
        String inflight;
        String sinceLast;
    }

}
