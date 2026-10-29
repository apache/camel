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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.commands.TransformJavaRoutes;
import org.apache.camel.dsl.jbang.core.common.CamelJBangConstants;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.PathUtils;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.RowSort;
import org.apache.camel.main.KameletMain;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.support.LoggerHelper.stripSourceLocationLineNumber;

@Command(name = "route-structure", description = "Dump Camel route structure", sortOptions = false,
         showDefaultValues = true,
         footer = {
                 "%nExamples:",
                 "  camel cmd route-structure",
                 "  camel cmd route-structure --filter=myRoute" })
public class CamelRouteStructureAction extends ActionBaseCommand {

    private static final RowSort<Row> ORDER = new RowSort<Row>()
            .text("name", r -> r.location)
            .text("id", r -> r.routeId);

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

    @CommandLine.Option(names = { "--description" },
                        description = "To show description instead of code")
    boolean description;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter route by filename or route id (multiple names can be separated by comma)")
    String filter;

    @CommandLine.Option(names = { "--json" },
                        description = "Output in JSON Format")
    boolean jsonOutput;

    @CommandLine.Option(names = { "--varied" },
                        description = "To show only the steps that have been reached with more than one body type, as the"
                                      + " running integration has seen them (the message history records them, such as"
                                      + " with camel run --dev)")
    boolean varied;

    @CommandLine.Option(names = { "--ignore-loading-error" }, defaultValue = "false",
                        description = "Whether to ignore route loading and compilation errors (use this with care!)")
    boolean ignoreLoadingError;

