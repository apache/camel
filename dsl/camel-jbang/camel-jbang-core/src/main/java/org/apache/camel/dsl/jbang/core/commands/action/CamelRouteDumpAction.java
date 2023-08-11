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
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.support.PatternHelper;
import org.apache.camel.util.FileUtil;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import static org.apache.camel.support.LoggerHelper.stripSourceLocationLineNumber;

@Command(name = "route-dump", description = "Dump Camel route in XML or YAML format", sortOptions = false)
public class CamelRouteDumpAction extends ActionBaseCommand {

    public static class NameIdCompletionCandidates implements Iterable<String> {

        public NameIdCompletionCandidates() {
        }

        @Override
        public Iterator<String> iterator() {
            return List.of("name", "id").iterator();
        }

    }

    @CommandLine.Parameters(description = "Name or pid of running Camel integration", arity = "0..1")
    String name = "*";

    @CommandLine.Option(names = { "--format" },
                        description = "Output format (xml or yaml)", defaultValue = "xml")
    String format;

    @CommandLine.Option(names = { "--raw" },
                        description = "To output raw without metadata")
    boolean raw;

    @CommandLine.Option(names = { "--uri-as-parameters" },
                        description = "Whether to expand URIs into separated key/value parameters (only in use for YAML format)")
    boolean uriAsParameters;

    @CommandLine.Option(names = { "--filter" },
                        description = "Filter route by filename (multiple names can be separated by comma)")
    String filter;

    @CommandLine.Option(names = { "--sort" }, completionCandidates = NameIdCompletionCandidates.class,
                        description = "Sort route by name or id", defaultValue = "name")
    String sort;

    private volatile long pid;

    public CamelRouteDumpAction(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        List<Row> rows = new ArrayList<>();

        List<Long> pids = findPids(name);
        if (pids.isEmpty()) {
            return 0;
        } else if (pids.size() > 1) {
            System.out.println("Name or pid " + name + " matches " + pids.size()
                               + " running Camel integrations. Specify a name or PID that matches exactly one.");
            return 0;
        }

        this.pid = pids.get(0);

        // ensure output file is deleted before executing action
        File outputFile = getOutputFile(Long.toString(pid));
        FileUtil.deleteFile(outputFile);

        JsonObject root = new JsonObject();
        root.put("action", "route-dump");
        root.put("filter", "*");
        root.put("format", format);
        root.put("uriAsParameters", uriAsParameters);
        File file = getActionFile(Long.toString(pid));
        try {
            IOHelper.writeText(root.toJson(), file);
        } catch (Exception e) {
            // ignore
        }

        JsonObject jo = waitForOutputFile(outputFile);
        if (jo != null) {
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
                        boolean match = PatternHelper.matchPattern(row.location, f);
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
        } else {
            System.out.println("Response from running Camel with PID " + pid + " not received within 5 seconds");
            return 1;
        }

        // sort rows
        rows.sort(this::sortRow);

        if (!rows.isEmpty()) {
            printSource(rows);
        }

        // delete output file after use
        FileUtil.deleteFile(outputFile);

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
            System.out.println();
            if (!raw) {
                System.out.printf("Source: %s%n", row.location);
                System.out.println("--------------------------------------------------------------------------------");
            }
            for (int i = 0; i < row.code.size(); i++) {
                Code code = row.code.get(i);
                String c = Jsoner.unescape(code.code);
                if (raw) {
                    System.out.printf("%s%n", c);
                } else {
                    System.out.printf("%4d: %s%n", code.line, c);
                }
            }
            System.out.println();
        }
    }

    protected JsonObject waitForOutputFile(File outputFile) {
        StopWatch watch = new StopWatch();
        while (watch.taken() < 5000) {
            try {
                // give time for response to be ready
                Thread.sleep(100);

                if (outputFile.exists()) {
                    FileInputStream fis = new FileInputStream(outputFile);
                    String text = IOHelper.loadText(fis);
                    IOHelper.close(fis);
                    return (JsonObject) Jsoner.deserialize(text);
                }

            } catch (Exception e) {
                // ignore
            }
        }
        return null;
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
        String code;
    }

}
