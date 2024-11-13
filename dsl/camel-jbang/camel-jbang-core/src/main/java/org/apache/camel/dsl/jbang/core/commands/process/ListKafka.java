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

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "kafka",
         description = "List Kafka consumers of Camel integrations", sortOptions = false, showDefaultValues = true)
public class ListKafka extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameAgeCompletionCandidates.class,
                        description = "Sort by pid, name or age", defaultValue = "pid")
    String sort;

    @CommandLine.Option(names = { "--committed" },
                        description = "Show committed offset (slower due to sync call to Kafka brokers)")
    boolean committed;

    @CommandLine.Option(names = { "--short-uri" },
                        description = "List endpoint URI without query parameters (short)")
    boolean shortUri;

    @CommandLine.Option(names = { "--wide-uri" },
                        description = "List endpoint URI in full details")
    boolean wideUri;

    public ListKafka(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected void autoClearScreen() {
        // do not auto clear as can be slow when getting committed details
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses()
                .filter(ph -> pids.contains(ph.pid()))
                .forEach(ph -> {
                    JsonObject root = loadStatus(ph.pid());
                    // there must be a status file for the running Camel integration
                    if (root != null) {
                        Row copy = new Row();
                        JsonObject context = (JsonObject) root.get("context");
                        if (context == null) {
                            return;
                        }
                        copy.name = context.getString("name");
                        if ("CamelJBang".equals(copy.name)) {
                            copy.name = ProcessHelper.extractName(root, ph);
                        }
                        copy.pid = Long.toString(ph.pid());
                        copy.uptime = extractSince(ph);
                        copy.age = TimeUtils.printSince(copy.uptime);

                        JsonObject jo = (JsonObject) root.get("kafka");
                        if (jo != null) {
                            if (committed) {
                                // we ask for committed so need to do an action on-demand to get data
                                // ensure output file is deleted before executing action
                                File outputFile = getOutputFile(Long.toString(ph.pid()));
                                FileUtil.deleteFile(outputFile);

                                JsonObject root2 = new JsonObject();
                                root2.put("action", "kafka");
                                File file = getActionFile(Long.toString(ph.pid()));
                                try {
                                    IOHelper.writeText(root2.toJson(), file);
                                } catch (Exception e) {
                                    // ignore
                                }
                                jo = waitForOutputFile(outputFile);
                            }
                            JsonArray arr = jo != null ? (JsonArray) jo.get("kafkaConsumers") : null;
                            if (arr != null) {
                                for (int i = 0; i < arr.size(); i++) {
                                    Row row = copy.copy();
                                    jo = (JsonObject) arr.get(i);
                                    row.routeId = jo.getString("routeId");
                                    row.uri = jo.getString("uri");
                                    row.state = jo.getString("state");

                                    JsonArray wa = (JsonArray) jo.get("workers");
                                    if (wa != null) {
                                        for (int j = 0; j < wa.size(); j++) {
                                            JsonObject wo = (JsonObject) wa.get(j);
                                            row.threadId = wo.getString("threadId");
                                            row.state = wo.getString("state");
                                            row.lastError = wo.getString("lastError");
                                            row.groupId = wo.getString("groupId");
                                            row.groupInstanceId = wo.getString("groupInstanceId");
                                            row.memberId = wo.getString("memberId");
                                            row.generationId = wo.getIntegerOrDefault("generationId", 0);
                                            row.lastTopic = wo.getString("lastTopic");
                                            row.lastPartition = wo.getIntegerOrDefault("lastPartition", 0);
                                            row.lastOffset = wo.getLongOrDefault("lastOffset", 0);
                                            if (committed) {
                                                JsonArray ca = (JsonArray) wo.get("committed");
                                                if (ca != null) {
                                                    JsonObject found = null;
                                                    for (int k = 0; k < ca.size(); k++) {
                                                        JsonObject co = (JsonObject) ca.get(k);
                                                        if (row.lastTopic == null
                                                                || (row.lastTopic.equals(co.getString("topic"))
                                                                        && row.lastPartition == co.getInteger("partition"))) {
                                                            found = co;
                                                            break;
                                                        }
                                                    }
                                                    if (found != null) {
                                                        row.lastTopic = found.getString("topic");
                                                        row.lastPartition = found.getIntegerOrDefault("partition", 0);
                                                        row.committedOffset = found.getLongOrDefault("offset", 0);
                                                        row.committedEpoch = found.getLongOrDefault("epoch", 0);
                                                    }
                                                }
                                            }
                                            rows.add(row);
                                            row = row.copy();
                                        }
                                    } else {
                                        rows.add(row);
                                    }
                                }
                            }
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        // clear before writing new data
        if (watch) {
            clearScreen();
        }

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT).with(this::getRouteId),
                    new Column().header("STATE").dataAlign(HorizontalAlign.LEFT).with(this::getState),
                    new Column().header("GROUP-ID").dataAlign(HorizontalAlign.LEFT).with(r -> r.groupId),
                    new Column().header("TOPIC").dataAlign(HorizontalAlign.RIGHT).with(r -> r.lastTopic),
                    new Column().header("PARTITION").dataAlign(HorizontalAlign.RIGHT).with(r -> "" + r.lastPartition),
                    new Column().header("OFFSET").dataAlign(HorizontalAlign.RIGHT).with(r -> "" + r.lastOffset),
                    new Column().header("COMMITTED").visible(committed).dataAlign(HorizontalAlign.RIGHT)
                            .with(this::getCommitted),
                    new Column().header("ERROR").dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(60, OverflowBehaviour.NEWLINE)
                            .with(this::getLastError),
                    new Column().header("ENDPOINT").visible(!wideUri).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(90, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(this::getUri),
                    new Column().header("ENDPOINT").visible(wideUri).dataAlign(HorizontalAlign.LEFT)
                            .maxWidth(140, OverflowBehaviour.NEWLINE)
                            .with(this::getUri))));
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
            case "age":
                return Long.compare(o1.uptime, o2.uptime) * negate;
            default:
                return 0;
        }
    }

    private String getRouteId(Row r) {
        if (r.routeId != null) {
            return r.routeId;
        }
        return "";
    }

    private String getLastError(Row r) {
        if (r.lastError != null) {
            return r.lastError;
        }
        return "";
    }

    private String getUri(Row r) {
        String u = r.uri;
        if (shortUri) {
            int pos = u.indexOf('?');
            if (pos > 0) {
                u = u.substring(0, pos);
            }
        }
        return u;
    }

    private String getMetadata(Row r) {
        return r.groupId;
    }

    private String getState(Row r) {
        return StringHelper.capitalize(r.state.toLowerCase(Locale.ROOT));
    }

    private String getCommitted(Row r) {
        if (r.committedEpoch > 0) {
            String age = TimeUtils.printSince(r.committedEpoch);
            return r.committedOffset + " (" + age + ")";
        } else {
            return "" + r.committedOffset;
        }
    }

    private static class Row implements Cloneable {
        String pid;
        String name;
        String age;
        long uptime;
        String routeId;
        String uri;
        String threadId;
        String state;
        String lastError;
        String groupId;
        String groupInstanceId;
        String memberId;
        int generationId;
        String lastTopic;
        int lastPartition;
        long lastOffset;
        long committedOffset;
        long committedEpoch;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                return null;
            }
        }
    }

    private JsonObject waitForOutputFile(File outputFile) {
        JsonObject answer = null;

        StopWatch watch = new StopWatch();
        while (watch.taken() < 10000 && answer == null) {
            try {
                // give time for response to be ready
                Thread.sleep(100);

                if (outputFile.exists()) {
                    FileInputStream fis = new FileInputStream(outputFile);
                    String text = IOHelper.loadText(fis);
                    IOHelper.close(fis);
                    answer = (JsonObject) Jsoner.deserialize(text);
                }
            } catch (Exception e) {
                // ignore
            }
        }
        return answer;
    }

}
