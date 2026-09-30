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

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.apache.camel.java.in.JavaParseResult;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.ResourceHelper;

/**
 * {@code camel transform route} for Java routes without compiling them (CAMEL-25202): the sources are read into the
 * model by the Java DSL parser of camel-java-io, and dumped as YAML or XML by the same dump strategy a running Camel
 * uses ({@code camel.main.dumpRoutes}), in a CamelContext that is never started. Nothing of the project is compiled,
 * loaded or run.
 */
final class TransformJavaRoutes {

    /** Whether the routes were transformed, and why not: the first part the parser could not read. */
    record Result(boolean transformed, String reason) {
    }

    private TransformJavaRoutes() {
    }

    /** Whether the files are all Java sources, which the parser may read. */
    static boolean applies(List<String> files) {
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
        CamelCatalog catalog = new DefaultCamelCatalog();
        Map<String, Supplier<String>> sources = javaSources(files);
        List<JavaParseResult> results = new ArrayList<>();
        List<Resource> resources = new ArrayList<>();
        for (String f : files) {
            String content = Files.readString(Path.of(f), StandardCharsets.UTF_8);
            JavaParseResult result = ProjectRoutes.parseJava(content, sources, catalog);
            for (JavaParseResult.Unresolved u : result.unresolved()) {
                if (!JavaParseResult.configuresTheContext(u)) {
                    return new Result(false, f + ":" + u.line() + " " + u.text() + " (" + u.reason() + ")");
                }
            }
            if (result.routes().getRoutes().isEmpty() && result.rests().getRests().isEmpty()
                    && result.routeTemplates().getRouteTemplates().isEmpty()
                    && result.routeConfigurations().getRouteConfigurations().isEmpty()) {
                return new Result(false, f + " has no routes the parser can read");
            }
            results.add(result);
            // the source file, so each is dumped on its own and named after it, as when the routes run
            resources.add(ResourceHelper.fromString("file:" + Path.of(f).getFileName(), content));
        }
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // never started: adding the definitions only registers them
            for (int i = 0; i < results.size(); i++) {
                JavaParseResult r = results.get(i);
                Resource resource = resources.get(i);
                r.routes().getRoutes().forEach(d -> d.setResource(resource));
                r.rests().getRests().forEach(d -> d.setResource(resource));
                r.routeTemplates().getRouteTemplates().forEach(d -> d.setResource(resource));
                r.routeConfigurations().getRouteConfigurations().forEach(d -> d.setResource(resource));
                context.addRouteConfigurations(r.routeConfigurations().getRouteConfigurations());
                context.addRestDefinitions(r.rests().getRests(), false);
                context.addRouteTemplateDefinitions(r.routeTemplates().getRouteTemplates());
                context.addRouteDefinitions(r.routes().getRoutes());
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
        return new Result(true, null);
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
