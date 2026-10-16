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
package org.apache.camel.diagram;

import java.io.File;
import java.util.Map;

import javax.imageio.ImageIO;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.spi.DumpRoutesStrategy;
import org.apache.camel.spi.RouteDiagramDumper;
import org.apache.camel.spi.RouteDiagramDumper.NodeLabelMode;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25466: a route diagram draws the steps of a route, and with errors its onException clauses in a frame below
 * them; a topology with errors draws the routes reached only on error in a frame below the others. The same for the
 * dumper, the dev console and its web components.
 */
class DiagramErrorHandlingTest extends CamelTestSupport {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        // as fail-well/error-handling: a dead letter channel and an onException send to parked
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("direct:parked"));
                onException(IllegalStateException.class).handled(true).to("direct:parked");

                from("direct:checkout").routeId("checkout")
                        .to("direct:charge")
                        .to("mock:charged");

                from("direct:charge").routeId("payment").errorHandler(noErrorHandler())
                        .throwException(new IllegalStateException("declined"));

                from("direct:parked").routeId("parked")
                        .to("mock:parked");
            }
        };
    }

    @Test
    void aRouteDiagramDrawsTheStepsOfTheRoute() {
        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);

        String text = dumper.dumpRoutesAsAsciiArt("checkout", NodeLabelMode.CODE, 180, true);

        assertThat(text).contains("to[direct:charge]").doesNotContain("onException").doesNotContain("Error handling");
    }

    @Test
    void withErrorsTheOnExceptionClausesAreInAFrameBelowTheSteps() {
        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);

        String text = dumper.dumpRoutesAsAsciiArt("checkout", NodeLabelMode.CODE, 180, true, true);

        assertThat(text.indexOf("Error handling")).isGreaterThan(text.indexOf("to[direct:charge]"));
        assertThat(text.indexOf("onException")).isGreaterThan(text.indexOf("Error handling"));
        assertThat(text).contains("on failure to parked");
        // a route with noErrorHandler runs none of the onException clauses copied into it
        assertThat(dumper.dumpRoutesAsAsciiArt("payment", NodeLabelMode.CODE, 180, true, true))
                .doesNotContain("onException");
        assertThat(dumper.dumpRoutesAsImage("*", RouteDiagramDumper.Theme.LIGHT, false, NodeLabelMode.CODE, 180, 12, true))
                .isNotNull();
    }

    @Test
    void withErrorsTheTopologyHasTheRoutesReachedOnlyOnErrorInAFrame() {
        RouteDiagramDumper dumper = PluginHelper.getRouteDiagramDumper(context);

        String text = dumper.dumpTopologyAsAsciiArt(180, true, false, true);

        int frame = text.indexOf("Error handling");
        assertThat(frame).isGreaterThan(text.indexOf("payment"));
        assertThat(text.indexOf("parked")).isGreaterThan(frame);
        assertThat(dumper.dumpTopologyAsAsciiArt(180, true, false)).doesNotContain("Error handling");
        assertThat(dumper.dumpTopologyAsImage(RouteDiagramDumper.Theme.LIGHT, false, 180, 12, true, true)).isNotNull();
    }

    @Test
    void theDumpedDiagramsDrawTheErrorHandlingWhenAsked() throws Exception {
        DumpRoutesStrategy drs = context.getCamelContextExtension().getContextPlugin(DumpRoutesStrategy.class);
        drs.setInclude("*");
        drs.setLog(false);
        drs.setOutput("target/error-handling-dump/steps");
        drs.dumpRoutes("png");
        drs.setErrors(true);
        drs.setOutput("target/error-handling-dump/errors");
        drs.dumpRoutes("png");

        // the frame below the steps of checkout, and below the routes of the topology
        for (String png : new String[] { "checkout.png", context.getName() + "-topology.png" }) {
            int steps = ImageIO.read(new File("target/error-handling-dump/steps", png)).getHeight();
            int errors = ImageIO.read(new File("target/error-handling-dump/errors", png)).getHeight();
            assertThat(errors).as(png).isGreaterThan(steps);
        }
    }

    @Test
    void theDevConsoleHasTheErrorsOption() {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("route-diagram");

        String route = (String) console.call(DevConsole.MediaType.TEXT,
                Map.of(DiagramDevConsole.FILTER, "checkout", DiagramDevConsole.FORMAT, "unicode",
                        DiagramDevConsole.ERRORS, "true"));
        assertThat(route).contains("Error handling").contains("onException");
        String topology = (String) console.call(DevConsole.MediaType.TEXT,
                Map.of(DiagramDevConsole.MODE, "topology", DiagramDevConsole.FORMAT, "unicode",
                        DiagramDevConsole.ERRORS, "true"));
        assertThat(topology).contains("Error handling");

        // the web components get it as an attribute; the route diagram takes the lines of its frames from the topology
        String html = (String) console.call(DevConsole.MediaType.TEXT, Map.of(DiagramDevConsole.ERRORS, "true"));
        assertThat(html).contains("<camel-route-diagram").contains(" errors=\"true\" topology=\"route-topology\"");
        String topologyHtml = (String) console.call(DevConsole.MediaType.TEXT,
                Map.of(DiagramDevConsole.MODE, "topology", DiagramDevConsole.ERRORS, "true"));
        assertThat(topologyHtml).contains("<camel-topology-diagram").contains(" errors=\"true\"");
        assertThat((String) console.call(DevConsole.MediaType.TEXT)).doesNotContain(" errors=\"true\"");
    }
}
