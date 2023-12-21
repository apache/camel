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

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "vault",
         description = "List secrets from security vaults used by running Camel integrations", sortOptions = false)
public class ListVault extends ProcessWatchCommand {

    public static class PidNameCompletionCandidates implements Iterable<String> {

        public PidNameCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name").iterator();
        }

    }

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameCompletionCandidates.class,
                        description = "Sort by pid, name", defaultValue = "pid")
    String sort;

    public ListVault(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids("*");
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    if (root != null) {
                        Row row = new Row();
                        row.pid = Long.toString(ph.pid());
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        row.name = context.getString("name");
                        if ("CamelJBang".equals(row.name)) {
                            row.name = ProcessHelper.extractName(root, ph);
                        }
                        JsonObject vaults = (JsonObject) root.get("vaults");
                        if (vaults != null) {
                            JsonObject aws = (JsonObject) vaults.get("aws-secrets");
                            if (aws != null) {
                                row.vault = "AWS";
                                row.region = aws.getString("region");
                                row.lastCheck = aws.getLongOrDefault("lastCheckTimestamp", 0);
                                row.lastReload = aws.getLongOrDefault("lastReloadTimestamp", 0);
                                JsonArray arr = (JsonArray) aws.get("secrets");
                                for (int i = 0; i < arr.size(); i++) {
                                    if (i > 0) {
                                        // create a copy for 2+ secrets
                                        row = row.copy();
                                    }
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.secret = jo.getString("name");
                                    row.timestamp = jo.getLongOrDefault("timestamp", 0);
                                    rows.add(row);
                                }
                            }
                            JsonObject gcp = (JsonObject) vaults.get("gcp-secrets");
                            if (gcp != null) {
                                row.vault = "GCP";
                                row.lastCheck = gcp.getLongOrDefault("lastCheckTimestamp", 0);
                                row.lastReload = gcp.getLongOrDefault("lastReloadTimestamp", 0);
                                JsonArray arr = (JsonArray) gcp.get("secrets");
                                for (int i = 0; i < arr.size(); i++) {
                                    if (i > 0) {
                                        // create a copy for 2+ secrets
                                        row = row.copy();
                                    }
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.secret = jo.getString("name");
                                    row.timestamp = jo.getLongOrDefault("timestamp", 0);
                                    rows.add(row);
                                }
                            }
                            JsonObject azure = (JsonObject) vaults.get("azure-secrets");
                            if (azure != null) {
                                row.vault = "Azure";
                                row.lastCheck = azure.getLongOrDefault("lastCheckTimestamp", 0);
                                row.lastReload = azure.getLongOrDefault("lastReloadTimestamp", 0);
                                JsonArray arr = (JsonArray) azure.get("secrets");
                                for (int i = 0; i < arr.size(); i++) {
                                    if (i > 0) {
                                        // create a copy for 2+ secrets
                                        row = row.copy();
                                    }
                                    JsonObject jo = (JsonObject) arr.get(i);
                                    row.secret = jo.getString("name");
                                    row.timestamp = jo.getLongOrDefault("timestamp", 0);
                                    rows.add(row);
                                }
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("VAULT").dataAlign(HorizontalAlign.LEFT).with(r -> r.vault),
                    new Column().header("REGION").dataAlign(HorizontalAlign.LEFT).with(r -> r.region),
                    new Column().header("SECRET").dataAlign(HorizontalAlign.LEFT).maxWidth(40, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.secret),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(this::getAgo),
                    new Column().header("UPDATE").headerAlign(HorizontalAlign.LEFT).with(this::getReloadAgo),
                    new Column().header("CHECK").headerAlign(HorizontalAlign.LEFT).with(this::getCheckAgo))));
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
            case "pid":
                return Long.compare(Long.parseLong(o1.pid), Long.parseLong(o2.pid)) * negate;
            case "name":
                return o1.name.compareToIgnoreCase(o2.name) * negate;
            default:
                return 0;
        }
    }

    private String getCheckAgo(Row r) {
        if (r.lastCheck == 0) {
            return "";
        }
        return TimeUtils.printSince(r.lastCheck);
    }

    private String getReloadAgo(Row r) {
        if (r.lastReload == 0) {
            return "";
        }
        return TimeUtils.printSince(r.lastReload);
    }

    private String getAgo(Row r) {
        if (r.timestamp == 0) {
            return "";
        }
        return TimeUtils.printSince(r.timestamp);
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        String vault;
        String region;
        long lastCheck;
        long lastReload;
        String secret;
        long timestamp;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }

    }

}
