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
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.ProcessHelper;
import org.apache.camel.dsl.jbang.core.common.VersionHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.dsl.jbang.core.common.CamelCommandHelper.extractState;

@Command(name = "context",
         description = "Top status of Camel integrations",
         sortOptions = false)
public class CamelContextTop extends ProcessWatchCommand {

    public static class PidNameMemAgeCompletionCandidates implements Iterable<String> {

        public PidNameMemAgeCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("pid", "name", "mem", "age").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--sort" }, completionCandidates = PidNameMemAgeCompletionCandidates.class,
                        description = "Sort by pid, name, mem, or age", defaultValue = "mem")
    String sort;

    public CamelContextTop(CamelJBangMain main) {
        super(main);
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
                        Row row = new Row();
                        rows.add(row);
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
                        row.ago = TimeUtils.printSince(row.uptime);
                        JsonObject runtime = (JsonObject) root.get("runtime");
                        row.platform = extractPlatform(ph, runtime);
                        row.platformVersion = extractPlatformVersion(row.platform,
                                runtime != null ? runtime.getString("platformVersion") : null);
                        row.javaVersion = runtime != null ? runtime.getString("javaVersion") : null;
                        row.state = context.getInteger("phase");
                        row.camelVersion = context.getString("version");
                        Map<String, ?> stats = context.getMap("statistics");
                        if (stats != null) {
                            Object thp = stats.get("exchangesThroughput");
                            if (thp != null) {
                                row.throughput = thp.toString();
                            }
                            Object load = stats.get("load01");
                            if (load != null) {
                                row.load01 = load.toString();
                            }
                            load = stats.get("load05");
                            if (load != null) {
                                row.load05 = load.toString();
                            }
                            load = stats.get("load15");
                            if (load != null) {
                                row.load15 = load.toString();
                            }
                        }
                        JsonObject mem = (JsonObject) root.get("memory");
                        if (mem != null) {
                            row.heapMemUsed = mem.getLong("heapMemoryUsed");
                            row.heapMemCommitted = mem.getLong("heapMemoryCommitted");
                            row.heapMemMax = mem.getLong("heapMemoryMax");
                            row.nonHeapMemUsed = mem.getLong("nonHeapMemoryUsed");
                            row.nonHeapMemCommitted = mem.getLong("nonHeapMemoryCommitted");
                        }
                        JsonObject threads = (JsonObject) root.get("threads");
                        if (threads != null) {
                            row.threadCount = threads.getInteger("threadCount");
                            row.peakThreadCount = threads.getInteger("peakThreadCount");
                        }
                        JsonObject cl = (JsonObject) root.get("classLoading");
                        if (cl != null) {
                            row.loadedClassCount = cl.getInteger("loadedClassCount");
                            row.totalLoadedClassCount = cl.getLong("totalLoadedClassCount");
                        }
                        JsonObject gc = (JsonObject) root.get("gc");
                        if (gc != null) {
                            row.gcCount = gc.getLong("collectionCount");
                            row.gcTime = gc.getLong("collectionTime");
                        }
                    }
                });

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, Arrays.asList(
                    new Column().header("PID").headerAlign(HorizontalAlign.CENTER).with(r -> r.pid),
                    new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).maxWidth(30, OverflowBehaviour.ELLIPSIS_RIGHT)
                            .with(r -> r.name),
                    new Column().header("JAVA").dataAlign(HorizontalAlign.LEFT).with(this::getJavaVersion),
                    new Column().header("CAMEL").dataAlign(HorizontalAlign.LEFT).with(r -> r.camelVersion),
                    new Column().header("PLATFORM").dataAlign(HorizontalAlign.LEFT).with(this::getPlatform),
                    new Column().header("STATUS").headerAlign(HorizontalAlign.CENTER)
                            .with(r -> extractState(r.state)),
                    new Column().header("AGE").headerAlign(HorizontalAlign.CENTER).with(r -> r.ago),
                    new Column().header("LOAD").headerAlign(HorizontalAlign.CENTER).dataAlign(HorizontalAlign.CENTER)
                            .with(this::getLoad),
                    new Column().header("HEAP").headerAlign(HorizontalAlign.CENTER).with(this::getHeapMemory),
                    new Column().header("NON-HEAP").headerAlign(HorizontalAlign.CENTER).with(this::getNonHeapMemory),
                    new Column().header("GC").headerAlign(HorizontalAlign.CENTER).with(this::getGC),
                    new Column().header("THREADS").headerAlign(HorizontalAlign.CENTER).with(this::getThreads))));
        }

        return 0;
    }

    private String extractPlatform(ProcessHandle ph, JsonObject runtime) {
        String answer = runtime != null ? runtime.getString("platform") : null;
        if ("Camel".equals(answer)) {
            // generic camel, we need to check if we run in JBang
            String cl = ph.info().commandLine().orElse("");
            if (cl.contains("main.CamelJBang run")) {
                answer = "JBang";
            }
        }
        return answer;
    }

    private String extractPlatformVersion(String platform, String platformVersion) {
        if (platformVersion == null) {
            if ("JBang".equals(platform)) {
                platformVersion = VersionHelper.getJBangVersion();
            }
        }
        return platformVersion;
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
            case "mem":
                return Long.compare(o1.heapMemUsed, o2.heapMemUsed) * negate * -1; // we want the biggest first
            case "age":
                return Long.compare(o1.uptime, o2.uptime) * negate;
            default:
                return 0;
        }
    }

    protected String getThroughput(Row r) {
        String s = r.throughput;
        if (s == null || s.isEmpty()) {
            s = "";
        }
        return s;
    }

    private String getPlatform(Row r) {
        if (r.platformVersion != null) {
            return r.platform + " v" + r.platformVersion;
        } else {
            return r.platform;
        }
    }

    private String getHeapMemory(Row r) {
        return asMegaBytesOneDigit(r.heapMemUsed) + "/" + asMegaBytesOneDigit(r.heapMemCommitted) + "/"
               + asMegaBytesOneDigit(r.heapMemMax) + " MB";
    }

    private String getNonHeapMemory(Row r) {
        return asMegaBytesOneDigit(r.nonHeapMemUsed) + "/" + asMegaBytesOneDigit(r.nonHeapMemCommitted) + " MB";
    }

    private String getThreads(Row r) {
        return r.threadCount + "/" + r.peakThreadCount;
    }

    private String getGC(Row r) {
        if (r.gcTime <= 0) {
            return "";
        } else {
            return String.format("%s (%d)", TimeUtils.printDuration(r.gcTime, true), r.gcCount);
        }
    }

    private String getJavaVersion(Row r) {
        String v = r.javaVersion;
        if (v == null) {
            v = "";
        }
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (Character.isDigit(ch) || ch == '.') {
                continue;
            }
            return v.substring(0, i);
        }
        return v;
    }

    private String getLoad(Row r) {
        String s1 = r.load01 != null ? r.load01 : "-";
        String s2 = r.load05 != null ? r.load05 : "-";
        String s3 = r.load15 != null ? r.load15 : "-";
        if ("0.00".equals(s1)) {
            s1 = "-";
        }
        if ("0.00".equals(s2)) {
            s2 = "-";
        }
        if ("0.00".equals(s3)) {
            s3 = "-";
        }
        if (s1.equals("-") && s2.equals("-") && s3.equals("-")) {
            return "0/0/0";
        }
        return s1 + "/" + s2 + "/" + s3;
    }

    private static long asMegaBytesOneDigit(long bytes) {
        return bytes / 1000 / 1000;
    }

    private static class Row {
        String pid;
        String platform;
        String platformVersion;
        String camelVersion;
        String javaVersion;
        String name;
        int state;
        String ago;
        long uptime;
        String throughput;
        String load01;
        String load05;
        String load15;
        long heapMemUsed;
        long heapMemCommitted;
        long heapMemMax;
        long nonHeapMemUsed;
        long nonHeapMemCommitted;
        int threadCount;
        int peakThreadCount;
        int loadedClassCount;
        long totalLoadedClassCount;
        long gcCount;
        long gcTime;
    }

}
