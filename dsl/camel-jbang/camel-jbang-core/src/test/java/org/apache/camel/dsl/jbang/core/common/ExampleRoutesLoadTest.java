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
package org.apache.camel.dsl.jbang.core.common;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.Model;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every bundled example route resource loads and parses through the Camel routes loader without error.
 *
 * The example files ship as run-ready resources for {@code camel run --example=...}. A malformed file (for instance a
 * misplaced YAML key that the DSL cannot construct) is only discovered when the loader builds the route model, so this
 * test loads each one through the same loader the CLI uses. The context is intentionally not started, but note this is
 * still a parse-time test: example beans are instantiated while the loader pre-parses (which is why the {@code Greeter}
 * fixture and the example's {@code application.properties} are required), whereas endpoints and their producers and
 * consumers are only created at start. Not starting therefore avoids opening external endpoints (JMS, Kafka, SQL, ...)
 * but also means endpoint wiring is not exercised.
 */
class ExampleRoutesLoadTest {

    static Stream<Arguments> exampleRouteFiles() throws Exception {
        // resolve the examples from the classpath (target/classes/examples) so the test does not depend on the working
        // directory being the module base, which differs between Maven Surefire and IDE run configurations
        URL examplesUrl = ExampleRoutesLoadTest.class.getClassLoader().getResource("examples");
        assertNotNull(examplesUrl, "examples resources not found on the test classpath");
        Path examplesDir = Path.of(examplesUrl.toURI());

        try (Stream<Path> files = Files.walk(examplesDir)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()
                    // use the path relative to the examples root as a stable, readable test display name
                    .map(p -> Arguments.of(Named.of(examplesDir.relativize(p).toString(), p)))
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exampleRouteFiles")
    void shouldLoadAndParseExample(Path file) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // make the example's own application.properties available so that property placeholders bound eagerly while
            // the loader pre-parses (e.g. bean properties in beans.yaml) can be resolved; this reproduces the effect of
            // the CLI loading the example properties, through setInitialProperties rather than the CLI's
            // properties-location mechanism
            loadExampleProperties(context, file);

            Resource resource = ResourceHelper.resolveResource(context, "file:" + file);

            assertDoesNotThrow(() -> PluginHelper.getRoutesLoader(context).loadRoutes(resource),
                    "Failed to load and parse example: " + file);

            // a parsed example must contribute something the loader understood: a route, a REST definition, a route
            // template or a bean. An otherwise-empty model means the loader silently accepted content it did not
            // understand, so inspect the parsed model rather than the raw text (which would pass on a tolerated-but-
            // empty file that merely contains a recognised keyword)
            Model model = context.getCamelContextExtension().getContextPlugin(Model.class);
            int modelElements = model.getRouteDefinitions().size()
                                + model.getRestDefinitions().size()
                                + model.getRouteTemplateDefinitions().size()
                                + model.getCustomBeans().size();
            assertTrue(modelElements > 0,
                    "Example parsed to an empty model (no routes, rests, templates or beans): " + file);
        }
    }

    private static void loadExampleProperties(DefaultCamelContext context, Path file) throws Exception {
        Path properties = file.resolveSibling("application.properties");
        if (!Files.exists(properties)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(properties)) {
            props.load(is);
        }
        context.getPropertiesComponent().setInitialProperties(props);
    }
}
