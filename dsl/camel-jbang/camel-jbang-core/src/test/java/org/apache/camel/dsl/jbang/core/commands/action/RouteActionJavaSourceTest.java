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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.UsesWorkingDirectory;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.StringPrinter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * route-diagram, route-topology and route-structure read Java source files with the Java DSL parser (CAMEL-25565): the
 * routes are dumped from a CamelContext that is never started, so nothing is compiled and a source that does not
 * compile (here: a processor class that does not exist) still gets its diagram.
 */
@UsesWorkingDirectory
class RouteActionJavaSourceTest {

    private static final String ORDERS = """
            import org.apache.camel.builder.RouteBuilder;

            public class Orders extends RouteBuilder {
                @Override
                public void configure() {
                    from("timer:orders").routeId("intake")
                        .setBody(constant("order"))
                        .process(exchange -> exchange.getMessage().setHeader("checked", true))
                        .to("direct:billing");
                }
            }
            """;

    private static final String BILLING = """
            import org.apache.camel.builder.RouteBuilder;

            public class Billing extends RouteBuilder {
                @Override
                public void configure() {
                    from("direct:billing").routeId("billing")
                        .process(new NoSuchProcessor())
                        .log("${body}");
                }
            }
            """;

    private static final String MIXED = """
            import org.apache.camel.builder.RouteBuilder;

            public class Mixed extends RouteBuilder {
                @Override
                public void configure() {
                    from("timer:tick").routeId("named")
                        .to("direct:next");

                    from("direct:next")
                        .log("${body}");
                }
            }
            """;

    @Test
    void diagramReadsJavaRoutesWithoutCompilingThem(@TempDir Path dir) throws Exception {
        Path orders = write(dir, "Orders.java", ORDERS);
        Path billing = write(dir, "Billing.java", BILLING);
        StringPrinter printer = new StringPrinter();
        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain().withPrinter(printer));

        List<RouteInfo> routes = command.doCallSource(List.of(orders.toString(), billing.toString()));

