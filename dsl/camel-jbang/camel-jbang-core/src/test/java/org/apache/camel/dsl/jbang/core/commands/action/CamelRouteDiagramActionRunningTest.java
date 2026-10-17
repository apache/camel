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

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CliException;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * camel cmd route-diagram of a running integration, as text (the diagrams as images need a terminal with graphics).
 */
@ExtendWith(MockitoExtension.class)
class CamelRouteDiagramActionRunningTest extends ActionCommandTestSupport {

    @TempDir
    Path dir;

    @Test
    void asciiDiagramOfTheRoutes() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelRouteDiagramAction command = command("--theme=ascii");

        assertThat(callWithResponse(command::doWatchCall, structure("route1", "route2"))).isZero();

        assertThat(readActionFile(TEST_PID).getString("action")).isEqualTo("route-structure");
        assertThat(printer.getOutput()).contains("route1", "route2", "timer:tick", "log:out").doesNotContain("─");
    }

    @Test
    void unicodeDiagramByDefault() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command()::doWatchCall, structure("route1"))).isZero();

        assertThat(printer.getOutput()).contains("route1", "─");
    }

    @Test
    void filterDrawsTheMatchingRoutes() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--theme=ascii", "--filter=route2")::doWatchCall,
                structure("route1", "route2"))).isZero();

        assertThat(printer.getOutput()).contains("route2").doesNotContain("route1");
    }

    @Test
    void filterThatMatchesNoRouteIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelRouteDiagramAction command = command("--theme=ascii", "--filter=orders");

        assertThatThrownBy(() -> callWithResponse(command::doWatchCall, structure("route1")))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No route matching: orders");
    }

    @Test
    void highlightOfNodesThatNoRouteHasIsNotFound() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelRouteDiagramAction command = command("--theme=ascii", "--highlight=nope");

        // the diagram was drawn without anything highlighted
        assertThatThrownBy(() -> callWithResponse(command::doWatchCall, structure("route1")))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.NOT_FOUND))
                .hasMessage("No route has the nodes to highlight: nope");
        assertThat(printer.getStdout()).isEmpty();
    }

    @Test
    void highlightDrawsTheRoutesWithTheNodesHighlighted() throws Exception {
        writeStatusFile(TEST_PID, "myApp");

        assertThat(callWithResponse(command("--theme=ascii", "--highlight=to2")::doWatchCall,
                structure("route1", "route2"))).isZero();

        assertThat(printer.getOutput()).contains("route1", "route2");
    }

    @Test
    void textDiagramIntoAFile() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        Path png = dir.resolve("out").resolve("diagram.png");

        assertThat(callWithResponse(command("--theme=ascii", "--output=" + png)::doWatchCall, structure("route1")))
                .isZero();

        // a text theme writes text, next to where the image would go
        Path txt = dir.resolve("out").resolve("diagram.txt");
        assertThat(Files.readString(txt)).contains("route1", "timer:tick");
        assertThat(printer.getOutput()).contains("Diagram saved to: " + txt.toAbsolutePath());
    }

    @Test
    void anIntegrationWithoutRoutesIsAFailure() throws Exception {
        writeStatusFile(TEST_PID, "myApp");
        CamelRouteDiagramAction command = command("--theme=ascii");

        assertThatThrownBy(() -> callWithResponse(command::doWatchCall, structure()))
                .isInstanceOfSatisfying(CliException.class,
                        e -> assertThat(e.getExitCode()).isEqualTo(CliExitCode.FAILURE))
                .hasMessage("No routes found");
    }

    private CamelRouteDiagramAction command(String... args) {
        CamelRouteDiagramAction command = new CamelRouteDiagramAction(new CamelJBangMain().withPrinter(printer));
        CommandLine.populateCommand(command, args);
        command.files = List.of("myApp");
        return command;
    }

    // the answer of the route-structure action: each route from a timer to a log
    private static JsonObject structure(String... routeIds) {
        JsonArray routes = new JsonArray();
        int n = 0;
        for (String routeId : routeIds) {
            n++;
            JsonArray code = new JsonArray();
            code.add(line("from", "timer:tick", 0, "from" + n));
            code.add(line("to", "log:out", 1, "to" + n));
            JsonObject route = new JsonObject();
            route.put("routeId", routeId);
            route.put("code", code);
            routes.add(route);
        }
        JsonObject answer = new JsonObject();
        answer.put("routes", routes);
        return answer;
    }

    private static JsonObject line(String type, String code, int level, String id) {
        JsonObject line = new JsonObject();
        line.put("type", type);
        line.put("code", code);
        line.put("level", level);
        line.put("id", id);
        return line;
    }
}
