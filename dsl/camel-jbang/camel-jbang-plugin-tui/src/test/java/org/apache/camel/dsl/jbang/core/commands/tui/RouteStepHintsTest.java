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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-25161: the decision points of a running route get their path as the project overview gives it in the source,
 * and the label the AI wrote for it in camel-summary.md.
 */
class RouteStepHintsTest {

    @TempDir
    Path dir;

    @AfterEach
    void reset() {
        IntegrationSummaryHints.resetForTesting();
    }

    /** The route tree of the route-structure dev console for a choice, doTry, split and loop. */
    private static RouteInfo route() {
        RouteInfo r = new RouteInfo();
        r.routeId = "a";
        String[][] tree = {
                { "route", "a", "0" }, { "from", "from1", "1" },
                { "choice", "choice1", "2" },
                { "when", "when1", "3" }, { "to", "to1", "4" }, { "filter", "filter1", "4" }, { "to", "to2", "5" },
                { "when", "when2", "3" }, { "to", "to3", "4" },
                { "otherwise", "otherwise1", "3" }, { "to", "to4", "4" },
                { "doTry", "doTry1", "2" }, { "to", "to5", "3" }, { "doCatch", "doCatch1", "3" }, { "to", "to6", "4" },
                { "split", "split1", "2" }, { "to", "to7", "3" },
                { "loop", "loop1", "2" }, { "to", "to8", "3" } };
        for (String[] n : tree) {
            NodeInfo ni = new NodeInfo();
            ni.type = n[0];
            ni.id = n[1];
            ni.code = n[0].equals("when") ? "when[simple{${header.x} > 5}]" : n[0];
            ni.level = Integer.parseInt(n[2]);
            r.nodes.add(ni);
        }
        return r;
    }

    @Test
    void pathsAsTheSourceGivesThem() {
        assertThat(RouteStepHints.paths(route().nodes)).containsExactly(
                Map.entry("choice1", "choice[1]"),
                Map.entry("when1", "choice[1]/when[1]"),
                Map.entry("filter1", "choice[1]/when[1]/filter[1]"),
                Map.entry("when2", "choice[1]/when[2]"),
                Map.entry("otherwise1", "choice[1]/otherwise[1]"),
                Map.entry("doTry1", "doTry[1]"),
                Map.entry("doCatch1", "doTry[1]/doCatch[1]"),
                Map.entry("split1", "split[1]"),
                Map.entry("loop1", "loop[1]"));
    }

    @Test
    void theBusinessViewShowsTheLabel() throws Exception {
        Files.writeString(dir.resolve(IntegrationSummary.FILE_NAME), """
                <!-- camel-summary fingerprint="x" model="m" date="2026-09-30" -->
                # Integration summary: test

                ## Steps %s

                <!-- ai:begin -->
                - `a` `choice[1]/when[2]`: Rush orders | Rush orders skip the queue.
                <!-- ai:end -->
                """.formatted(IntegrationSummary.AI_MARK));
        RouteInfo r = route();
        NodeInfo when2 = r.nodes.get(7);
        Map<String, IntegrationSummary.StepLabel> labels = RouteStepHints.apply(List.of(r), dir, true);
        assertThat(labels).containsOnlyKeys(RouteStepHints.key("a", "when2"));
        assertThat(labels.get(RouteStepHints.key("a", "when2")).why()).isEqualTo("Rush orders skip the queue.");
        assertThat(when2.description).isEqualTo(IntegrationSummaryHints.MARK + "Rush orders");
        // the technical view keeps the code
        RouteInfo technical = route();
        RouteStepHints.apply(List.of(technical), dir, false);
        assertThat(technical.nodes.get(7).description).isNull();
        // with the AI hints off there is nothing
        IntegrationSummaryHints.setShown(false);
        RouteInfo off = route();
        assertThat(RouteStepHints.apply(List.of(off), dir, true)).isEmpty();
        assertThat(off.nodes.get(7).description).isNull();
    }
}
