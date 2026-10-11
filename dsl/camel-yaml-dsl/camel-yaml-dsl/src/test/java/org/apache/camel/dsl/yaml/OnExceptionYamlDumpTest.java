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
package org.apache.camel.dsl.yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.NamedNode;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.impl.DefaultDumpRoutesStrategy;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.ModelToYAMLDumper;
import org.apache.camel.spi.Resource;
import org.apache.camel.yaml.LwModelToYAMLDumper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OnExceptionYamlDumpTest extends YamlTestSupport {

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void topLevelOnExceptionIsDumpedAsTopLevel(boolean started) throws Exception {
        loadRoutes("""
                - onException:
                    exception:
                      - java.lang.Exception
                    handled:
                      constant:
                        expression: "true"
                    steps:
                      - to: mock:error
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
                """);
        if (started) {
            context.start();
        }
        String yaml = dumpRoutes();
        // a route dumped alone (JMX dumpRouteAsYaml, the route-dump dev console)
        String single = new LwModelToYAMLDumper()
                .dumpModelAsYaml(context, context.getRouteDefinition("first"), false, true, false, false);
        if (started) {
            context.stop();
        }

        // written once, before the routes, and not in the steps of each route
        assertThat(yaml).startsWith("- onException:");
        assertThat(yaml.split("onException:", -1)).hasSize(2);
        // also before a route dumped alone
        assertThat(single).startsWith("- onException:").contains("id: first").doesNotContain("id: second");

        // the dump is valid for the YAML DSL schema, and the onException still applies to all the routes
        context.removeRouteDefinitions(List.copyOf(context.getRouteDefinitions()));
        loadRoutes(yaml);
        for (String id : List.of("first", "second")) {
            OnExceptionDefinition oe = (OnExceptionDefinition) context.getRouteDefinition(id).getOutputs().get(0);
            assertThat(oe.isRouteScoped()).isFalse();
            assertThat(oe.getExceptions()).containsExactly("java.lang.Exception");
        }
    }

    @Test
    void routeConfigurationOnExceptionIsNotDumpedInTheRoutes() throws Exception {
        loadRoutes("""
                - routeConfiguration:
                    onException:
                      - onException:
                          exception:
                            - java.lang.Exception
                          handled:
                            constant:
                              expression: "true"
                          steps:
                            - to: mock:error
                """);
        loadRoutes("""
                - route:
                    id: myRoute
                    from:
                      uri: direct:start
                      steps:
                        - to: mock:result
                """);
        assertThat(context.getRouteDefinition("myRoute").getOutputs().get(0)).isInstanceOf(OnExceptionDefinition.class);

        // the onException is dumped with the route configuration
        String yaml = dumpRoutes();
        assertThat(yaml).doesNotContain("onException").contains("id: myRoute");
        context.removeRouteDefinition(context.getRouteDefinition("myRoute"));
        loadRoutes(yaml);
    }

    @Test
    void onExceptionOfAnotherFileIsDumpedBeforeTheRoutesInOneFile(@TempDir Path dir) throws Exception {
        // two files normalized into one, as camel validate normalize a.yaml b.yaml does: the onException of the
        // second file must come before the route of the first one, or the dump does not load
        loadRoutes("""
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                """, """
                - onException:
                    exception:
                      - java.lang.Exception
                    handled:
                      constant:
                        expression: "true"
                    steps:
                      - to: mock:error
                - route:
                    id: second
                    from:
                      uri: direct:second
                      steps:
                        - to: mock:second
                """);

        Path output = dir.resolve("normalized.yaml");
        List<String> warnings = new ArrayList<>();
        dumpRoutesToFile(output, new ArrayList<>(), warnings);
        String yaml = Files.readString(output);

        assertThat(yaml).startsWith("- onException:");
        assertThat(yaml.split("onException:", -1)).hasSize(2);
        assertThat(yaml).contains("id: first", "id: second");

        // the user is told that the onException of the second file applies to the route of the first file as well
        assertThat(warnings).singleElement().asString().contains("route-1.yaml").doesNotContain("route-0.yaml");

        // the combined dump is valid for the YAML DSL schema and loads
        assertThat(context.getRouteDefinition("first").getOutputs()).noneMatch(OnExceptionDefinition.class::isInstance);
        context.removeRouteDefinitions(List.copyOf(context.getRouteDefinitions()));
        loadRoutes(yaml);
        assertThat(context.getRouteDefinitions()).hasSize(2);
        // the top-level onException of the second file now applies to the route of the first file as well
        assertThat(context.getRouteDefinition("first").getOutputs().get(0)).isInstanceOf(OnExceptionDefinition.class);
    }

    @Test
    void routesOfOneFileDumpedIntoOneFileKeepTheirSource(@TempDir Path dir) throws Exception {
        loadRoutes("""
                - onException:
                    exception:
                      - java.lang.Exception
                    handled:
                      constant:
                        expression: "true"
                    steps:
                      - to: mock:error
                - route:
                    id: first
                    from:
                      uri: direct:first
                      steps:
                        - to: mock:first
                """);

        Path output = dir.resolve("normalized.yaml");
        List<Resource> resources = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        dumpRoutesToFile(output, resources, warnings);

        // the routes are dumped with their resource, so the log shows its source, and there is no scope to warn about
        assertThat(resources).singleElement().extracting(Resource::getLocation).isEqualTo("route-0.yaml");
        assertThat(warnings).isEmpty();
    }

    private void dumpRoutesToFile(Path output, List<Resource> resources, List<String> warnings) {
        AbstractAppender appender = new AbstractAppender("dump-routes", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(DefaultDumpRoutesStrategy.class);
        logger.addAppender(appender);
        try {
            DefaultDumpRoutesStrategy dump = new DefaultDumpRoutesStrategy() {
                @Override
                protected void doDumpYaml(
                        CamelContext camelContext, NamedNode def, Resource resource, ModelToYAMLDumper dumper,
                        String kind, StringBuilder sbLocal, StringBuilder sbLog) {
                    resources.add(resource);
                    super.doDumpYaml(camelContext, def, resource, dumper, kind, sbLocal, sbLog);
                }
            };
            dump.setCamelContext(context);
            dump.setInclude("routes");
            dump.setLog(false);
            dump.setUriAsParameters(true);
            dump.setOutput(output.toString());
            dump.dumpRoutes("yaml");
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    private String dumpRoutes() throws Exception {
        // as the routes are dumped by camel.main.dumpRoutes, which camel validate normalize uses
        RoutesDefinition routes = new RoutesDefinition();
        for (RouteDefinition route : context.getRouteDefinitions()) {
            routes.getRoutes().add(route);
        }
        return new LwModelToYAMLDumper().dumpModelAsYaml(context, routes, false, true, false, false);
    }
}
