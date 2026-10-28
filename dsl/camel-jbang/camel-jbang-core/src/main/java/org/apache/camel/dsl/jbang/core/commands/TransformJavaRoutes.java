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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.camel.NamedNode;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;

/**
 * Java routes read without compiling them: the sources are read into the model by the Java DSL parser of camel-java-io,
 * and dumped by the same dump strategy a running Camel uses ({@code camel.main.dumpRoutes}), in a CamelContext that is
 * never started. Nothing of the project is compiled, loaded or run.
 * <p>
 * Used by {@code camel transform route} (as YAML or XML, CAMEL-25202), and by {@code camel cmd route-diagram},
 * {@code route-structure} and {@code route-topology} (as the route structure and topology JSON, CAMEL-25565).
 */
public final class TransformJavaRoutes {

    /**
     * Whether the routes were dumped, and why not; and the parts the parser could not read (empty when every route was
     * read completely).
     */
    public record Result(boolean transformed, String reason, List<String> unread) {
    }

    /** The routes read from the files, and the parts of them the parser could not read. */
    private record Read(List<JavaParseResult> results, List<Resource> resources, List<String> unread, String failure) {
    }

    private TransformJavaRoutes() {
    }

    /** Whether the files are all Java sources, which the parser may read. */
    public static boolean applies(List<String> files) {
        return !files.isEmpty()
                && files.stream().allMatch(f -> f.endsWith(".java") && Files.isRegularFile(Path.of(f)));
    }

    /**
     * Transforms the Java routes of the files when every route can be read completely.
     *
     * @param  format          yaml or xml
     * @param  output          the file or directory to write to
     * @param  uriAsParameters whether to expand URIs into key/value parameters (YAML)
     * @return                 whether they were transformed; else why not, and nothing is written
     */
    static Result transform(List<String> files, String format, String output, boolean uriAsParameters)
            throws Exception {
        Read read = read(files, true);
        if (read.failure() != null) {
            return new Result(false, read.failure(), read.unread());
        }
        dump(read, format, output, uriAsParameters);
        return new Result(true, null, List.of());
    }

    /**
     * Dumps the route structure and the route topology of the Java routes of the files as JSON, as
     * {@code camel.main.dumpRoutes=json} does: one route structure file per source file, and
     * {@code route-topology.json}. A route the parser reads only partly is dumped too: a step it cannot work out (a
     * lambda, a processor) is the placeholder the parser puts in the model, and the parts it could not read are
     * returned.
     *
     * @param  output the directory (or file) to write to
     * @return        whether they were dumped; else why not (no file has a route the parser can read)
     */
    public static Result dumpStructure(List<String> files, String output) throws Exception {
        Read read = read(files, false);
        if (read.failure() != null) {
            return new Result(false, read.failure(), read.unread());
        }
        dump(read, "json", output, false);
        return new Result(true, null, read.unread());
    }

