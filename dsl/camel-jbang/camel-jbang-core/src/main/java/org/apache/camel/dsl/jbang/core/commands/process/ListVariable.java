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
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CopyableRow;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "variable", description = "List variables of Camel integrations", sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel get variable",
                 "  camel get variable --watch" })
public class ListVariable extends ProcessWatchCommand {

    private static final RowSort<Row> ORDER = new RowSort<Row>()
            .number("pid", r -> r.pid)
            .text("name", r -> r.name)
            .text("key", r -> r.key);

    public static class PidNameKeyCompletionCandidates implements Iterable<String> {

        public PidNameKeyCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name", "key").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameKeyCompletionCandidates.class,
                        description = "Sort by pid, name or key", defaultValue = "pid")
    String sort;

    public ListVariable(CamelJBangMain main) {
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

            JsonObject jv = (JsonObject) root.get("variables");
            if (jv != null) {
                JsonArray repositories = jv.getCollection("repositories");
                if (repositories != null) {
                    for (Object r : repositories) {
                        JsonObject repo = (JsonObject) r;
                        String id = repo.getString("id");
                        JsonArray arr = repo.getCollection("variables");
                        if (arr != null) {
                            for (int i = 0; i < arr.size(); i++) {
                                row = row.copy();
                                JsonObject jo = (JsonObject) arr.get(i);
                                row.id = id;
                                row.key = jo.getString("key");
                                row.type = jo.getString("type");
                                row.value = jo.get("value");
                                rows.add(row);
                            }
                        }
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
                    jo.put("repo", r.id);
                    jo.put("type", r.type);
                    jo.put("key", r.key);
                    jo.put("value", getValue(r));
                    return jo;
                }).collect(Collectors.toList())));
            } else {
                // Flexible columns: TYPE (40), KEY (50), VALUE (80)
                // Fixed columns: PID(8)+NAME(30)+REPO(8) ~= 46
                int tw = terminalWidth();
                int typeW = TerminalWidthHelper.flexWidth(tw, 46 + 50 + 80, TerminalWidthHelper.noBorderOverhead(6), 15, 40);
                int keyW = TerminalWidthHelper.flexWidth(tw, 46 + 40 + 80, TerminalWidthHelper.noBorderOverhead(6), 15, 50);
                int valW = TerminalWidthHelper.flexWidth(tw, 46 + 40 + 50, TerminalWidthHelper.noBorderOverhead(6), 20, 80);
                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                        new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                        new Column().header("NAME").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.name),
                        new Column().header("REPO").headerAlign(HorizontalAlign.CENTER).with(r -> r.id),
                        new Column().header("TYPE").headerAlign(HorizontalAlign.CENTER)
                                .maxWidth(typeW, OverflowBehaviour.ELLIPSIS_LEFT).with(r -> r.type),
                        new Column().header("KEY").dataAlign(HorizontalAlign.LEFT)
                                .maxWidth(keyW, OverflowBehaviour.ELLIPSIS_RIGHT)
                                .with(r -> r.key),
                        new Column().header("VALUE").headerAlign(HorizontalAlign.RIGHT)
                                .maxWidth(valW, OverflowBehaviour.NEWLINE)
                                .with(this::getValue))));
            }
        }

        return 0;
    }

    private String getValue(Row r) {
        if (r.value != null) {
            // clip very large value
            String s = r.value.toString();
            if (s.length() > 300) {
                s = s.substring(0, 300) + "...";
            }
            return s;
        }
        return "";
    }

    private static class Row extends CopyableRow<Row> {
        String pid;
        String name;
        String id;
        String key;
        String type;
        Object value;
    }

}
