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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that every bundled example route resource loads and parses through the Camel routes loader without error.
 *
 * The example files ship as run-ready resources for {@code camel run --example=...}. A malformed file (for instance a
 * misplaced YAML key that the DSL cannot construct) is only discovered when the loader builds the route model, so this
 * test loads each one through the same path the CLI uses. The context is intentionally not started: that keeps the test
 * focused on parsing and avoids instantiating example beans or opening external endpoints (JMS, Kafka, SQL, ...).
 */
class ExampleRoutesLoadTest {

    private static final Path EXAMPLES_DIR = Path.of("src/main/resources/examples");

    static Stream<Path> exampleRouteFiles() throws Exception {
        try (Stream<Path> files = Files.walk(EXAMPLES_DIR)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yaml") || name.endsWith(".yml");
                    })
                    .sorted()
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("exampleRouteFiles")
    void shouldLoadAndParseExample(Path file) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // mirror the CLI: make the example's own application.properties available so that property
            // placeholders bound eagerly at parse time (e.g. bean properties in beans.yaml) can be resolved
            loadExampleProperties(context, file);

            Resource resource = ResourceHelper.resolveResource(context, "file:" + file);

            assertDoesNotThrow(() -> PluginHelper.getRoutesLoader(context).loadRoutes(resource),
                    "Failed to load and parse example: " + file);

            // a parsed example must yield either routes or a beans definition; an empty model means the
            // loader silently accepted content it did not understand
            int routeCount = context.getRouteDefinitions().size();
            boolean beansFile = Files.readString(file).contains("beans:");
            assertTrue(routeCount > 0 || beansFile,
                    "Example parsed to an empty model (no routes and no beans): " + file);
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
