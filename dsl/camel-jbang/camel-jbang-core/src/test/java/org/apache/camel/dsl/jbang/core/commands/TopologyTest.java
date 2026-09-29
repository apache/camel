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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24956: {@code camel topology}, the routes of YAML route files read from the source.
 */
class TopologyTest extends CamelCommandBaseTestSupport {

    private static final String ORDERS = """
            - route:
                id: orders
                from:
                  uri: timer:tick
                  steps:
                    - to:
                        uri: direct:lookup
                    - to:
                        uri: direct:missing
            """;

    private static final String LOOKUP = """
            - route:
                id: lookup
                from:
                  uri: direct:lookup
                  steps:
                    - setBody:
                        constant: found
                    - to:
                        uri: http:api
            """;

    private static final String CONSUME = """
            - route:
                id: consume
                from:
                  uri: kafka:orders
                  steps:
                    - log: got it
            """;

    private static final String ORPHAN = """
            - route:
                id: orphan
                from:
                  uri: direct:orphan
                  steps:
                    - log: nobody calls me
            """;

    private static final String TEMPLATE = """
            - routeTemplate:
                id: any
                from:
                  uri: direct:any
            """;

    @TempDir
    Path dir;

    private Topology command() {
        Topology command = new Topology(new CamelJBangMain().withPrinter(printer));
        command.baseDir = dir;
        return command;
    }

    private Path write(String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private JsonObject json(Topology command) throws Exception {
        command.jsonOutput = true;
        assertThat(command.doCall()).isZero();
        return (JsonObject) Jsoner.deserialize(printer.getOutput(), new JsonObject());
    }

    @Test
    void noArgumentsReadsTheCurrentDirectory() throws Exception {
        write("orders.camel.yaml", ORDERS);
        write("lookup.camel.yaml", LOOKUP);

        int exit = command().doCall();

        assertThat(exit).isZero();
        assertThat(printer.getOutput())
                .contains("Route topology (2 routes, 1 connections, 1 findings) - YAML routes only")
                .contains("orders (timer:tick) type=trigger  [orders.camel.yaml]")
                .contains("--> lookup via direct:lookup [internal]")
                .contains("lookup (direct:lookup) type=route  [lookup.camel.yaml]")
                .contains("body: none; set by setBody")
                .contains("External endpoints:")
                .contains("[out] http:api (http) route=lookup")
                .contains("Findings:")
                .contains("route orders: sends to direct:missing, and no route in the YAML files read consumes it");
    }

    @Test
    void aStepThatOnlyMayPutSomethingInTheBodyIsNotSaidToSetIt() throws Exception {
        write("orders.camel.yaml", ORDERS);
        write("lookup.camel.yaml", LOOKUP);

        assertThat(command().doCall()).isZero();

        // the orders route only sends to other routes; a to may replace the body, it does not certainly set one
        assertThat(printer.getOutput()).contains("body: none; may be set by to").contains("body: none; set by setBody");
    }

    @Test
    void theFindingsDoNotClaimMoreThanTheYamlFilesReadShow() throws Exception {
        Path orders = write("orders.camel.yaml", ORDERS);
        write("lookup.camel.yaml", LOOKUP);
        Topology command = command();
        // the consumer of direct:lookup is in a file that is not given
        command.paths = List.of(orders.toString());

        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput())
                .contains("no route in the YAML files read consumes it - if nothing else does (a Java or XML route, a"
                          + " file not read)")
                .doesNotContain("other route files of the directory");
    }

    @Test
    void aFindingDoesNotFailTheCommand() throws Exception {
        write("orders.camel.yaml", ORDERS);

        int exit = command().doCall();

        assertThat(exit).isZero();
        assertThat(printer.getOutput()).contains("Findings:").contains("direct:lookup").contains("direct:missing");
    }

