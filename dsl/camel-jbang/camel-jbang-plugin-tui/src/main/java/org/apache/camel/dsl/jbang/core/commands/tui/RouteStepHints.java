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

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.diagram.RouteDiagramLayoutEngine.NodeInfo;
import org.apache.camel.diagram.RouteDiagramLayoutEngine.RouteInfo;
import org.apache.camel.dsl.jbang.core.commands.ai.IntegrationSummary;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteDecisions;

/**
 * The AI labels of the decision points of the running routes (CAMEL-25161). The route tree of a running route gives its
 * steps with their nesting level; the path of a decision point in it ({@code choice[1]/when[2]}) is the one the project
 * overview gives it in the source, which is how camel-summary.md addresses its label.
 */
final class RouteStepHints {

    private RouteStepHints() {
    }

    /** The path of each decision point of a running route, by node id, in route order. */
    static Map<String, String> paths(List<NodeInfo> nodes) {
        Map<String, String> answer = new LinkedHashMap<>();
        record Open(int level, RouteDecisions.Scope scope) {
        }
        Deque<Open> open = new ArrayDeque<>();
        open.push(new Open(-1, RouteDecisions.Scope.route()));
        for (NodeInfo n : nodes) {
            while (open.size() > 1 && open.peek().level() >= n.level) {
                open.pop();
            }
            RouteDecisions.Scope scope = open.peek().scope();
            if (n.type != null && RouteDecisions.TYPES.contains(n.type)) {
                scope = scope.child(n.type);
                if (n.id != null) {
                    answer.put(n.id, scope.path());
                }
            }
            // the steps below a node count under the nearest decision point above them
            open.push(new Open(n.level, scope));
        }
        return answer;
    }

    /**
     * Finds the AI labels of the decision points of the routes; in the business view a decision point without a
     * description of its own shows its label, marked.
     *
     * @return the label of each node that has one
     */
    static Map<NodeInfo, IntegrationSummary.StepLabel> apply(List<RouteInfo> routes, Path dir, boolean business) {
        Map<NodeInfo, IntegrationSummary.StepLabel> answer = new IdentityHashMap<>();
        if (dir == null || !IntegrationSummaryHints.enabled()) {
            return answer;
        }
        for (RouteInfo r : routes) {
            Map<String, String> paths = paths(r.nodes);
            for (NodeInfo n : r.nodes) {
                String path = n.id != null ? paths.get(n.id) : null;
                IntegrationSummary.StepLabel st = path != null ? IntegrationSummaryHints.step(dir, r.routeId, path) : null;
                if (st == null) {
                    continue;
                }
                answer.put(n, st);
                if (business && st.label() != null && (n.description == null || n.description.isBlank())) {
                    n.description = IntegrationSummaryHints.MARK + st.label();
                }
            }
        }
        return answer;
    }
}
