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
package org.apache.camel.dsl.jbang.core.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.dsl.jbang.core.commands.ai.AuthoringTools;
import org.apache.camel.dsl.yaml.validator.SourceTopology;
import org.apache.camel.dsl.yaml.validator.SourceTopology.Finding;
import org.apache.camel.dsl.yaml.validator.SourceTopology.Result;
import org.apache.camel.dsl.yaml.validator.SourceTopology.RouteInfo;
import org.apache.camel.dsl.yaml.validator.SourceTopology.Skipped;
import org.apache.camel.spi.RouteTopologyDumper.TopologyEdge;
import org.apache.camel.spi.RouteTopologyDumper.TopologyExternalEndpoint;
import org.apache.camel.spi.RouteTopologyDumper.TopologyNode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Shows the routes of YAML route files as they are written, without running them: where each route starts, which routes
 * send to which, the endpoints that leave and enter the application, where the body of each route comes from, and what
 * looks wrong (CAMEL-24956). The findings are informational and do not fail the command.
 */
@Command(name = "topology",
         description = "Show the routes of YAML route files, how they connect, and what looks wrong",
         sortOptions = false, showDefaultValues = true,
         footer = {
                 "%nOnly YAML routes are read: not Java or XML routes, route templates or Kamelets.",
                 "%nExamples:",
                 "  camel topology",
                 "  camel topology src/main/resources/camel",
                 "  camel topology orders.camel.yaml lookup.camel.yaml",
                 "  camel topology --json" })
public class Topology extends CamelCommand {

    @CommandLine.Parameters(description = "YAML route files or directories to read (default: the current directory)",
                            arity = "0..*", paramLabel = "<paths>", parameterConsumer = PathsConsumer.class,
                            showDefaultValue = CommandLine.Help.Visibility.NEVER)
    List<String> paths = new ArrayList<>();

    @CommandLine.Option(names = { "--json" }, description = "Output in JSON Format")
    boolean jsonOutput;

    /** The directory read when no path is given. */
    Path baseDir = Path.of("").toAbsolutePath();

    public Topology(CamelJBangMain main) {
        super(main);
    }

    @Override
    public Integer doCall() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        // the files named on the command line: one of them that cannot be read is an error, not a skipped file
        Set<String> explicit = new HashSet<>();
        List<Skipped> unreadable = new ArrayList<>();
        // whether the scan of a directory did not see all of it
        AtomicBoolean incomplete = new AtomicBoolean();

        if (paths.isEmpty()) {
            if (!readDirectory(baseDir, null, sources, unreadable, incomplete)) {
                return 1;
            }
        } else {
            for (String name : paths) {
                Path path = Path.of(name);
                if (Files.isDirectory(path)) {
                    if (!readDirectory(path, name, sources, unreadable, incomplete)) {
                        return 1;
                    }
                } else if (!Files.isRegularFile(path)) {
                    printer().printErr("File does not exist: " + name);
                    return 1;
                } else if (!isYaml(path)) {
                    printer().printErr("Not a YAML file: " + name + " (only YAML routes are read)");
                    return 1;
                } else {
                    try {
                        sources.put(name, Files.readString(path));
                        explicit.add(name);
                    } catch (IOException e) {
                        printer().printErr("Cannot read file: " + name, e);
                        return 1;
                    }
                }
            }
        }

        Result result = SourceTopology.analyze(sources);
        for (Skipped skipped : result.skipped()) {
            if (explicit.contains(skipped.file()) && "unparseable".equals(skipped.reason())) {
                printer().printErr("Cannot parse YAML file: " + skipped.file());
                return 1;
            }
        }
        List<Skipped> skipped = new ArrayList<>(result.skipped());
        skipped.addAll(unreadable);

