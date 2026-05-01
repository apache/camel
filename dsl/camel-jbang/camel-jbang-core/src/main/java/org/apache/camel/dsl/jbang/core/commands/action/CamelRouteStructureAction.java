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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.main.KameletMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.support.LoggerHelper.stripSourceLocationLineNumber;

@Command(name = "route-structure", description = "Dump Camel route structure", sortOptions = false,
         showDefaultValues = true)
public class CamelRouteStructureAction extends ActionBaseCommand {

    public static class NameIdCompletionCandidates implements Iterable<String> {

        public NameIdCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("name", "id").iterator();
        }

    }

    @CommandLine.Parameters(description = "Source file name, or name/pid of a running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--raw" },
                        description = "To output raw without metadata")
    boolean raw;

    @CommandLine.Option(names = { "--brief" },
                        description = "To show less detailed route structure")
    boolean brief;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter route by filename or route id (multiple names can be separated by comma)")
    String filter;

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Option(names = { "--ignore-loading-error" }, defaultValue = "false",
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    boolean ignoreLoadingError;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = NameIdCompletionCandidates.class,
                        description = "Sort route by name or id", defaultValue = "name")
    String sort;

    private volatile long pid;

    public CamelRouteStructureAction(CamelJBangMain main) {
        super(main);
    }

    protected void doCallPid(Long pid) {
        this.pid = pid;

        JsonObject root = new JsonObject();
        root.put("action", "route-structure");
        root.put("filter", "*");
        root.put("brief", brief);
        Path file = getActionFile(Long.toString(pid));
        try {
            Files.writeString(file, root.toJson());
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        Path outputFile;
        int exit = 0;
        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            // no running so check source files
            outputFile = Path.of(CommandLineHelper.CAMEL_JBANG_WORK_DIR, "/structure-output.json");
            PathUtils.deleteFile(outputFile);
            exit = doCallSource(name);
        } else if (pids.size() > 1) {
            printer().println("Name or pid " + name + " matches " + pids.size()
                              + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 1;
        } else {
            // ensure output file is deleted before executing action
            this.pid = pids.get(0);
            outputFile = getOutputFile(Long.toString(this.pid));
            PathUtils.deleteFile(outputFile);
            doCallPid(this.pid);
        }
        if (exit != 0) {
            return exit;
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
            if (jsonOutput) {
                String dump = Jsoner.prettyPrint(jo.toJson(), 2);
                printer().println(dump);
            } else {
                JsonArray arr = (JsonArray) jo.get("routes");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject o = (JsonObject) arr.get(i);
                    Row row = new Row();
                    row.location = extractSourceName(o.getString("source"));
                    row.routeId = o.getString("routeId");
                    if (!rows.contains(row)) {
                        List<JsonObject> lines = o.getCollection("code");
                        if (lines != null) {
                            for (JsonObject line : lines) {
                                Code code = new Code();
                                code.line = line.getInteger("line");
                                code.type = line.getString("type");
                                code.id = line.getString("id");
                                code.level = line.getInteger("level");
                                code.code = line.getString("code");
                                row.code.add(code);
                            }
                        }
                        boolean add = true;
                        if (filter != null) {
                            String f = filter;
                            boolean negate = filter.startsWith("-");
                            if (negate) {
                                f = f.substring(1);
                            }
                            // make filtering easier
                            if (!f.endsWith("*")) {
                                f += "*";
                            }
                            boolean match
                                    = PatternHelper.matchPattern(row.location, f) || PatternHelper.matchPattern(row.routeId, f);
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
                // sort rows
                rows.sort(this::sortRow);

                if (!rows.isEmpty()) {
                    printSource(rows);
                }
            }
        } else {
            printer().println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        // delete output file after use
        PathUtils.deleteFile(outputFile);

        return 0;
    }

    private int doCallSource(String name) throws Exception {
        File f = new File(name);
        if (!f.isFile() || !f.exists()) {
            printer().printErr("File does not exist: " + name);
            return 1;
        }

        final String target = CommandLineHelper.CAMEL_JBANG_WORK_DIR + "/structure-output.json";

        Run run = new Run(getMain()) {
            @Override
            protected void doAddInitialProperty(KameletMain main) {
                main.addInitialProperty("camel.main.dumpRoutes", "json");
                main.addInitialProperty("camel.main.dumpRoutesLog", "false");
                main.addInitialProperty("camel.main.dumpRoutesOutput", target);
                // turn debug off as this can otherwise include source location in dump
                main.addInitialProperty("camel.debug.enabled", "false");
                main.addInitialProperty(CamelJBangConstants.TRANSFORM, "true");
                main.addInitialProperty("camel.component.properties.ignoreMissingProperty", "true");
                if (ignoreLoadingError) {
                    // turn off bean method validator if ignore loading error
                    main.addInitialProperty("camel.language.bean.validate", "false");
                }
            }
        };
        run.files = List.of(name);
        run.executionLimitOptions.maxSeconds = 1;
        return run.runTransform(ignoreLoadingError);
    }

    protected int sortRow(Row o1, Row o2) {
        String s = sort;
        int negate = 1;
        if (s.startsWith("-")) {
            s = s.substring(1);
            negate = -1;
        }
        switch (s) {
            case "name":
                return o1.location.compareToIgnoreCase(o2.location) * negate;
            case "id":
                return o1.routeId.compareToIgnoreCase(o2.routeId) * negate;
            default:
                return 0;
        }
    }

    protected void printSource(List<Row> rows) {
        for (Row row : rows) {
            printer().println();
            if (!raw) {
                printer().printf("Source: %s%n", row.location);
                printer().println("--------------------------------------------------------------------------------");
            }
            for (int i = 0; i < row.code.size(); i++) {
                Code code = row.code.get(i);
                String c = StringHelper.limitLength(Jsoner.unescape(code.code), 90, " (clipped)");
                String pad = StringHelper.padString(code.level);
                if (raw) {
                    printer().printf("%s%s%n", pad, c);
                } else {
                    if (code.line != -1) {
                        printer().printf("%4d: %s%s%n", code.line, pad, c);
                    } else {
                        printer().printf("      %s%s%n", pad, c);
                    }
                }
            }
            printer().println();
        }
    }

    protected JsonObject waitForOutputFile(Path outputFile) {
        return getJsonObject(outputFile);
    }

    public static String extractSourceName(String loc) {
        loc = stripSourceLocationLineNumber(loc);
        if (loc != null) {
            if (loc.contains(":")) {
                // strip prefix
                loc = loc.substring(loc.indexOf(':') + 1);
                // file based such as xml and yaml
                loc = FileUtil.stripPath(loc);
            }
        }
        return loc;
    }

    private static class Row {
        String location;
        String routeId;
        List<Code> code = new ArrayList<>();

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            Row row = (Row) o;

            if (!Objects.equals(location, row.location))
                return false;
            return routeId.equals(row.routeId);
        }

        @Override
        public int hashCode() {
            return routeId.hashCode();
        }
    }

    private static class Code {
        int line;
        String type;
        String id;
        int level;
        String code;
    }

}
