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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What to try (CAMEL-26727): chaos rules suggested per route from what the route does, ranked, each a known rule of the
 * chaos model ({@link ChaosModel#recipeList()}) with its target filled in and a line that says why. The facts come from
 * the running routes: their sends, their circuit breakers, their dead letter channel, their input, and the hand-offs
 * between them.
 */
final class ChaosAdvisor {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosAdvisor.class);

    /** The most suggestions in all, so a big integration does not flood a tool. */
    static final int MAX = 40;

    // the inputs that can deliver a message again (at least once), so a call out may be made twice
    private static final Set<String> AT_LEAST_ONCE = Set.of(
            "kafka", "jms", "activemq", "activemq6", "amqp", "sjms", "sjms2", "aws2-sqs", "google-pubsub",
            "spring-rabbitmq", "rabbitmq", "nats", "paho", "paho-mqtt5", "azure-servicebus", "ibm-mq");
    // a hand-off to another route of the same application, which does not wait for it
    private static final Set<String> HAND_OFF = Set.of("seda", "vm", "disruptor", "disruptor-vm");
    // the endpoints that write to the local file system: remote to Camel, but with no connection to refuse
    private static final Set<String> LOCAL_FILES = Set.of("file");

    /** The kinds of faults, which the report also uses for what has been tried. */
    enum Kind {
        ERRORS("errors"),
        SLOW("slow"),
        LOST("lost"),
        CORRUPT("corrupt"),
        DUPLICATE("duplicate"),
        CONSUMER("consumer"),
        LIFECYCLE("lifecycle"),
        DEAD_LETTER("dead letter channel");

        final String key;

        Kind(String key) {
            this.key = key;
        }
    }

    /**
     * A rule to try.
     *
     * @param route  the route it is about
     * @param kind   the kind of fault
     * @param recipe the id of the known rule it comes from (null for one of its own)
     * @param title  the known rule in plain words, such as "Returns an error (HTTP status)"
     * @param rule   the rule, ready to add
     * @param why    why it is worth trying for this route
     * @param rank   higher is tried first
     * @param routes the routes a rule of its kind tries it in: the route, and the other routes of a dead letter channel
     *               they share, which is suggested once
     */
    record Suggestion(String route, Kind kind, String recipe, String title, String rule, String why, int rank,
            List<String> routes) {

        Suggestion(String route, Kind kind, String recipe, String title, String rule, String why, int rank) {
            this(route, kind, recipe, title, rule, why, rank, List.of(route));
        }
    }

    /**
     * A kind of fault tried in a route (CAMEL-26728): by which rules, how often they fired there, how the exchanges
     * they faulted came out (counted per rule, so a rule that fired in more routes counts in each), and the outcomes of
     * their expectations.
     */
    record KindTried(Kind kind, List<String> rules, long fired, long recovered, long handled, long failed,
            List<String> expectations) {
    }

    /** What has been tried in a route, and the kinds that the suggestions offer for it and no rule tried yet. */
    record RouteTried(String route, List<KindTried> tried, List<Kind> notTried) {
    }

    /** The kind of fault of a rule. */
    static Kind kindOf(ChaosRule rule) {
        if (rule.lifecycle != null || rule.fault == ChaosRule.Fault.LIFECYCLE) {
            return Kind.LIFECYCLE;
        }
        if (rule.hooks.size() == 1 && rule.hooks.contains(ChaosRule.Hook.DLQ)) {
            return Kind.DEAD_LETTER;
        }
        boolean consumer = rule.hooks.stream().allMatch(h -> h == ChaosRule.Hook.POLL || h == ChaosRule.Hook.ENTRY);
        return switch (rule.fault) {
            case DELAY -> Kind.SLOW;
            case DROP -> consumer ? Kind.CONSUMER : Kind.LOST;
            case CORRUPT -> Kind.CORRUPT;
            case DUPLICATE -> Kind.DUPLICATE;
            default -> consumer ? Kind.CONSUMER : Kind.ERRORS;
        };
    }

    /**
     * What has been tried per route: the kinds of fault of the rules (current and removed) that fired in it, and the
     * kinds the suggestions offer for it that none tried. The routes in the order of the suggestions, then the others.
     */
    static List<RouteTried> tried(List<ChaosRule> rules, Map<String, Map<Kind, List<String>>> suggested, long now) {
        // route -> kind -> the rules of that kind that fired in the route
        Map<String, Map<Kind, List<ChaosRule>>> byRoute = new LinkedHashMap<>();
        suggested.keySet().forEach(route -> byRoute.put(route, new LinkedHashMap<>()));
        for (ChaosRule rule : rules) {
            boolean fired = false;
            for (Map.Entry<String, ChaosRule.InRoute> e : rule.inRoute.entrySet()) {
                if (e.getValue().fired.sum() > 0) {
                    fired = true;
                    byRoute.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(kindOf(rule), k -> new ArrayList<>()).add(rule);
                }
            }
            // a rule that expects no message lost between two routes (orders->shipping:lost<=0) tries what a hand-off
            // loses for the first route, wherever its faults are: the hand-off suggestion fails the next route
            if (fired && rule.expectation != null && rule.expectation.toRoute != null) {
                List<ChaosRule> lost = byRoute.computeIfAbsent(rule.expectation.route, k -> new LinkedHashMap<>())
                        .computeIfAbsent(Kind.LOST, k -> new ArrayList<>());
                if (!lost.contains(rule)) {
                    lost.add(rule);
                }
            }
        }
        List<RouteTried> answer = new ArrayList<>();
        for (Map.Entry<String, Map<Kind, List<ChaosRule>>> entry : byRoute.entrySet()) {
            String route = entry.getKey();
            List<KindTried> tried = new ArrayList<>();
            for (Map.Entry<Kind, List<ChaosRule>> k : entry.getValue().entrySet()) {
                List<String> ids = new ArrayList<>();
                List<String> expectations = new ArrayList<>();
                long fired = 0;
                long recovered = 0;
                long handled = 0;
                long failed = 0;
                for (ChaosRule rule : k.getValue()) {
                    String id = rule.version > 1 ? rule.id + " v" + rule.version : rule.id;
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                    // the outcomes of the exchanges the rule faulted in this route, not in all of them; a rule that
                    // tries a hand-off for this route (by its expectation) faulted the next route: all of them
                    ChaosRule.InRoute in = rule.inRoute.get(route);
                    for (ChaosRule.InRoute r : in != null ? List.of(in) : List.copyOf(rule.inRoute.values())) {
                        fired += r.fired.sum();
                        recovered += r.effect.count(ChaosRule.Effect.Outcome.RECOVERED);
                        handled += r.effect.count(ChaosRule.Effect.Outcome.HANDLED);
                        failed += r.effect.count(ChaosRule.Effect.Outcome.FAILED);
                    }
                    if (rule.expectation != null) {
                        expectations.add(rule.expectation.spec() + " " + rule.expectation.read(now).outcome().key);
                    }
                }
                tried.add(new KindTried(k.getKey(), ids, fired, recovered, handled, failed, expectations));
            }
            List<Kind> notTried = new ArrayList<>();
            Map<Kind, List<String>> kinds = suggested.getOrDefault(route, Map.of());
            for (Kind kind : Kind.values()) {
                // a kind suggested for several routes (a dead letter channel they share) is tried in any of them
                if (kinds.containsKey(kind) && kinds.get(kind).stream()
                        .noneMatch(r -> byRoute.getOrDefault(r, Map.of()).containsKey(kind))) {
                    notTried.add(kind);
                }
            }
            if (!tried.isEmpty() || !notTried.isEmpty()) {
                answer.add(new RouteTried(route, tried, notTried));
            }
        }
        return answer;
    }

    /**
     * The kinds of fault the suggestions offer per route, in the order of the routes, each with the routes a rule of
     * that kind tries it in.
     */
    static Map<String, Map<Kind, List<String>>> suggestedKinds(List<Suggestion> suggestions) {
        Map<String, Map<Kind, List<String>>> answer = new LinkedHashMap<>();
        for (Suggestion s : suggestions) {
            answer.computeIfAbsent(s.route(), k -> new LinkedHashMap<>()).putIfAbsent(s.kind(), s.routes());
        }
        return answer;
    }

    private ChaosAdvisor() {
    }

    /** The rules to try for the routes of the context, the most worth trying first, at most {@link #MAX}. */
    static List<Suggestion> suggest(CamelContext camelContext) {
        Map<String, ChaosModel.Recipe> recipes = new LinkedHashMap<>();
        for (ChaosModel.Recipe recipe : ChaosModel.recipeList()) {
            recipes.put(recipe.id(), recipe);
        }
        List<Route> routes = camelContext.getRoutes();
        // the routes that consume an endpoint, for the hand-offs
        Map<String, String> consumers = new LinkedHashMap<>();
        for (Route route : routes) {
            if (route.getEndpoint() != null) {
                consumers.put(uri(route.getEndpoint()), route.getRouteId());
            }
        }
        // the routes of each dead letter channel, which is suggested once, not for the route that consumes it
        Map<String, List<String>> deadLetters = new LinkedHashMap<>();
        for (Route route : routes) {
            try {
                String dead = deadLetterUri(route);
                if (dead != null && !route.getRouteId().equals(consumers.get(URISupport.normalizeUri(dead)))) {
                    deadLetters.computeIfAbsent(dead, k -> new ArrayList<>()).add(route.getRouteId());
                }
            } catch (Exception e) {
                LOG.debug("No dead letter channel for route {}: {}", route.getRouteId(), e.getMessage(), e);
            }
        }
        List<Suggestion> answer = new ArrayList<>();
        for (Route route : routes) {
            try {
                suggest(route, recipes, consumers, deadLetters, answer);
            } catch (RuntimeException e) {
                // the route changed while it was read (such as a reload): nothing to say for it now
                LOG.debug("No chaos suggestions for route {}: {}", route.getRouteId(), e.getMessage(), e);
            }
        }
        answer.sort(Comparator.comparingInt(Suggestion::rank).reversed());
        return answer.size() > MAX ? List.copyOf(answer.subList(0, MAX)) : answer;
    }

    private static void suggest(
            Route route, Map<String, ChaosModel.Recipe> recipes, Map<String, String> consumers,
            Map<String, List<String>> deadLetters, List<Suggestion> answer) {
        String id = route.getRouteId();
        Endpoint from = route.getEndpoint();
        boolean breaker = route.getRoute() instanceof RouteDefinition definition
                && !ProcessorDefinitionHelper.filterTypeInOutputs(definition.getOutputs(), CircuitBreakerDefinition.class)
                        .isEmpty();
        boolean atLeastOnce = from != null
                && (AT_LEAST_ONCE.contains(ChaosPatterns.scheme(from.getEndpointUri())) || ChaosConsumers.keepsFailed(from));

        // the sends: each endpoint once
        Set<String> seen = new LinkedHashSet<>();
        for (Processor processor : route.filter("*")) {
            if (!(processor instanceof EndpointAware aware) || aware.getEndpoint() == null) {
                continue;
            }
            Endpoint to = aware.getEndpoint();
            String uri = uri(to);
            if (!seen.add(uri)) {
                continue;
            }
            String scheme = ChaosPatterns.scheme(uri);
            if (HAND_OFF.contains(scheme) && consumers.containsKey(uri) && !id.equals(consumers.get(uri))) {
                String next = consumers.get(uri);
                answer.add(new Suggestion(
                        id, Kind.LOST, "route-error", "The route after a hand-off fails",
                        "route=" + next + " error rate=0.2 until=2m expect=" + id + "->" + next + ":lost<=0 within=3m",
                        "route " + id + " hands the message to route " + next + " over " + scheme + " and does not wait:"
                                                                                                                        + " when "
                                                                                                                        + next
                                                                                                                        + " fails, is the message lost?",
                        85));
                continue;
            }
            if (!to.isRemote()) {
                continue;
            }
            if (LOCAL_FILES.contains(scheme)) {
                // a local file system: nothing connects, but the write can fail
                add(answer, recipes, "write-error", id, Kind.ERRORS, Map.of("endpoint", uri),
                        "a write to " + uri + ": what does the route do when the file cannot be written?", 60);
            } else if (ChaosRuleParser.HTTP_SCHEMES.contains(scheme)) {
                if (breaker) {
                    add(answer, recipes, "outage", id, Kind.ERRORS, Map.of("endpoint", uri),
                            "a call to " + uri + " in a route with a circuit breaker: when the service is down for a"
                                                                                             + " while, does the breaker open, and does the fallback answer?",
                            70);
                } else {
                    add(answer, recipes, "http-error", id, Kind.ERRORS, Map.of("endpoint", uri),
                            "an HTTP call to " + uri + " without a circuit breaker: what does the route do when the"
                                                                                                 + " service answers 503?",
                            90);
                }
                add(answer, recipes, "timeout", id, Kind.SLOW, Map.of("endpoint", uri),
                        "a call to " + uri + " that may not answer in time: does the route give up, and retry?", 65);
            } else {
                add(answer, recipes, "connection-refused", id, Kind.ERRORS, Map.of("endpoint", uri),
                        "a call to " + uri + ": what does the route do when the system is not there?", 75);
                add(answer, recipes, "slow", id, Kind.SLOW, Map.of("endpoint", uri),
                        "a call to " + uri + ": what does a slow answer do to the route and its input?", 50);
            }
            if (atLeastOnce) {
                add(answer, recipes, "duplicate", id, Kind.DUPLICATE, Map.of("endpoint", uri),
                        "the input of route " + id + " can deliver a message again: is the call to " + uri
                                                                                               + " safe to make twice?",
                        80);
            }
        }

        // the dead letter channel of the route: once for the routes that share it, with the first of them
        String dead = deadLetterUri(route);
        List<String> shared = dead != null ? deadLetters.getOrDefault(dead, List.of()) : List.of();
        if (!shared.isEmpty() && id.equals(shared.get(0))) {
            String who = shared.size() == 1 ? "route " + id + " sends" : "routes " + names(shared) + " send";
            add(answer, recipes, "dlq-unreachable", id, Kind.DEAD_LETTER, Map.of("endpoint", dead),
                    who + " what fails to the dead letter channel " + dead
                                                                                                    + ": when that fails too, is the message lost? Add it with a rule that fails a route",
                    78, shared);
        }

        // the input of the route
        if (from != null && from.isRemote()) {
            if (route.getConsumer() instanceof ScheduledPollConsumer) {
                add(answer, recipes, "poll-error", id, Kind.CONSUMER, Map.of("endpoint", uri(from)),
                        "route " + id + " polls " + uri(from)
                                                                                                     + ": does it poll again after a failed poll?",
                        55);
            } else {
                add(answer, recipes, "route-error", id, Kind.CONSUMER, Map.of("route", id),
                        "route " + id + " is called from outside (" + ChaosPatterns.scheme(from.getEndpointUri())
                                                                                            + "): what does the sender see when it fails, a rollback, a retry"
                                                                                            + " or an error?",
                        60);
            }
            add(answer, recipes, "stall", id, Kind.LIFECYCLE, Map.of("route", id),
                    "route " + id + " stops taking messages for a while: does the sender wait, retry or lose them?", 30);
        }
    }

    /**
     * The URI of an endpoint as a rule targets it, without its query: the options say nothing of which endpoint it is,
     * and may hold a secret.
     */
    private static String uri(Endpoint endpoint) {
        String uri = ChaosPatterns.endpointUri(endpoint);
        int query = uri.indexOf('?');
        return query > 0 ? uri.substring(0, query) : uri;
    }

    /** The dead letter channel of the route, without its options, or null when its error handler has none. */
    private static String deadLetterUri(Route route) {
        if (route.getErrorHandlerFactory() instanceof DeadLetterChannelDefinition dlc && dlc.getDeadLetterUri() != null) {
            String uri = dlc.getDeadLetterUri();
            return uri.indexOf('?') > 0 ? uri.substring(0, uri.indexOf('?')) : uri;
        }
        return null;
    }

    /** The first three routes and how many more: orders, inventory, stock and 2 more. */
    private static String names(List<String> routes) {
        if (routes.size() <= 3) {
            return String.join(routes.size() == 2 ? " and " : ", ", routes);
        }
        return String.join(", ", routes.subList(0, 3)) + " and " + (routes.size() - 3) + " more";
    }

    /** A suggestion from a known rule, with its placeholders filled in: the given values, else their defaults. */
    private static void add(
            List<Suggestion> answer, Map<String, ChaosModel.Recipe> recipes, String recipeId, String route, Kind kind,
            Map<String, String> values, String why, int rank) {
        add(answer, recipes, recipeId, route, kind, values, why, rank, List.of(route));
    }

    private static void add(
            List<Suggestion> answer, Map<String, ChaosModel.Recipe> recipes, String recipeId, String route, Kind kind,
            Map<String, String> values, String why, int rank, List<String> routes) {
        ChaosModel.Recipe recipe = recipes.get(recipeId);
        if (recipe == null) {
            return;
        }
        String rule = recipe.rule();
        for (ChaosModel.Placeholder p : recipe.placeholders()) {
            String value = values.getOrDefault(p.name(), p.defaultValue());
            if (value == null) {
                // a placeholder without a value, such as the header to remove: not a rule to add as it is
                return;
            }
            rule = rule.replace("${" + p.name() + "}", value);
        }
        answer.add(new Suggestion(route, kind, recipe.id(), recipe.title(), rule, why, rank, routes));
    }
}