    @Test
    void aDirectoryGivenAsAnArgumentNamesItsFilesUnderIt() throws Exception {
        write("orders.camel.yaml", ORDERS);
        write("lookup.camel.yaml", LOOKUP);
        Topology command = command();
        command.paths = List.of(dir.toString());

        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput())
                .contains("[" + dir.resolve("orders.camel.yaml") + "]")
                .contains("[" + dir.resolve("lookup.camel.yaml") + "]")
                .contains("Route topology (2 routes, 1 connections, 1 findings)");
    }

    @Test
    void theRoutesOfSubdirectoriesAreReadAndTheBuildDirectoryIsNot() throws Exception {
        write("src/main/resources/camel/lookup.camel.yaml", LOOKUP);
        write("target/classes/camel/orders.camel.yaml", ORDERS);

        assertThat(command().doCall()).isZero();

        assertThat(printer.getOutput())
                .contains("[src/main/resources/camel/lookup.camel.yaml]")
                .doesNotContain("orders");
    }

    @Test
    void aSingleFileIsReadOnItsOwn() throws Exception {
        Path orders = write("orders.camel.yaml", ORDERS);
        write("lookup.camel.yaml", LOOKUP);
        Topology command = command();
        command.paths = List.of(orders.toString());

        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput())
                .contains("Route topology (1 routes, 0 connections, 2 findings)")
                .doesNotContain("lookup (direct:lookup)");
    }

    @Test
    void jsonHasTheNodesEdgesAndExternalEndpointsOfTheDevConsole() throws Exception {
        write("orders.camel.yaml", ORDERS);
        write("lookup.camel.yaml", LOOKUP);
        write("consume.camel.yaml", CONSUME);

        JsonObject jo = json(command());

        // the files of a directory are read in the order of their paths
        JsonArray nodes = jo.getCollection("nodes");
        assertThat(nodes).extracting(n -> ((JsonObject) n).getString("routeId"))
                .containsExactly("consume", "lookup", "orders");
        JsonObject orders = (JsonObject) nodes.get(2);
        assertThat(orders.getString("from")).isEqualTo("timer:tick");
        assertThat(orders.getString("fromScheme")).isEqualTo("timer");
        assertThat(orders.getString("nodeType")).isEqualTo("trigger");
        assertThat(orders.getString("file")).isEqualTo("orders.camel.yaml");
        JsonObject lookupBody = ((JsonObject) nodes.get(1)).getMap("body");
        assertThat(lookupBody.getString("origin")).isEqualTo("none");
        assertThat(lookupBody.getString("setBy")).isEqualTo("setBody");

        JsonArray edges = jo.getCollection("edges");
        assertThat(edges).hasSize(1);
        JsonObject edge = (JsonObject) edges.get(0);
        assertThat(edge.getString("fromRouteId")).isEqualTo("orders");
        assertThat(edge.getString("toRouteId")).isEqualTo("lookup");
        assertThat(edge.getString("endpoint")).isEqualTo("direct:lookup");
        assertThat(edge.getString("connectionType")).isEqualTo("internal");

        JsonArray external = jo.getCollection("externalEndpoints");
        assertThat(external).extracting(o -> ((JsonObject) o).getString("uri") + " " + ((JsonObject) o).getString("direction"))
                .containsExactly("kafka:orders in", "http:api out");

        JsonArray findings = jo.getCollection("findings");
        assertThat(findings).singleElement().satisfies(f -> {
            assertThat(((JsonObject) f).getString("kind")).isEqualTo("unconsumed-endpoint");
            assertThat(((JsonObject) f).getString("endpoint")).isEqualTo("direct:missing");
        });
        assertThat((JsonArray) jo.getCollection("skipped")).isEmpty();
    }

    @Test
    void anUncalledRouteIsAFindingInBothOutputs() throws Exception {
        write("orphan.camel.yaml", ORPHAN);

        assertThat(command().doCall()).isZero();
        assertThat(printer.getOutput()).contains("nothing in the YAML route files sends to direct:orphan");
    }

    @Test
    void anEmptyDirectoryHasNoRoutesAndIsNotAnError() throws Exception {
        assertThat(command().doCall()).isZero();

        assertThat(printer.getOutput()).startsWith("No routes found in: the current directory");
    }

    @Test
    void anEmptyDirectoryIsEmptyArraysInJson() throws Exception {
        JsonObject jo = json(command());

        for (String key : List.of("nodes", "edges", "externalEndpoints", "findings", "skipped")) {
            assertThat((JsonArray) jo.getCollection(key)).as(key).isEmpty();
        }
    }

    @Test
    void aFileWithoutRoutesHasNoRoutesAndIsNotAnError() throws Exception {
        Path config = write("application.yaml", "server:\n  port: 8080\n");
        Topology command = command();
        command.paths = List.of(config.toString());

        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput()).startsWith("No routes found in: " + config);
    }

    @Test
    void aPathThatDoesNotExistIsAnError() throws Exception {
        Topology command = command();
        command.paths = List.of(dir.resolve("missing.camel.yaml").toString());

        assertThat(command.doCall()).isEqualTo(1);

        assertThat(printer.getOutput()).contains("ERROR: File does not exist: ");
    }

    @Test
    void aFileThatIsNotYamlIsAnErrorWhenNamed() throws Exception {
        Path java = write("Foo.java", "class Foo {}");
        Topology command = command();
        command.paths = List.of(java.toString());

        assertThat(command.doCall()).isEqualTo(1);

        assertThat(printer.getOutput()).contains("ERROR: Not a YAML file: ");
    }

    @Test
    void aFileNamedThatDoesNotParseIsAnError() throws Exception {
        Path broken = write("broken.camel.yaml", "- route: {");
        Topology command = command();
        command.paths = List.of(broken.toString());

        assertThat(command.doCall()).isEqualTo(1);

        assertThat(printer.getOutput()).contains("ERROR: Cannot parse YAML file: " + broken);
    }

    @Test
    void aFileOfADirectoryThatDoesNotParseIsSkippedAndTheOthersAreRead() throws Exception {
        write("orders.camel.yaml", ORDERS);
        write("lookup.camel.yaml", LOOKUP);
        write("broken.camel.yaml", "- route: {");
        write("template.camel.yaml", TEMPLATE);
        write("notes.txt", "not yaml, not looked at");

        assertThat(command().doCall()).isZero();

        assertThat(printer.getOutput())
                .contains("Route topology (2 routes, 1 connections")
                .contains("Skipped (not read):")
                .contains("broken.camel.yaml (unparseable)")
                .contains("template.camel.yaml (route-template)")
                .doesNotContain("notes.txt");
    }

    @Test
    void theOutputSaysWhyNoFindingIsReportedWhileATemplateIsNotRead() throws Exception {
        write("orders.camel.yaml", ORDERS);
        write("template.camel.yaml", TEMPLATE);

        assertThat(command().doCall()).isZero();

        // direct:lookup and direct:missing have no consumer in the files, yet a template could consume them
        assertThat(printer.getOutput())
                .contains("0 findings")
                .contains("are not reported while route templates or Kamelets are not read");
    }

    @Test
    void noNoteAboutTemplatesWhenNoneIsSkipped() throws Exception {
        write("orders.camel.yaml", ORDERS);
        write("broken.camel.yaml", "- route: {");

        assertThat(command().doCall()).isZero();

        assertThat(printer.getOutput()).contains("broken.camel.yaml (unparseable)")
                .doesNotContain("are not reported while");
    }

    @Test
    void theFilesNotReadAreInJsonAndTheExitIsStillZero() throws Exception {
        write("lookup.camel.yaml", LOOKUP);
        write("broken.camel.yaml", "- route: {");

        JsonArray skipped = json(command()).getCollection("skipped");

        assertThat(skipped).singleElement().satisfies(s -> {
            assertThat(((JsonObject) s).getString("file")).isEqualTo("broken.camel.yaml");
            assertThat(((JsonObject) s).getString("reason")).isEqualTo("unparseable");
        });
    }

    @Test
    void aRouteTemplateNamedIsSkippedNotAnError() throws Exception {
        Path template = write("template.camel.yaml", TEMPLATE);
        Topology command = command();
        command.paths = List.of(template.toString());

        assertThat(command.doCall()).isZero();

        assertThat(printer.getOutput()).contains("No routes found in: " + template)
                .contains("template.camel.yaml (route-template)");
    }

    @Test
    void theCommandIsRegisteredAndParsesItsArguments() {
        StringWriter out = new StringWriter();
        CamelJBangMain main = new CamelJBangMain() {
            @Override
            public void quit(int exitCode) {
            }

            @Override
            public void postAddCommands(CommandLine commandLine, String[] args) {
                commandLine.setOut(new PrintWriter(out));
            }
        }.withPrinter(printer);
        main.setDiscoverPlugins(false);

        main.execute("topology", "--help");

        assertThat(out.toString()).contains("topology").contains("--json").contains("<paths>")
                .doesNotContain("Default: []");
    }
}