    /**
     * Reads the routes of the files.
     *
     * @param complete whether every route must be read completely: else the first part the parser could not read is the
     *                 failure; when not, a file without routes is skipped, and the failure is that no file has one
     */
    private static Read read(List<String> files, boolean complete) throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        Map<String, Supplier<String>> sources = javaSources(files);
        List<JavaParseResult> results = new ArrayList<>();
        List<Resource> resources = new ArrayList<>();
        List<String> unread = new ArrayList<>();
        for (String f : files) {
            String content = Files.readString(Path.of(f), StandardCharsets.UTF_8);
            JavaParseResult result = ProjectRoutes.parseJava(content, sources, catalog);
            for (JavaParseResult.Unresolved u : result.unresolved()) {
                if (!JavaParseResult.configuresTheContext(u)) {
                    String part = f + ":" + u.line() + " " + u.text() + " (" + u.reason() + ")";
                    if (complete) {
                        return new Read(List.of(), List.of(), List.of(part), part);
                    }
                    unread.add(part);
                }
            }
            if (result.routes().getRoutes().isEmpty() && result.rests().getRests().isEmpty()
                    && result.routeTemplates().getRouteTemplates().isEmpty()
                    && result.routeConfigurations().getRouteConfigurations().isEmpty()) {
                if (complete) {
                    return new Read(List.of(), List.of(), List.of(), f + " has no routes the parser can read");
                }
                continue;
            }
            results.add(result);
            // the source file, so each is dumped on its own and named after it, as when the routes run
            resources.add(ResourceHelper.fromString("file:" + Path.of(f).getFileName(), content));
        }
        if (results.isEmpty()) {
            return new Read(List.of(), List.of(), unread, String.join(", ", files) + " has no routes the parser can read");
        }
        return new Read(results, resources, unread, null);
    }

    private static void dump(Read read, String format, String output, boolean uriAsParameters) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // never started: adding the definitions only registers them
            for (int i = 0; i < read.results().size(); i++) {
                JavaParseResult r = read.results().get(i);
                Resource resource = read.resources().get(i);
                r.routes().getRoutes().forEach(d -> d.setResource(resource));
                if ("json".equals(format)) {
                    // the source of each step, as a routes loader records it (the route structure shows it)
                    r.routes().getRoutes().forEach(d -> locate(d, resource.getLocation()));
                }
                r.rests().getRests().forEach(d -> d.setResource(resource));
                r.routeTemplates().getRouteTemplates().forEach(d -> d.setResource(resource));
                r.routeConfigurations().getRouteConfigurations().forEach(d -> d.setResource(resource));
                context.addRouteConfigurations(r.routeConfigurations().getRouteConfigurations());
                context.addRestDefinitions(r.rests().getRests(), false);
                context.addRouteTemplateDefinitions(r.routeTemplates().getRouteTemplates());
                context.addRouteDefinitions(r.routes().getRoutes());
            }
            if ("json".equals(format)) {
                // the checksum of each route as written, which is the one the route has when it runs: only when the
                // parser read the whole source, as a part it could not read is missing from the routes
                if (read.unread().isEmpty()) {
                    for (RouteDefinition route : context.getRouteDefinitions()) {
                        if (route.getChecksum() == null) {
                            route.setChecksum(RouteDefinitionHelper.checksum(context, route));
                        }
                    }
                }
                // the ids a start assigns: the route structure looks each route up by its id, and shows the step ids
                // (a route id that is a property placeholder is kept as it is, as there are no properties)
                context.getPropertiesComponent().setIgnoreMissingProperty(true);
                List<RouteDefinition> routes = context.getRouteDefinitions();
                RouteDefinitionHelper.forceAssignIds(context, routes);
                routes.forEach(route -> RouteDefinitionHelper.forceAssignIds(context, route));
            }
            DefaultDumpRoutesStrategy dumper = new DefaultDumpRoutesStrategy();
            dumper.setCamelContext(context);
            dumper.setInclude("routes,rests,routeConfigurations,beans,dataFormats");
            dumper.setLog(false);
            dumper.setResolvePlaceholders(false);
            dumper.setUriAsParameters(uriAsParameters);
            dumper.setOutput(output);
            dumper.dumpRoutes(format);
        }
    }

    /** Sets the source location of the node and its children (the line numbers the parser has set already). */
    private static void locate(NamedNode node, String location) {
        if (node.getLocation() == null) {
            node.setLocation(location);
        }
        if (node instanceof RouteDefinition route && route.getInput() != null && route.getInput().getLocation() == null) {
            route.getInput().setLocation(location);
        }
        List<? extends NamedNode> children = node.getChildren();
        if (children != null) {
            children.forEach(c -> locate(c, location));
        }
    }

    /** The Java sources of the files' folders, for the constants a route takes from another class. */
    private static Map<String, Supplier<String>> javaSources(List<String> files) throws IOException {
        Map<String, Supplier<String>> answer = new LinkedHashMap<>();
        for (String f : files) {
            Path dir = Path.of(f).toAbsolutePath().getParent();
            if (dir == null) {
                continue;
            }
            try (Stream<Path> list = Files.list(dir)) {
                list.filter(p -> p.getFileName().toString().endsWith(".java"))
                        .forEach(p -> answer.putIfAbsent(p.toString(), () -> read(p)));
            }
        }
        return answer;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }
}
