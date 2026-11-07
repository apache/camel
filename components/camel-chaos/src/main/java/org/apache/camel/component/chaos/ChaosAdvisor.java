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
import java.util.concurrent.atomic.LongAdder;

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

/**
 * What to try (CAMEL-26727): chaos rules suggested per route from what the route does, ranked, each a known rule of the
 * chaos model ({@link ChaosModel#recipeList()}) with its target filled in and a line that says why. The facts come from
 * the running routes: their sends, their circuit breakers, their dead letter channel, their input, and the hand-offs
 * between them.
 */
final class ChaosAdvisor {

    /** The most suggestions in all, so a big integration does not flood a tool. */
    static final int MAX = 40;

    // the inputs that can deliver a message again (at least once), so a call out may be made twice
    private static final Set<String> AT_LEAST_ONCE = Set.of(
            "kafka", "jms", "activemq", "activemq6", "amqp", "sjms", "sjms2", "aws2-sqs", "google-pubsub",
            "spring-rabbitmq", "rabbitmq", "nats", "paho", "paho-mqtt5", "azure-servicebus", "ibm-mq");
    // a hand-off to another route of the same application, which does not wait for it
    private static final Set<String> HAND_OFF = Set.of("seda", "vm", "disruptor", "disruptor-vm");

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
     */
    record Suggestion(String route, Kind kind, String recipe, String title, String rule, String why, int rank) {
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
    static List<RouteTried> tried(List<ChaosRule> rules, Map<String, Set<Kind>> suggested, long now) {
        // route -> kind -> the rules of that kind that fired in the route
        Map<String, Map<Kind, List<ChaosRule>>> byRoute = new LinkedHashMap<>();
        suggested.keySet().forEach(route -> byRoute.put(route, new LinkedHashMap<>()));
        for (ChaosRule rule : rules) {
            for (Map.Entry<String, LongAdder> e : rule.firedInRoute.entrySet()) {
                if (e.getValue().sum() > 0) {
                    byRoute.computeIfAbsent(e.getKey(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(kindOf(rule), k -> new ArrayList<>()).add(rule);
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
                    fired += rule.firedInRoute.get(route).sum();
                    recovered += rule.effect.count(ChaosRule.Effect.Outcome.RECOVERED);
                    handled += rule.effect.count(ChaosRule.Effect.Outcome.HANDLED);
                    failed += rule.effect.count(ChaosRule.Effect.Outcome.FAILED);
                    if (rule.expectation != null) {
                        expectations.add(rule.expectation.spec() + " " + rule.expectation.read(now).outcome().key);
                    }
                }
                tried.add(new KindTried(k.getKey(), ids, fired, recovered, handled, failed, expectations));
            }
            List<Kind> notTried = new ArrayList<>();
            for (Kind kind : Kind.values()) {
                if (suggested.getOrDefault(route, Set.of()).contains(kind) && !entry.getValue().containsKey(kind)) {
                    notTried.add(kind);
                }
            }
            if (!tried.isEmpty() || !notTried.isEmpty()) {
                answer.add(new RouteTried(route, tried, notTried));
            }
        }
        return answer;
    }

    /** The kinds of fault the suggestions offer per route, in the order of the routes. */
    static Map<String, Set<Kind>> suggestedKinds(List<Suggestion> suggestions) {
        Map<String, Set<Kind>> answer = new LinkedHashMap<>();
        for (Suggestion s : suggestions) {
            answer.computeIfAbsent(s.route(), k -> new LinkedHashSet<>()).add(s.kind());
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
        List<Suggestion> answer = new ArrayList<>();
        for (Route route : routes) {
            try {
                suggest(route, recipes, consumers, answer);
            } catch (RuntimeException e) {
                // the route changed while it was read (such as a reload): nothing to say for it now
            }
        }
        answer.sort(Comparator.comparingInt(Suggestion::rank).reversed());
        return answer.size() > MAX ? List.copyOf(answer.subList(0, MAX)) : answer;
    }

    private static void suggest(
            Route route, Map<String, ChaosModel.Recipe> recipes, Map<String, String> consumers, List<Suggestion> answer) {
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
            if (ChaosRuleParser.HTTP_SCHEMES.contains(scheme)) {
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

        // the dead letter channel of the route
        if (route.getErrorHandlerFactory() instanceof DeadLetterChannelDefinition dlc && dlc.getDeadLetterUri() != null) {
            String dead = dlc.getDeadLetterUri().indexOf('?') > 0
                    ? dlc.getDeadLetterUri().substring(0, dlc.getDeadLetterUri().indexOf('?')) : dlc.getDeadLetterUri();
            add(answer, recipes, "dlq-unreachable", id, Kind.DEAD_LETTER, Map.of("endpoint", dead),
                    "route " + id + " sends what fails to the dead letter channel " + dead
                                                                                                    + ": when that fails too, is the message lost? Add it with a rule that fails the route",
                    78);
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

    /** A suggestion from a known rule, with its placeholders filled in: the given values, else their defaults. */
    private static void add(
            List<Suggestion> answer, Map<String, ChaosModel.Recipe> recipes, String recipeId, String route, Kind kind,
            Map<String, String> values, String why, int rank) {
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
        answer.add(new Suggestion(route, kind, recipe.id(), recipe.title(), rule, why, rank));
    }
}
