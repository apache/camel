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

import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDiagramProblemsTest {

    @TempDir
    Path dir;

    private static NodeInfo node(String type, String code, int level, int line) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.code = code;
        n.level = level;
        n.line = line;
        return n;
    }

    @Test
    void textOfAProblem() {
        // the step and route it names first are where the callout points
        assertThat(RouteDiagramProblems.text("setBody: route orders: groovy reads fields of the body"))
                .isEqualTo("groovy reads fields of the body");
        // the lines to write after it are left out
        assertThat(RouteDiagramProblems.text("an option at the column of its EIP\n  - to:\n      uri: log:x"))
                .isEqualTo("an option at the column of its EIP");
        assertThat(RouteDiagramProblems.problem("Line 7: setBody: route orders: groovy reads fields"))
                .isEqualTo(new RouteDiagramProblems.Problem(7, "groovy reads fields"));
        assertThat(RouteDiagramProblems.problem("the file is empty"))
                .isEqualTo(new RouteDiagramProblems.Problem(0, "the file is empty"));
    }

    @Test
    void stepOfALine() {
        List<NodeInfo> nodes = List.of(node("route", "route[orders]", 0, 4), node("from", "from[file:orders]", 1, 4),
                node("setBody", "setBody[groovy{body.items}]", 2, 6), node("to", "to[http:pricing]", 2, 9));
        // the line of the expression is inside its step
        assertThat(RouteDiagramProblems.stepAt(nodes, 7).type).isEqualTo("setBody");
        assertThat(RouteDiagramProblems.stepAt(nodes, 9).type).isEqualTo("to");
        assertThat(RouteDiagramProblems.stepAt(nodes, 4).type).isEqualTo("from");
        // before the first step, or of the file: the first step
        assertThat(RouteDiagramProblems.stepAt(nodes, 1).type).isEqualTo("route");
        assertThat(RouteDiagramProblems.stepAt(nodes, 0).type).isEqualTo("route");
    }

    @Test
    void problemsOfAYamlRoute() throws Exception {
        Path file = dir.resolve("orders.camel.yaml");
        Files.writeString(file, """
                - route:
                    id: orders
                    from:
                      uri: file:orders
                      steps:
                        - setBody:
                            groovy:
                              expression: body.items
                        - to:
                            uri: log:orders
                """);
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        route.source = "orders.camel.yaml";
        route.nodes.add(node("route", "route[orders]", 0, 4));
        route.nodes.add(node("from", "from[file:orders]", 1, 4));
        route.nodes.add(node("setBody", "setBody[groovy{body.items}]", 2, 6));
        route.nodes.add(node("to", "to[log:orders]", 2, 9));

        RouteDiagramProblems.attach(List.of(route), List.of(file.toString()));

        // groovy reads fields of the file as it was read
        assertThat(route.nodes.get(2).problems).singleElement().asString()
                .contains("body.items").contains("GenericFile").doesNotStartWith("setBody: route");
        assertThat(route.nodes.get(0).problems).isEmpty();
        assertThat(route.nodes.get(3).problems).isEmpty();
    }

    @Test
    void problemsOfAJavaRoute() throws Exception {
        Path file = dir.resolve("MyRoute.java");
        Files.writeString(file, """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() {
                        from("timer:tick")
                            .log("tick")
                            .to("kafka:orders?brokerz=localhost:9092");
                    }
                }
                """);
        RouteInfo route = new RouteInfo();
        route.routeId = "route1";
        route.source = "MyRoute";
        route.nodes.add(node("route", "route[route1]", 0, 6));
        route.nodes.add(node("from", "from[timer:tick]", 1, 6));
        route.nodes.add(node("log", "log[tick]", 2, 7));
        route.nodes.add(node("to", "to[kafka:orders?brokerz=localhost:9092]", 2, 8));

        RouteDiagramProblems.attach(List.of(route), List.of(file.toString()));

        // the option the kafka component does not have, found without compiling the source
        assertThat(route.nodes.get(3).problems).singleElement().asString().contains("brokerz");
        assertThat(route.nodes.get(2).problems).isEmpty();
    }

    @Test
    void aFileThatCannotBeRead() throws Exception {
        Path file = dir.resolve("orders.camel.yaml");
        // not UTF-8 text
        Files.write(file, new byte[] { '-', ' ', (byte) 0xff, (byte) 0xfe, '\n' });
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        route.source = "orders.camel.yaml";
        route.nodes.add(node("route", "route[orders]", 0, 1));
        route.nodes.add(node("from", "from[file:orders]", 1, 2));

        RouteDiagramProblems.attach(List.of(route), List.of(file.toString()));

        // on the first step, as a problem of the file, not as a file without problems
        assertThat(route.nodes.get(0).problems)
                .containsExactly("the file cannot be read to check it: it is not UTF-8 text");
        assertThat(route.nodes.get(1).problems).isEmpty();
    }

    @Test
    void aFileWithoutRoutesOfTheDiagramIsNotChecked() throws Exception {
        Path file = dir.resolve("other.camel.yaml");
        Files.writeString(file, "- route:\n    from:\n      uri: nope:\n");
        RouteInfo route = new RouteInfo();
        route.routeId = "orders";
        route.source = "orders.camel.yaml";
        route.nodes.add(node("route", "route[orders]", 0, 1));

        RouteDiagramProblems.attach(List.of(route), List.of(file.toString(), dir.resolve("missing.yaml").toString()));

        assertThat(route.nodes.get(0).problems).isEmpty();
    }
}