        assertThat(routes).extracting(r -> r.routeId).containsExactlyInAnyOrder("intake", "billing");
        RouteInfo intake = routes.stream().filter(r -> "intake".equals(r.routeId)).findFirst().orElseThrow();
        // the lambda is a placeholder step, so the route keeps all its steps
        assertThat(intake.nodes).extracting(n -> n.type).contains("from", "setBody", "process", "to");
        // a warning on stderr, not part of the output
        assertThat(printer.getStderr())
                .contains("WARN: Routes read without compiling them")
                .contains("Orders.java:")
                .contains("a lambda or method reference");
    }

    @Test
    void diagramCompilesWithCompileOption(@TempDir Path dir) throws Exception {
        Path missing = dir.resolve("Missing.java");
        StringPrinter printer = new StringPrinter();
        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain().withPrinter(printer));
        command.compile = true;

        // the existence check comes first, also when compiling
        assertThatThrownBy(() -> command.doCallSource(List.of(missing.toString())))
                .isInstanceOf(CliException.class)
                .hasMessage("File does not exist: " + missing)
                .extracting(e -> ((CliException) e).getExitCode()).isEqualTo(CliExitCode.NOT_FOUND);
    }

    @Test
    void diagramReportsJavaSourceWithoutRoutes(@TempDir Path dir) throws Exception {
        Path helper = write(dir, "Helper.java", """
                public class Helper {
                    public static String name() {
                        return "helper";
                    }
                }
                """);
        StringPrinter printer = new StringPrinter();
        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain().withPrinter(printer));

        assertThat(command.doCallSource(List.of(helper.toString()))).isNull();
        assertThat(printer.getOutput())
                .contains("No routes found")
                .contains("Helper.java has no routes the parser can read")
                .contains("--compile");
    }

    @Test
    void topologyLinksJavaRoutesOfSeveralFiles(@TempDir Path dir) throws Exception {
        Path orders = write(dir, "Orders.java", ORDERS);
        Path billing = write(dir, "Billing.java", BILLING);
        StringPrinter printer = new StringPrinter();
        CamelRouteTopologyAction command = new CamelRouteTopologyAction(new CamelJBangMain().withPrinter(printer));
        command.files = List.of(orders.toString(), billing.toString());
        command.jsonOutput = true;

        int exit = command.doCall();

        assertThat(exit).isZero();
        // with --json only the JSON is on stdout, and the parts the parser could not read are listed in it (and on
        // stderr, as a warning)
        JsonObject topology = (JsonObject) Jsoner.deserialize(printer.getStdout());
        assertThat(printer.getStderr()).contains("WARN: Routes read without compiling them");
        JsonArray edges = (JsonArray) topology.get("edges");
        assertThat(edges).hasSize(1);
        JsonObject edge = (JsonObject) edges.get(0);
        assertThat(edge.getString("fromRouteId")).isEqualTo("intake");
        assertThat(edge.getString("toRouteId")).isEqualTo("billing");
        JsonArray unread = (JsonArray) topology.get("unread");
        assertThat(unread).isNotEmpty();
        assertThat(unread.get(0).toString()).contains("Orders.java:");
    }

    @Test
    void structureReadsJavaRoutesWithoutCompilingThem(@TempDir Path dir) throws Exception {
        Path billing = write(dir, "Billing.java", BILLING);
        StringPrinter printer = new StringPrinter();
        CamelRouteStructureAction command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = billing.toString();

        int exit = command.doCall();

        assertThat(exit).isZero();
        assertThat(printer.getOutput())
                .contains("Source: Billing.java")
                .contains("billing")
                .contains("direct:billing")
                .contains("an object created in the route");
    }

    @Test
    void diagramReadsJavaRoutesWithoutRouteId(@TempDir Path dir) throws Exception {
        Path mixed = write(dir, "Mixed.java", MIXED);
        StringPrinter printer = new StringPrinter();
        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain().withPrinter(printer));

        List<RouteInfo> routes = command.doCallSource(List.of(mixed.toString()));

        // the route without an id gets one, as when Camel starts, and the named route is kept
        assertThat(routes).extracting(r -> r.routeId).hasSize(2).contains("named").doesNotContainNull();
    }

    @Test
    void structureReadsJavaRouteWithoutRouteId(@TempDir Path dir) throws Exception {
        Path mixed = write(dir, "Mixed.java", MIXED);
        StringPrinter printer = new StringPrinter();
        CamelRouteStructureAction command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = mixed.toString();
        // the default of --sort (two routes are sorted)
        command.sort = "name";

        int exit = command.doCall();

        assertThat(exit).isZero();
        assertThat(printer.getOutput())
                .contains("Source: Mixed.java")
                .contains("named")
                .contains("timer:tick")
                .contains("direct:next");
    }

    @Test
    void structureHasTheChecksumTheRouteHasWhenItRuns(@TempDir Path dir) throws Exception {
        Path orders = write(dir, "Orders.java", """
                import org.apache.camel.builder.RouteBuilder;

                public class Orders extends RouteBuilder {
                    @Override
                    public void configure() {
                        from("direct:orders").routeId("orders")
                            .choice()
                                .when(simple("${header.vip} == true")).to("seda:vip")
                                .otherwise().to("seda:orders")
                            .end()
                            .log("${body}");
                    }
                }
                """);
        StringPrinter printer = new StringPrinter();
        CamelRouteStructureAction command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = orders.toString();
        command.jsonOutput = true;

        assertThat(command.doCall()).isZero();
        JsonObject structure = Jsoner.deserialize(printer.getOutput(), new JsonObject());
        JsonObject route = ((JsonArray) structure.getCollection("routes")).getMap(0);
        String read = route.getString("checksum");

        // the same route, compiled and added to a CamelContext as when it runs
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:orders").routeId("orders")
                            .choice()
                            .when(simple("${header.vip} == true")).to("seda:vip")
                            .otherwise().to("seda:orders")
                            .end()
                            .log("${body}");
                }
            });
            String running = ((ModelCamelContext) context).getRouteDefinition("orders").getChecksum();
            assertThat(read).isNotNull().isEqualTo(running);
        }

        // the text shows it under the source
        printer = new StringPrinter();
        command = new CamelRouteStructureAction(new CamelJBangMain().withPrinter(printer));
        command.name = orders.toString();
        assertThat(command.doCall()).isZero();
        assertThat(printer.getOutput()).contains("Source: Orders.java", "Checksum: " + read);
    }

    private static Path write(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
