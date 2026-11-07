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
package org.apache.camel.component.chaos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.builder.EndpointProducerBuilder;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks what the target of a rule finds in the context now, for the warning about a rule that matches nothing (often a
 * typo). The warning names some of what is there, so a person or a tool can fix the pattern.
 */
final class ChaosTargets {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosTargets.class);

    // the most endpoints, routes or steps a warning names
    private static final int NAMED = 5;

    private ChaosTargets() {
    }

    /**
     * Why the rule finds nothing to fire at in the context now, or null when it finds something. A lifecycle rule is
     * not checked: its route was checked when it was added.
     */
    static String check(CamelContext camelContext, ChaosRule rule) {
        if (rule.lifecycle != null) {
            return null;
        }
        String answer = switch (rule.target) {
            case ENDPOINT, COMPONENT -> checkEndpoints(camelContext, rule);
            case ROUTE -> checkRoutes(camelContext, rule);
            case STEP -> checkSteps(camelContext, rule);
        };
        return answer == null && rule.in != null ? checkIn(camelContext, rule) : answer;
    }

    /**
     * Why a rule with {@code in=} finds nothing to fire at in its routes: no route matches {@code in=}, or its target
     * matches nothing inside those routes (a send, a step, or the poll of the consumer of the route). A send inside a
     * route that one of them calls ({@code direct:}) is in that other route.
     */
    private static String checkIn(CamelContext camelContext, ChaosRule rule) {
        List<Route> routes = new ArrayList<>();
        TreeSet<String> ids = new TreeSet<>();
        for (Route route : camelContext.getRoutes()) {
            ids.add(route.getRouteId());
            if (rule.matchesIn(route.getRouteId())) {
                routes.add(route);
            }
        }
        if (routes.isEmpty()) {
            return "in=" + rule.in + " matches no route of the context now, so the rule fires 0 times; the routes are: "
                   + some(ids);
        }
        String in = (routes.size() == 1 ? "route " : "routes ") + rule.in + " (in=" + rule.in + ")";
        if (rule.target == ChaosRule.Target.STEP) {
            TreeSet<String> steps = new TreeSet<>();
            for (Route route : routes) {
                for (Processor processor : route.filter("*")) {
                    String id = processor instanceof IdAware idAware ? idAware.getId() : null;
                    if (id != null) {
                        if (rule.matchesId(id)) {
                            return null;
                        }
                        steps.add(id);
                    }
                }
            }
            return "step=" + rule.pattern + " matches no step of " + in + ", so the rule fires 0 times; the steps there"
                   + " are: " + some(steps);
        }
        if (rule.hooks.contains(Hook.DLQ)) {
            // the dead letter channel is the error handler's, not a send of the route
            return null;
        }
        Reach reach;
        try {
            reach = reach(camelContext, routes);
        } catch (RuntimeException e) {
            // the routes changed while they were read (such as a reload)
            LOG.debug("Cannot read what the routes of in={} reach for the chaos warning (the routes may have changed): {}",
                    rule.in, e.getMessage(), e);
            return null;
        }
        if (!hooksThatCanFire(reach, rule).isEmpty()) {
            return null;
        }
        TreeSet<String> uris = new TreeSet<>();
        reach.routes().values().forEach(r -> r.sentTo().forEach(uri -> uris.add(withoutQuery(uri))));
        return rule.target.key + "=" + rule.pattern + " matches no " + (rule.hooks.contains(Hook.POLL)
                ? "send or poll"
                : "send")
               + " of " + in + ", so the rule fires 0 times (in= is the route of the exchange, not the routes"
               + " it calls); the sends there are: " + some(uris);
    }

    private static String checkEndpoints(CamelContext camelContext, ChaosRule rule) {
        Collection<Endpoint> endpoints = camelContext.getEndpoints();
        boolean any = false;
        boolean polled = false;
        TreeSet<String> uris = new TreeSet<>();
        for (Endpoint endpoint : endpoints) {
            String uri = ChaosPatterns.endpointUri(endpoint);
            uris.add(withoutQuery(uri));
            if (rule.matchesUri(uri)) {
                any = true;
            }
        }
        if (!any) {
            return rule.target.key + "=" + rule.pattern + " matches no endpoint of the context now, so the rule fires 0"
                   + " times (a toD creates its endpoints when it sends); the endpoints are: " + some(uris);
        }
        if (rule.hooks.equals(Set.of(Hook.POLL))) {
            for (Route route : camelContext.getRoutes()) {
                if (route.getConsumer() instanceof ScheduledPollConsumer
                        && rule.matchesUri(ChaosPatterns.endpointUri(route.getEndpoint()))) {
                    polled = true;
                }
            }
            if (!polled) {
                return "at=poll: no route consumes an endpoint that matches " + rule.target.key + "=" + rule.pattern
                       + " with a scheduled poll (consumers such as Kafka and JMS do not poll on a schedule), so the rule"
                       + " fires 0 times";
            }
        }
        return null;
    }

    /**
     * The hooks of the rule that can fire in the context now, for the list and the report: of the hooks of an
     * {@code endpoint=} or {@code component=} rule, {@code poll} only when a route that consumes a matching endpoint
     * polls on a schedule ({@link ScheduledPollConsumer}), and {@code send} (and {@code reply}, the reply of a send)
     * only when something sends to a matching endpoint. A hook where the rule fired already can fire, whatever the
     * context has now. The other hooks, and the hooks of the other targets, are those of the rule.
     */
    static Set<Hook> hooksThatCanFire(Reach reach, ChaosRule rule) {
        if ((rule.target != ChaosRule.Target.ENDPOINT && rule.target != ChaosRule.Target.COMPONENT)
                || rule.lifecycle != null || reach == null) {
            return rule.hooks;
        }
        // with in=, what the routes of in= reach, else what all of them do (and the sends of a ProducerTemplate)
        List<String> polled = reach.polled;
        List<String> sentTo = reach.sentTo;
        List<String> dynamic = reach.dynamic;
        if (rule.in != null) {
            polled = new ArrayList<>();
            sentTo = new ArrayList<>();
            dynamic = new ArrayList<>();
            for (Map.Entry<String, RouteReach> entry : reach.routes.entrySet()) {
                if (rule.matchesIn(entry.getKey())) {
                    RouteReach r = entry.getValue();
                    if (r.polled() != null) {
                        polled.add(r.polled());
                    }
                    sentTo.addAll(r.sentTo());
                    dynamic.addAll(r.dynamic());
                }
            }
        }
        Set<Hook> answer = EnumSet.noneOf(Hook.class);
        Boolean sent = null;
        for (Hook hook : rule.hooks) {
            boolean can = switch (hook) {
                case POLL -> polled.stream().anyMatch(rule::matchesUri);
                case SEND, REPLY -> {
                    if (sent == null) {
                        sent = sentTo.stream().anyMatch(rule::matchesUri)
                                || dynamic.stream().anyMatch(uri -> mayMatch(rule, uri));
                    }
                    yield sent;
                }
                default -> true;
            };
            if (can || rule.firedAt(hook) > 0) {
                answer.add(hook);
            }
        }
        return answer;
    }

    /**
     * What the routes of the context reach now, read once for all the rules of a report: the URIs of the endpoints
     * something sends to, the URIs of the sends that are known only when they send (toD, wireTap, enrich,
     * recipientList, routingSlip, dynamicRouter), and the URIs of the endpoints that a route consumes with a scheduled
     * poll. Null before the routes are there (the context did not start yet), as then nothing is known.
     */
    static Reach reach(CamelContext camelContext) {
        List<Route> routes = camelContext.getRoutes();
        if (routes.isEmpty() && !camelContext.isStarted()) {
            return null;
        }
        try {
            return reach(camelContext, routes);
        } catch (RuntimeException e) {
            // the routes changed while they were read (such as a reload): the hooks of the rules, as before the start
            LOG.debug("Cannot read what the routes reach for the chaos report (the routes may have changed): {}",
                    e.getMessage(), e);
            return null;
        }
    }

    private static Reach reach(CamelContext camelContext, List<Route> routes) {
        Set<Endpoint> consumed = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Endpoint> sentTo = Collections.newSetFromMap(new IdentityHashMap<>());
        List<String> polled = new ArrayList<>();
        List<String> dynamic = new ArrayList<>();
        Map<String, RouteReach> byRoute = new LinkedHashMap<>();
        for (Route route : routes) {
            Endpoint from = route.getEndpoint();
            String polledHere = null;
            if (from != null) {
                consumed.add(from);
                if (route.getConsumer() instanceof ScheduledPollConsumer) {
                    polledHere = ChaosPatterns.endpointUri(from);
                    polled.add(polledHere);
                }
            }
            List<String> sentHere = new ArrayList<>();
            for (Processor processor : route.filter("*")) {
                // a to, whose endpoint is known when the route starts (also one a route consumes, such as direct)
                if (processor instanceof EndpointAware aware && aware.getEndpoint() != null) {
                    sentTo.add(aware.getEndpoint());
                    sentHere.add(ChaosPatterns.endpointUri(aware.getEndpoint()));
                }
            }
            List<String> dynamicHere = new ArrayList<>();
            if (route.getRoute() instanceof RouteDefinition definition) {
                dynamicSends(definition.getOutputs(), dynamicHere);
                dynamic.addAll(dynamicHere);
            }
            byRoute.put(route.getRouteId(), new RouteReach(sentHere, dynamicHere, polledHere));
        }
        List<String> uris = new ArrayList<>();
        for (Endpoint endpoint : camelContext.getEndpoints()) {
            // an endpoint that no route consumes is there for a send: a to, a toD or a template that sent to it
            if (!consumed.contains(endpoint) || sentTo.contains(endpoint)) {
                uris.add(ChaosPatterns.endpointUri(endpoint));
            }
        }
        return new Reach(uris, dynamic, polled, byRoute);
    }

    private static void dynamicSends(List<ProcessorDefinition<?>> outputs, List<String> uris) {
        for (ToDynamicDefinition to : ProcessorDefinitionHelper.filterTypeInOutputs(outputs, ToDynamicDefinition.class)) {
            String uri = to.getUri();
            EndpointProducerBuilder builder = to.getEndpointProducerBuilder();
            if (uri == null && builder != null) {
                uri = builder.getRawUri();
            }
            if (uri != null) {
                uris.add(uri);
            }
        }
        for (Class<? extends ExpressionNode> type : List.of(EnrichDefinition.class, RecipientListDefinition.class,
                RoutingSlipDefinition.class, DynamicRouterDefinition.class)) {
            for (ExpressionNode node : ProcessorDefinitionHelper.filterTypeInOutputs(outputs, type)) {
                ExpressionDefinition expression = node.getExpression();
                String text = expression != null ? expression.getExpression() : null;
                if (text == null && expression != null && expression.getExpressionValue() != null) {
                    // an expression of the Java DSL, such as constant("seda:a,seda:b")
                    text = expression.getExpressionValue().toString();
                }
                if (text == null) {
                    continue;
                }
                // a list of URIs (recipientList, routingSlip), each with a scheme; an expression such as a header
                // name says nothing of where it sends
                for (String uri : text.split(",")) {
                    if (uri.indexOf(':') > 0) {
                        uris.add(uri.trim());
                    }
                }
            }
        }
    }

    /**
     * Whether a rule may match the URI of a send that is known only when it sends, such as
     * {@code http:stock/${header.id}}: the rule matches the text as it is, or the text before its first dynamic part
     * agrees with the text of the pattern before its first {@code *}.
     */
    static boolean mayMatch(ChaosRule rule, String template) {
        if (rule.matchesUri(template)) {
            return true;
        }
        int dynamic = firstDynamic(template);
        if (dynamic < 0) {
            return false;
        }
        String fixed = ChaosPatterns.withoutSlashes(template.substring(0, dynamic));
        String pattern = rule.target == ChaosRule.Target.COMPONENT ? rule.pattern : ChaosPatterns.withoutSlashes(rule.pattern);
        int star = pattern.indexOf('*');
        String literal = star >= 0 ? pattern.substring(0, star) : pattern;
        if (rule.target == ChaosRule.Target.COMPONENT) {
            // the scheme of the send, when it is fixed
            int colon = fixed.indexOf(':');
            return colon < 0 ? literal.startsWith(fixed) : ChaosPatterns.matches(rule.pattern, fixed.substring(0, colon));
        }
        int n = Math.min(fixed.length(), literal.length());
        return fixed.regionMatches(0, literal, 0, n);
    }

    // where the text of a URI becomes dynamic: a simple expression or a property placeholder
    private static int firstDynamic(String uri) {
        int answer = -1;
        for (String start : new String[] { "${", "$simple{", "{{" }) {
            int i = uri.indexOf(start);
            if (i >= 0 && (answer < 0 || i < answer)) {
                answer = i;
            }
        }
        return answer;
    }

    /**
     * What the routes of a context reach, see {@link #reach(CamelContext)}, and what each route does, by its id (for
     * the rules with {@code in=}).
     */
    record Reach(List<String> sentTo, List<String> dynamic, List<String> polled, Map<String, RouteReach> routes) {
    }

    /**
     * What one route reaches: the URIs of the endpoints its steps send to, of the sends known only when they send, and
     * of the endpoint it consumes with a scheduled poll (null when its consumer does not poll on a schedule).
     */
    record RouteReach(List<String> sentTo, List<String> dynamic, String polled) {
    }

    private static String checkRoutes(CamelContext camelContext, ChaosRule rule) {
        TreeSet<String> ids = new TreeSet<>();
        for (Route route : camelContext.getRoutes()) {
            if (rule.matchesId(route.getRouteId())) {
                return null;
            }
            ids.add(route.getRouteId());
        }
        return "route=" + rule.pattern + " matches no route of the context now, so the rule fires 0 times; the routes are: "
               + some(ids);
    }

    private static String checkSteps(CamelContext camelContext, ChaosRule rule) {
        TreeSet<String> ids = new TreeSet<>();
        for (Route route : camelContext.getRoutes()) {
            for (Processor processor : route.filter("*")) {
                String id = processor instanceof IdAware idAware ? idAware.getId() : null;
                if (id != null) {
                    if (rule.matchesId(id)) {
                        return null;
                    }
                    ids.add(id);
                }
            }
        }
        return "step=" + rule.pattern + " matches no step of the context now, so the rule fires 0 times (a step id is"
               + " such as to1, or the id set with id(...)); the steps are: " + some(ids);
    }

    // the endpoints are named as Camel shows them elsewhere (a pattern matches them with or without the // after the
    // scheme), and without their query, which can hold secrets
    static String withoutQuery(String uri) {
        int query = uri.indexOf('?');
        return URISupport.sanitizeUri(query > 0 ? uri.substring(0, query) : uri);
    }

    private static String some(TreeSet<String> names) {
        if (names.isEmpty()) {
            return "none";
        }
        StringJoiner sj = new StringJoiner(", ");
        names.stream().limit(NAMED).forEach(sj::add);
        int more = names.size() - NAMED;
        return more > 0 ? sj + " and " + more + " more" : sj.toString();
    }
}