    @CommandLine.Option(names = { "--compile" }, defaultValue = "false",
                        description = "Whether to compile Java source files and load their routes. By default Java "
                                      + "routes are read by the Java DSL parser, without compiling them")
    boolean compile;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = NameIdCompletionCandidates.class,
                        description = "Sort route by name or id", defaultValue = "name")
    String sort;

    @CommandLine.Mixin
    ActionTimeoutMixin timeoutOption;

    private volatile long pid;

    public CamelRouteStructureAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        JsonObject jo;
        List<CamelProcess> processes = ProcessFinder.find(name);
        if (processes.size() > 1) {
            // the message says which integrations match
            ProcessFinder.requireOne(name);
        }
        if (!processes.isEmpty()) {
            CamelProcess process = processes.get(0);
            this.pid = process.pid();
            jo = executeAction(process, "route-structure", root -> {
                root.put("filter", "*");
                root.put("brief", brief);
            });
        } else if ("*".equals(name)) {
            throw CliException.notFound("No running Camel integrations, and no source file given");
        } else {
            // no running integration with the name, so it is a source file
            jo = doCallSource(name);
        }
        if (varied) {
            jo = onlyVaried(jo);
            JsonArray routes = jo.getCollection("routes");
            if (!jsonOutput && (routes == null || routes.isEmpty())) {
                printer().println("No step has been reached with more than one body type (the message history records"
                                  + " the body types, such as with camel run --dev)");
                return 0;
            }
        }
        if (jsonOutput) {
            addUnreadSourceParts(jo);
            String dump = Jsoner.prettyPrint(jo.toJson(), 2);
            printer().println(dump);
        } else {
            JsonArray arr = (JsonArray) jo.get("routes");
            for (int i = 0; i < arr.size(); i++) {
                JsonObject o = (JsonObject) arr.get(i);
                Row row = new Row();
                row.location = extractSourceName(o.getString("source"));
                row.routeId = o.getString("routeId");
                row.checksum = o.getString("checksum");
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
                            code.description = line.getString("description");
                            code.bodyTypes = line.getCollection("bodyTypes");
                            code.bodyKinds = line.getCollection("bodyKinds");
                            if (brief) {
                                // make code brief
                                code.code = StringHelper.before(code.code, "[", code.code);
                            } else if (description) {
                                // show description
                                code.code = code.type + (code.description != null ? "[" + code.description + "]" : "");
                            }
                            row.code.add(code);
                        }
                    }
                    boolean add = true;
                    if (filter != null && !matchesFilter(filter, row.location, row.routeId)) {
                        add = false;
                    }
                    if (add) {
                        rows.add(row);
                    }
                }
            }
            // sort rows
            rows.sort(ORDER.comparator(sort));

            if (rows.isEmpty() && filter != null) {
                throw CliException.notFound("No route matching: " + filter);
            }
            if (!rows.isEmpty()) {
                printSource(rows);
            }
        }

        return 0;
    }

    /**
     * The route structure of a source file, without a running integration.
     */
    private JsonObject doCallSource(String name) throws Exception {
        File f = new File(name);
        if (!f.isFile() || !f.exists()) {
            throw CliException.notFound("File does not exist: " + name);
        }

        // a file of its own, as several commands may run at the same time
        Path output = Path.of(CommandLineHelper.CAMEL_JBANG_WORK_DIR,
                "structure-output-" + ProcessHandle.current().pid() + ".json");
        PathUtils.deleteFile(output);
        final String target = output.toString();
        try {
            if (!compile && TransformJavaRoutes.applies(List.of(name))) {
                if (!dumpJavaRoutes(List.of(name), target)) {
                    throw CliException.failure("Cannot read the routes of " + name);
                }
            } else {
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
                int exit = run.runTransform(ignoreLoadingError);
                if (exit != 0) {
                    throw new CliException(exit, "Cannot load the routes of " + name);
                }
            }
            JsonObject jo = getJsonObject(output);
            if (jo == null) {
                throw CliException.failure("No routes dumped from " + name);
            }
            return jo;
        } finally {
            PathUtils.deleteFile(output);
        }
    }

    protected void printSource(List<Row> rows) {
        for (Row row : rows) {
            printer().println();
            if (!raw) {
                printer().printf("Source: %s%n", row.location);
                if (row.checksum != null) {
                    // the same as the checksum of the running route while the route is not changed
                    printer().printf("Checksum: %s%n", row.checksum);
                }
                printer().println("--------------------------------------------------------------------------------");
            }
            // the body types each step was reached with, as the running app has seen them, line up in a column after
            // the code (the message history records them, such as with camel run --dev)
            int width = 0;
            if (!raw && row.code.stream().anyMatch(code -> code.bodyTypes != null && !code.bodyTypes.isEmpty())) {
                for (Code code : row.code) {
                    width = Math.max(width, StringHelper.padString(code.level).length() + clippedCode(code).length());
                }
            }
            for (int i = 0; i < row.code.size(); i++) {
                Code code = row.code.get(i);
                String c = clippedCode(code);
                String pad = StringHelper.padString(code.level);
                if (raw) {
                    printer().printf("%s%s%n", pad, c);
                } else {
                    String text = pad + c;
                    if (width > 0 && code.bodyTypes != null && !code.bodyTypes.isEmpty()) {
                        text = String.format("%-" + width + "s   body: %s", text, bodyTypes(code.bodyTypes, code.bodyKinds));
                    }
                    if (code.line != -1) {
                        printer().printf("%4d: %s%n", code.line, text);
                    } else {
                        printer().printf("      %s%n", text);
                    }
                }
            }
            printer().println();
        }
    }

    private static String clippedCode(Code code) {
        return StringHelper.limitLength(Jsoner.unescape(code.code), 90, " (clipped)");
    }

    /**
     * The body types by their simple class names, such as {@code String | LinkedHashMap} for a step reached with a text
     * by some messages and with a Map by others, followed by the kinds when there is more than one:
     * {@code String | LinkedHashMap (payload and data)}, a step that needs attention.
     */
    static String bodyTypes(List<String> types, List<String> kinds) {
        StringJoiner sj = new StringJoiner(" | ");
        for (String type : types) {
            sj.add(StringHelper.afterLast(type, ".", type));
        }
        String answer = sj.toString();
        if (kinds != null && kinds.size() > 1) {
            answer += " (" + String.join(", ", kinds.subList(0, kinds.size() - 1)) + " and " + kinds.get(kinds.size() - 1)
                      + ")";
        }
        return answer;
    }

    /**
     * Only the steps that have been reached with more than one body type, and the routes that have such steps: what is
     * dynamic in the routes, or needs attention when the kinds differ (bodyKinds).
     */
    public static JsonObject onlyVaried(JsonObject jo) {
        JsonArray routes = new JsonArray();
        JsonArray all = jo.getCollection("routes");
        if (all != null) {
            for (Object o : all) {
                JsonObject route = (JsonObject) o;
                JsonArray code = new JsonArray();
                List<JsonObject> lines = route.getCollection("code");
                if (lines != null) {
                    for (JsonObject line : lines) {
                        List<String> types = line.getCollection("bodyTypes");
                        if (types != null && types.size() > 1) {
                            code.add(line);
                        }
                    }
                }
                if (!code.isEmpty()) {
                    JsonObject copy = new JsonObject(route);
                    copy.put("code", code);
                    routes.add(copy);
                }
            }
        }
        JsonObject answer = new JsonObject(jo);
        answer.put("routes", routes);
        return answer;
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
        String checksum;
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
        String description;
        List<String> bodyTypes;
        List<String> bodyKinds;
    }
}