        if (jsonOutput) {
            printer().println(Jsoner.prettyPrint(toJson(result, skipped, incomplete.get()).toJson(), 2));
        } else {
            printText(result, skipped, incomplete.get());
        }
        return 0;
    }

    /**
     * Reads the YAML files under the directory, the way the file tools of the AI commands look at a project: build and
     * tooling directories skipped.
     *
     * @param  shownAs the directory as it was given on the command line, which the files are named under; null for the
     *                 current directory, whose files are named by their path in it
     * @return         false when the directory cannot be listed, after printing why
     */
    private boolean readDirectory(
            Path dir, String shownAs, Map<String, String> sources, List<Skipped> unreadable,
            AtomicBoolean incomplete) {
        List<Path> files;
        try {
            files = AuthoringTools.projectFiles(dir, incomplete);
        } catch (RuntimeException e) {
            printer().printErr("Cannot read directory: " + (shownAs != null ? shownAs : dir), e);
            return false;
        }
        for (Path file : files) {
            if (!isYaml(file)) {
                continue;
            }
            String relative = dir.relativize(file).toString().replace('\\', '/');
            String name = shownAs != null ? Path.of(shownAs).resolve(relative).toString() : relative;
            try {
                sources.put(name, Files.readString(file));
            } catch (IOException e) {
                // a file of the directory that cannot be read, such as one that is not text: not the fault of the
                // command line
                unreadable.add(new Skipped(name, "unreadable"));
            }
        }
        return true;
    }

    private static boolean isYaml(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".yaml") || name.endsWith(".yml");
    }

    private void printText(Result result, List<Skipped> skipped, boolean incomplete) {
        List<TopologyNode> nodes = result.topology().nodes();
        List<TopologyEdge> edges = result.topology().edges();
        if (nodes.isEmpty()) {
            printer().println("No routes found in: "
                              + (paths.isEmpty() ? "the current directory" : String.join(", ", paths))
                              + " (only YAML routes are read)");
        } else {
            printer().println(String.format("Route topology (%d routes, %d connections, %d findings) - YAML routes only",
                    nodes.size(), edges.size(), result.findings().size()));
            printer().println("");
            for (int i = 0; i < nodes.size(); i++) {
                TopologyNode node = nodes.get(i);
                RouteInfo info = result.routes().get(i);
                printer().println(String.format("  %s (%s) type=%s  [%s]", node.routeId(), node.from(), node.nodeType(),
                        info.file()));
                printer().println("    body in: " + describeBody(info));
                for (TopologyEdge edge : edges) {
                    if (edge.fromRouteId().equals(node.routeId())) {
                        printer().println(String.format("    --> %s via %s [%s]", edge.toRouteId(), edge.endpoint(),
                                edge.connectionType()));
                    }
                }
            }
        }

        if (!result.topology().externalEndpoints().isEmpty()) {
            printer().println("");
            printer().println("External endpoints:");
            for (TopologyExternalEndpoint ep : result.topology().externalEndpoints()) {
                printer().println(String.format("  [%s] %s (%s) route=%s", ep.direction(), ep.uri(), ep.scheme(),
                        ep.routeId()));
            }
        }
        if (!result.findings().isEmpty()) {
            printer().println("");
            printer().println("Findings:");
            for (Finding finding : result.findings()) {
                printer().println("  " + finding.message());
            }
        }
        if (!skipped.isEmpty()) {
            printer().println("");
            printer().println("Not fully read:");
            for (Skipped s : skipped) {
                printer().println(String.format("  %s (%s)", s.file(), s.reason()));
            }
            if (skipped.stream().anyMatch(
                    s -> SourceTopology.ROUTE_TEMPLATES_NOT_READ.equals(s.reason()) || "kamelet".equals(s.reason()))) {
                // what a route template or a Kamelet creates could consume or call any endpoint
                printer().println("  Endpoints no route consumes and routes no route calls are not reported while"
                                  + " route templates or Kamelets are not read.");
            }
        }
        if (incomplete) {
            printer().println("");
            printer().println("Note: the directory has more files or levels than the scan reads, so some route files"
                              + " may not have been read.");
        }
    }

    private static String describeBody(RouteInfo info) {
        String origin = switch (info.bodyOrigin()) {
            case NONE -> "none";
            case CALLERS -> "from " + String.join(", ", info.callers());
            case CONSUMER -> "from the consumer of the route";
            case UNKNOWN -> "unknown - no route in the files calls it";
        };
        if (info.bodySetBy() == null) {
            return origin;
        }
        // the first step that may put something in the body: only a setBody certainly does
        return origin + ("setBody".equals(info.bodySetBy()) ? "; set by " : "; may be set by ") + info.bodySetBy();
    }

    /**
     * The same nodes, edges and external endpoints as the {@code route-topology} developer console gives, with the file
     * and the body of each node, the findings and the files not read added.
     */
    private static JsonObject toJson(Result result, List<Skipped> skipped, boolean incomplete) {
        JsonArray nodes = new JsonArray();
        for (int i = 0; i < result.topology().nodes().size(); i++) {
            TopologyNode node = result.topology().nodes().get(i);
            // the nodes and the route infos are in the same order
            RouteInfo info = result.routes().get(i);
            JsonObject jo = new JsonObject();
            jo.put("routeId", node.routeId());
            putIfNotNull(jo, "description", node.description());
            putIfNotNull(jo, "from", node.from());
            putIfNotNull(jo, "fromScheme", node.fromScheme());
            jo.put("nodeType", node.nodeType());
            jo.put("file", info.file());
            JsonObject body = new JsonObject();
            body.put("origin", info.bodyOrigin().name().toLowerCase(Locale.ROOT));
            body.put("callers", new JsonArray(info.callers()));
            putIfNotNull(body, "setBy", info.bodySetBy());
            jo.put("body", body);
            nodes.add(jo);
        }
        JsonArray edges = new JsonArray();
        for (TopologyEdge edge : result.topology().edges()) {
            JsonObject jo = new JsonObject();
            jo.put("fromRouteId", edge.fromRouteId());
            jo.put("toRouteId", edge.toRouteId());
            jo.put("endpoint", edge.endpoint());
            jo.put("connectionType", edge.connectionType());
            edges.add(jo);
        }
        JsonArray external = new JsonArray();
        for (TopologyExternalEndpoint ep : result.topology().externalEndpoints()) {
            JsonObject jo = new JsonObject();
            jo.put("id", ep.id());
            jo.put("uri", ep.uri());
            jo.put("scheme", ep.scheme());
            jo.put("direction", ep.direction());
            jo.put("routeId", ep.routeId());
            external.add(jo);
        }
        JsonArray findings = new JsonArray();
        for (Finding finding : result.findings()) {
            JsonObject jo = new JsonObject();
            jo.put("kind", finding.kind());
            jo.put("routeId", finding.routeId());
            jo.put("file", finding.file());
            putIfNotNull(jo, "endpoint", finding.endpoint());
            jo.put("message", finding.message());
            findings.add(jo);
        }
        JsonArray skippedFiles = new JsonArray();
        for (Skipped s : skipped) {
            JsonObject jo = new JsonObject();
            jo.put("file", s.file());
            jo.put("reason", s.reason());
            skippedFiles.add(jo);
        }
        JsonObject root = new JsonObject();
        root.put("nodes", nodes);
        root.put("edges", edges);
        root.put("externalEndpoints", external);
        root.put("findings", findings);
        root.put("skipped", skippedFiles);
        if (incomplete) {
            root.put("incomplete", true);
        }
        return root;
    }

    private static void putIfNotNull(JsonObject jo, String key, Object value) {
        if (value != null) {
            jo.put(key, value);
        }
    }

    static class PathsConsumer extends CamelCommand.ParameterConsumer<Topology> {
        @Override
        protected void doConsumeParameters(Stack<String> args, Topology cmd) {
            cmd.paths.add(args.pop());
        }
    }
}
