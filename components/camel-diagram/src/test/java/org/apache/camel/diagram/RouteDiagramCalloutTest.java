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

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.Callout;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.LayoutRoute;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.diagram.RouteDiagramRenderer.DiagramColors;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The problems of the steps in a route diagram image: callouts beside a route, a numbered list below a route with
 * branches.
 */
class RouteDiagramCalloutTest {

    private static final String GROOVY = "groovy reads fields of the body (body.items), but the body here is the file as"
                                         + " it was read (a GenericFile), not parsed data - unmarshal it first";

    private static NodeInfo node(String type, String code, int level, String... problems) {
        NodeInfo n = new NodeInfo();
        n.type = type;
        n.id = type + level + code.length();
        n.code = code;
        n.level = level;
        n.problems = List.of(problems);
        return n;
    }

    private static RouteInfo orders() {
        RouteInfo r = new RouteInfo();
        r.routeId = "orders";
        r.nodes.add(node("route", "route[orders]", 0));
        r.nodes.add(node("from", "from[file:orders]", 1));
        r.nodes.add(node("setBody", "setBody[groovy{body.items}]", 2, GROOVY));
        r.nodes.add(node("to", "to[http:pricing]", 2, "the http producer cannot send a Map"));
        r.nodes.add(node("to", "to[jms:priced]", 2));
        return r;
    }

    private static RouteInfo choice() {
        RouteInfo r = new RouteInfo();
        r.routeId = "choice";
        r.nodes.add(node("route", "route[choice]", 0));
        r.nodes.add(node("from", "from[timer:tick]", 1));
        r.nodes.add(node("choice", "choice", 2));
        r.nodes.add(node("when", "when[simple{${header.kind} == 0}]", 3));
        r.nodes.add(node("to", "to[kafka:zero?brokerz=localhost]", 4, "kafka has no option brokerz"));
        r.nodes.add(node("otherwise", "otherwise", 3));
        r.nodes.add(node("marshal", "marshal[jackson]", 4, "the body is a String here"));
        return r;
    }

    private static RouteDiagramLayoutEngine engine(boolean problems) {
        RouteDiagramLayoutEngine engine = new RouteDiagramLayoutEngine();
        engine.setCardStyle(true);
        engine.setProblems(problems);
        return engine;
    }

    @Test
    void calloutsBesideARoute() {
        RouteDiagramLayoutEngine engine = engine(true);
        LayoutRoute lr = engine.layoutRoute(orders(), RouteDiagramLayoutEngine.PADDING);

        assertThat(lr.problemList).isFalse();
        assertThat(lr.callouts).hasSize(2);
        Callout groovy = lr.callouts.get(0);
        Callout http = lr.callouts.get(1);
        assertThat(groovy.node.type).isEqualTo("setBody");
        assertThat(groovy.number).isZero();
        // right of the steps, the text wrapped to more than one line
        int right = groovy.node.x + engine.getNodeWidth();
        assertThat(groovy.x).isEqualTo(right + RouteDiagramLayoutEngine.CALLOUT_GAP);
        assertThat(groovy.lines).hasSizeGreaterThan(1);
        assertThat(String.join(" ", groovy.lines)).contains("GenericFile");
        // one below the other, and room for them in the image
        assertThat(http.y).isGreaterThanOrEqualTo(groovy.y + groovy.height);
        assertThat(lr.maxX).isEqualTo(groovy.x + groovy.width);

        // without problems shown, the route does not change
        LayoutRoute plain = engine(false).layoutRoute(orders(), RouteDiagramLayoutEngine.PADDING);
        assertThat(plain.callouts).isEmpty();
        assertThat(plain.maxX).isLessThan(lr.maxX);
    }

    @Test
    void numberedListBelowARouteWithBranches() {
        RouteDiagramLayoutEngine engine = engine(true);
        LayoutRoute plain = engine(false).layoutRoute(choice(), RouteDiagramLayoutEngine.PADDING);
        LayoutRoute lr = engine.layoutRoute(choice(), RouteDiagramLayoutEngine.PADDING);

        assertThat(lr.problemList).isTrue();
        assertThat(lr.callouts).hasSize(2);
        // numbered in the order of the steps in the route, below the steps
        assertThat(lr.callouts.get(0).number).isEqualTo(1);
        assertThat(lr.callouts.get(0).node.type).isEqualTo("to");
        assertThat(lr.callouts.get(1).number).isEqualTo(2);
        assertThat(lr.callouts.get(1).node.type).isEqualTo("marshal");
        assertThat(lr.callouts.get(0).y).isGreaterThan(plain.maxY);
        assertThat(lr.maxY).isGreaterThan(plain.maxY);
    }

    @Test
    void drawTheProblems() {
        DiagramColors colors = DiagramColors.parse("light");
        RouteDiagramLayoutEngine engine = engine(true);
        LayoutRoute lr = engine.layoutRoute(orders(), RouteDiagramLayoutEngine.PADDING);
        BufferedImage image = new RouteDiagramRenderer().renderDiagram(List.of(lr), lr.maxY, colors);

        Callout groovy = lr.callouts.get(0);
        // the callout is filled with the warning background, at its right end where there is no text
        assertThat(new Color(
                image.getRGB(groovy.x + groovy.width - 4 * RouteDiagramLayoutEngine.SCALE,
                        groovy.y + groovy.height / 2)))
                .isEqualTo(colors.getWarningBg());
        // the card of the step has a warning border
        assertThat(new Color(image.getRGB(groovy.node.x + 1, groovy.node.y + groovy.node.height / 2)))
                .isEqualTo(colors.getWarning());
    }
}
