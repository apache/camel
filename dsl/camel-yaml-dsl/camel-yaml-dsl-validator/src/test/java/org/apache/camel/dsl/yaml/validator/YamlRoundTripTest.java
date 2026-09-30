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
package org.apache.camel.dsl.yaml.validator;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.networknt.schema.Error;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.xml.LwModelToXMLDumper;
import org.apache.camel.xml.in.ModelParser;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The routes of the XML test corpus of camel-xml-io are dumped as YAML and loaded back with the YAML DSL: both models,
 * dumped as XML, must be the same, so what the YAML dumper writes is what the YAML DSL reads (CAMEL-25206). The YAML
 * must also pass the checks of camel validate.
 */
class YamlRoundTripTest {

    /** XML routes of the core modules' tests: camel-xml-io's parser tests and camel-core's model tests. */
    private static final List<Path> CORPUS = List.of(
            Path.of("../../../core/camel-xml-io/src/test/resources"),
            Path.of("../../../core/camel-core/src/test/resources/org/apache/camel/model"));

    /**
     * Routes that read back differently, and why. In YAML an EIP with no steps of its own takes the steps after it, as
     * in the Java DSL; the XML routes of the corpus keep them apart. The YAML DSL has no route scoped onException,
     * onCompletion or interceptors yet (CAMEL-25207): the YAML written for them does not validate.
     */
    private static final Map<String, String> KNOWN = Map.of(
            "kamelet.xml", "the steps after kamelet are its own in YAML: they run at the kamelet's sink",
            "barOnExceptionRoute.xml", "route scoped onException (CAMEL-25207)",
            "onCompletion.xml", "route scoped onCompletion (CAMEL-25207)",
            "barInterceptorRoute.xml", "route scoped intercept (CAMEL-25207)",
            "interceptFrom.xml", "route scoped interceptFrom (CAMEL-25207)",
            "interceptFromAndSendTo.xml", "route scoped interceptSendToEndpoint (CAMEL-25207)");

    private static List<RouteDefinition> routes(Path file) {
        for (String ns : List.of("http://camel.apache.org/schema/xml-io", "http://camel.apache.org/schema/spring")) {
            try (InputStream in = Files.newInputStream(file)) {
                RoutesDefinition routes = new ModelParser(in, ns).parseRoutesDefinition().orElse(null);
                if (routes != null && !routes.getRoutes().isEmpty()) {
                    return routes.getRoutes();
                }
            } catch (Exception e) {
                // not a routes file in this namespace
            }
        }
        return List.of();
    }

    @Test
    void xmlCorpus() throws Exception {
        assumeTrue(Files.isDirectory(CORPUS.get(0)), "the core test routes are in the source tree");
        List<Path> files = new ArrayList<>();
        for (Path dir : CORPUS) {
            try (Stream<Path> s = Files.list(dir)) {
                files.addAll(s.filter(p -> p.toString().endsWith(".xml")).sorted().toList());
            }
        }
        List<String> failures = new ArrayList<>();
        int routes = 0;
        for (Path file : files) {
            for (RouteDefinition route : routes(file)) {
                routes++;
                String problem = roundTrip(route);
                if (problem != null && !KNOWN.containsKey(file.getFileName().toString())) {
                    failures.add(file.getFileName() + "\n" + problem);
                }
            }
        }
        assertThat(routes).isGreaterThan(100);
        assertThat(failures).as("%d of %d routes do not read back:%n%s", failures.size(), routes,
                String.join("\n\n", failures)).isEmpty();
    }

    /** Null when the route reads back as it was, else what went wrong. */
    private static String roundTrip(RouteDefinition route) throws Exception {
        String yaml;
        String xml;
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            yaml = new LwModelToYAMLDumper().dumpModelAsYaml(context, route);
            // prepared as the routes loader prepares the route read back (transacted and saga take the route,
            // an interceptFrom that does not match the route is removed)
            RoutesDefinition routes = new RoutesDefinition();
            routes.setCamelContext(context);
            routes.prepareRoute(route);
            xml = new LwModelToXMLDumper().dumpModelAsXml(context, route);
        }
        // the YAML written must pass the checks of camel validate, not only load
        List<Error> errors = new YamlValidator().validate(yaml);
        if (!errors.isEmpty()) {
            return yaml + "\n--- does not validate: " + errors;
        }
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PluginHelper.getRoutesLoader(context).loadRoutes(ResourceHelper.fromString("route.yaml", yaml));
            List<RouteDefinition> loaded = context.getCamelContextExtension().getContextPlugin(ModelCamelContext.class)
                    .getRouteDefinitions();
            if (loaded.size() != 1) {
                return yaml + "\n--- loads " + loaded.size() + " routes";
            }
            String back = new LwModelToXMLDumper().dumpModelAsXml(context, loaded.get(0));
            return back.equals(xml) ? null : yaml + "\n--- was ---\n" + xml + "\n--- read back as ---\n" + back;
        } catch (Exception e) {
            return yaml + "\n--- does not load: " + e;
        }
    }
}
