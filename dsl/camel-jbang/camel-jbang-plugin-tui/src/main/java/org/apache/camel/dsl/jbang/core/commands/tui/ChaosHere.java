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

import java.util.List;

import dev.tamboui.text.Span;
import dev.tamboui.tui.event.KeyEvent;

/**
 * Chaos from where you look ({@code !}): on the Endpoint tab, the Consumers tab and a node of the Diagram, the key
 * opens the known chaos rules (the Chaos screen, {@link ChaosRecipeForm}) for what is selected, with it filled in: the
 * endpoint a step sends to, the endpoint a route polls, the route, or the step. Only when chaos is on in the
 * integration.
 */
final class ChaosHere {

    /**
     * What is selected, as targets of a chaos rule.
     *
     * @param label    what the user sees, for the title (such as {@code to http://localhost:8765/stock})
     * @param endpoint the endpoint a step sends to, without its query, or null
     * @param polled   the endpoint a route consumes with a scheduled poll, without its query, or null
     * @param route    the route, or null
     * @param step     the id of the step, or null
     */
    record Target(String label, String endpoint, String polled, String route, String step) {

        /** The target as the start of a rule typed as one line. */
        String rulePrefix() {
            if (step != null) {
                return "step=" + step + " ";
            }
            if (endpoint != null) {
                return "endpoint=" + endpoint + " ";
            }
            if (polled != null) {
                return "endpoint=" + polled + " ";
            }
            return route != null ? "route=" + route + " " : "";
        }
    }

    private ChaosHere() {
    }

    /** Whether {@code !} works for the integration: chaos is on. */
    static boolean available(IntegrationInfo info) {
        return info != null && info.chaos != null && info.chaos.enabled;
    }

    /**
     * Handles {@code !}: opens the Chaos screen for the target.
     *
     * @return whether the key was used
     */
    static boolean handleKey(KeyEvent ke, MonitorContext ctx, IntegrationInfo info, Target target) {
        if (!ke.isChar('!') || !available(info) || target == null || ctx.chaosHere == null) {
            return false;
        }
        ctx.chaosHere.accept(target);
        return true;
    }

    /** The {@code !} key in the footer, when it works. */
    static void hint(List<Span> spans, IntegrationInfo info) {
        if (available(info)) {
            TuiHelper.hint(spans, "!", "chaos");
        }
    }

    /** An endpoint of the Endpoint tab: what its routes send to, or what a route consumes from it. */
    static Target forEndpoint(EndpointInfo ep, IntegrationInfo info) {
        if (ep == null || ep.uri == null) {
            return null;
        }
        String uri = withoutQuery(ep.uri);
        if ("in".equalsIgnoreCase(ep.direction)) {
            return new Target("from " + uri, null, polled(info, ep.uri), ep.routeId, null);
        }
        return new Target("to " + uri, uri, null, null, null);
    }

    /** A consumer of the Consumers tab: its route, and its endpoint when it polls. */
    static Target forConsumer(ConsumerInfo consumer) {
        if (consumer == null) {
            return null;
        }
        String uri = consumer.uri != null ? withoutQuery(consumer.uri) : null;
        return new Target(
                "route " + consumer.id + (uri != null ? " (" + uri + ")" : ""), null,
                consumer.polls ? uri : null, consumer.id, null);
    }

    /**
     * A node of a route on the Diagram: the step, and the endpoint it sends to (the uri of the node, else the producer
     * of the step); the {@code from} of a route is the route, and its endpoint when it polls.
     */
    static Target forDiagramNode(String nodeId, String type, String uri, String routeId, IntegrationInfo info) {
        if ("from".equals(type) || nodeId == null) {
            if (routeId == null) {
                return null;
            }
            String polled = null;
            for (ConsumerInfo c : info.consumers) {
                if (routeId.equals(c.id) && c.polls && c.uri != null) {
                    polled = withoutQuery(c.uri);
                }
            }
            return new Target("route " + routeId, null, polled, routeId, null);
        }
        String endpoint = uri != null && !uri.isBlank() ? withoutQuery(uri) : null;
        for (ProducerInfo p : endpoint == null ? info.producers : List.<ProducerInfo> of()) {
            if (nodeId.equals(p.stepId) && p.uri != null) {
                endpoint = withoutQuery(p.uri);
            }
        }
        // a step offers the faults of its endpoint and its own; the route is the target of the from of the route
        return new Target(
                type + " " + nodeId + (endpoint != null ? " (" + endpoint + ")" : ""), endpoint, null, null,
                nodeId);
    }

    private static String polled(IntegrationInfo info, String uri) {
        for (ConsumerInfo c : info.consumers) {
            if (c.polls && uri.equals(c.uri)) {
                return withoutQuery(uri);
            }
        }
        return null;
    }

    static String withoutQuery(String uri) {
        int q = uri.indexOf('?');
        return q > 0 ? uri.substring(0, q) : uri;
    }
}
