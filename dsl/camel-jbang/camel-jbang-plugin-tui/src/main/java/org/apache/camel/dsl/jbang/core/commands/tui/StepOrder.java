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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The steps of an exchange in the order they ran, as the Inspect tab shows them, for the steps of the live trace and of
 * the history alike: the other exchanges it led to are inlined, one level deeper, where they started. An exchange a
 * route calls (to direct:x) goes under the step that sends to the endpoint it came from; an exchange of an EIP that
 * runs its steps on exchanges of its own (a split, a multicast, a circuit breaker) under that EIP.
 */
final class StepOrder {

    /** A step of a trace or of the history. */
    interface Step {
        String exchangeId();

        /** Whether this is the step the route started with (its from). */
        boolean isFirst();

        String routeId();

        String nodeLabel();

        String nodeShortName();

        void setInlineDepth(int depth);
    }

    private StepOrder() {
    }

    /**
     * The steps of the exchange, with the exchanges it led to inlined, from the steps of all of them (each exchange in
     * the order its steps ran). Sets the depth each step is inlined at.
     */
    static <T extends Step> List<T> depthFirst(String exchangeId, List<T> steps) {
        Map<String, List<T>> byExchange = new LinkedHashMap<>();
        for (T s : steps) {
            if (s.exchangeId() != null) {
                byExchange.computeIfAbsent(s.exchangeId(), k -> new ArrayList<>()).add(s);
            }
        }
        // the endpoint each exchange came in from, for the steps that send to it
        Map<String, List<String>> fromIndex = new LinkedHashMap<>();
        // the exchanges an EIP made in a route: they start with a step of that route, not with its from
        Map<String, List<String>> branchChildren = new LinkedHashMap<>();
        for (var entry : byExchange.entrySet()) {
            T first = entry.getValue().get(0);
            if (first.isFirst()) {
                String ep = fromEndpoint(first.nodeLabel());
                if (ep != null) {
                    fromIndex.computeIfAbsent(ep, k -> new ArrayList<>()).add(entry.getKey());
                }
            } else if (first.routeId() != null && toEndpoint(first.nodeLabel()) != null) {
                branchChildren.computeIfAbsent(first.routeId(), k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        List<T> result = new ArrayList<>();
        inline(exchangeId, byExchange, fromIndex, branchChildren, new HashSet<>(), result, 0);
        return result;
    }

    private static <T extends Step> void inline(
            String exchangeId, Map<String, List<T>> byExchange, Map<String, List<String>> fromIndex,
            Map<String, List<String>> branchChildren, Set<String> visited, List<T> result, int depth) {
        if (!visited.add(exchangeId)) {
            return;
        }
        List<T> steps = byExchange.get(exchangeId);
        if (steps == null) {
            return;
        }
        for (T step : steps) {
            step.setInlineDepth(depth);
            result.add(step);
            List<String> children = new ArrayList<>();
            if (runsOnExchangesOfItsOwn(step.nodeShortName())) {
                children.addAll(branchChildren.getOrDefault(step.routeId(), List.of()));
            }
            String target = toEndpoint(step.nodeLabel());
            if (target != null) {
                children.addAll(fromIndex.getOrDefault(target, List.of()));
            }
            for (String child : children) {
                if (!visited.contains(child)) {
                    inline(child, byExchange, fromIndex, branchChildren, visited, result, depth + 1);
                }
            }
        }
    }

    /** The EIPs that run their steps on exchanges of their own: a copy for a circuit breaker. */
    static boolean runsOnExchangesOfItsOwn(String nodeShortName) {
        if (nodeShortName == null) {
            return false;
        }
        return switch (nodeShortName) {
            case "multicast", "recipientList", "split", "routingSlip", "enrich", "pollEnrich", "wireTap",
                    "dynamicRouter", "circuitBreaker" ->
                true;
            default -> false;
        };
    }

    /** The endpoint of a from step (its label is the endpoint), without its options. */
    static String fromEndpoint(String nodeLabel) {
        return nodeLabel != null ? normalize(nodeLabel) : null;
    }

    /** The endpoint a to step (to[direct:a]) sends to, without its options; null for another step. */
    static String toEndpoint(String nodeLabel) {
        if (nodeLabel != null && nodeLabel.startsWith("to[")) {
            int end = nodeLabel.indexOf(']');
            if (end > 3) {
                return normalize(nodeLabel.substring(3, end));
            }
        }
        return null;
    }

    private static String normalize(String endpoint) {
        int q = endpoint.indexOf('?');
        if (q > 0) {
            endpoint = endpoint.substring(0, q);
        }
        return endpoint.replace("://", ":");
    }
}
