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

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.PidNameAgeCompletionCandidates;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "internal-tasks",
        description = "List internal tasks of Camel integrations",
        sortOptions = false,
        showDefaultValues = true)
public class ListInternalTask extends ProcessWatchCommand {

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(
            names = {"--sort"},
            completionCandidates = PidNameAgeCompletionCandidates.class,
            description = "Sort by pid, name or age",
            defaultValue = "pid")
    String sort;

    public ListInternalTask(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doProcessWatchCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        ProcessHandle.allProcesses().filter(ph -> pids.contains(ph.pid())).forEach(ph -> {
            JsonObject root = loadStatus(ph.pid());
            // there must be a status file for the running Camel integration
            if (root != null) {
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

                JsonObject jo = (JsonObject) root.get("internal-tasks");
                if (jo != null) {
                    JsonArray arr = (JsonArray) jo.get("tasks");
                    for (int i = 0; i < arr.size(); i++) {
                        jo = (JsonObject) arr.get(i);
                        row = row.copy();
                        row.task = jo.getString("name");
                        row.status = jo.getString("status");
                        row.attempts = jo.getLong("attempts");
                        row.delay = jo.getLong("delay");
                        row.elapsed = jo.getLong("elapsed");
                        row.firstTime = jo.getLong("firstTime");
                        row.lastTime = jo.getLong("lastTime");
                        row.nextTime = jo.getLong("nextTime");
                        row.error = jo.getString("error");
                        rows.add(row);
                    }
                }
            }
        });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printer()
                    .println(AsciiTable.getTable(
                            AsciiTable.NO_BORDERS,
                            rows,
                            Arrays.asList(
                                    new Column()
                                            .header("PID")
                                            .headerAlign(HorizontalAlign.CENTER)
                                            .with(r -> r.pid),
                                    new Column()
                                            .header("NAME")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                                            .with(r -> r.name),
                                    new Column()
                                            .header("TASK")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .with(r -> r.task),
                                    new Column()
                                            .header("STATUS")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .with(r -> r.status),
                                    new Column()
                                            .header("ATTEMPT")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .with(r -> "" + r.attempts),
                                    new Column()
                                            .header("DELAY")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .with(r -> "" + r.delay),
                                    new Column()
                                            .header("ELAPSED")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .with(this::getElapsed),
                                    new Column()
                                            .header("FIRST")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .with(this::getFirst),
                                    new Column()
                                            .header("LAST")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .with(this::getLast),
                                    new Column()
                                            .header("NEXT")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .with(this::getNext),
                                    new Column()
                                            .header("FAILURE")
                                            .dataAlign(HorizontalAlign.LEFT)
                                            .maxWidth(140, OverflowBehaviour.NEWLINE)
                                            .with(r -> r.error))));
        }

        return 0;
    }

    private String getElapsed(Row r) {
        return TimeUtils.printAge(r.elapsed);
    }

    private String getFirst(Row r) {
        if (r.firstTime > 0) {
            return TimeUtils.printSince(r.firstTime);
        }
        return "";
    }

    private String getLast(Row r) {
        if (r.lastTime > 0) {
            return TimeUtils.printSince(r.lastTime, true);
        }
        return "";
    }

    private String getNext(Row r) {
        if (r.nextTime > 0) {
            long age = r.nextTime - System.currentTimeMillis();
            return TimeUtils.printDuration(age, true);
        }
        return "";
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

    private static class Row implements Cloneable {
        String pid;
        String name;
        long uptime;
        String age;
        String task;
        String status;
        long attempts;
        long delay;
        long elapsed;
        long firstTime;
        long lastTime;
        long nextTime;
        String error;

        Row copy() {
            try {
                return (Row) clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
