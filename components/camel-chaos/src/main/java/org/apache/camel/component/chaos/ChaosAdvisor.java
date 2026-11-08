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
                LOG.debug("No chaos suggestions for route {}: {}", route.getRouteId(), e.getMessage(), e);
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
