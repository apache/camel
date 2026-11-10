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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
    // the inputs that feed their route by themselves
    private static final Set<String> SELF_FEEDING = Set.of("timer", "scheduler", "cron", "quartz");
    // the HTTP servers, which are fed with an HTTP request: platform-http and rest with the port of the HTTP server of
    // Camel, the others with the address in their URI
    private static final Set<String> HTTP_SERVERS = Set.of("platform-http", "rest");
    private static final Set<String> HTTP_SERVERS_WITH_ADDRESS
            = Set.of("jetty", "netty-http", "undertow", "servlet", "vertx-http");

    /** The kinds of faults, which the report also uses for what has been tried. */
    enum Kind {
        ERRORS("errors"),
        SLOW("slow"),
        LOST("lost"),
        CORRUPT("corrupt"),
        DUPLICATE("duplicate"),
        CONSUMER("consumer"),
        LIFECYCLE("lifecycle"),
        DEAD_LETTER("dead letter channel"),
        // two faults at once (CAMEL-26772): its rules are tried as their own kinds
        COMBINED("combined");

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
     * @param with   the other rules to add with the rule, for two faults at once (CAMEL-26772); empty for one fault
     */
    record Suggestion(String route, Kind kind, String recipe, String title, String rule, String why, int rank,
            List<String> routes, List<String> with) {

        Suggestion(String route, Kind kind, String recipe, String title, String rule, String why, int rank) {
            this(route, kind, recipe, title, rule, why, rank, List.of(route), List.of());
        }

        Suggestion(String route, Kind kind, String recipe, String title, String rule, String why, int rank,
                   List<String> routes) {
            this(route, kind, recipe, title, rule, why, rank, routes, List.of());
        }

        /** The rules to add: the rule, and the rules to add with it. */
        List<String> rules() {
            if (with.isEmpty()) {
                return List.of(rule);
            }
            List<String> answer = new ArrayList<>();
            answer.add(rule);
            answer.addAll(with);
            return List.copyOf(answer);
        }
    }

    /**
     * How to feed a route with messages (CAMEL-26771), so a rule can be tried on it.
     *
     * @param kind {@code self} (the route feeds itself), {@code http}, {@code file} or {@code send}
     * @param hint how, such as the command to run
     * @param real whether the messages go to the real system the route reads, rather than a stub of it
     *             ({@code camel run --stub=remote})
     */
    record Feed(String kind, String hint, boolean real) {
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
            if (s.kind() == Kind.COMBINED) {
                // its rules are tried as their own kinds, which the single suggestions offer
                continue;
            }
            answer.computeIfAbsent(s.route(), k -> new LinkedHashMap<>()).putIfAbsent(s.kind(), s.routes());
        }
        return answer;
    }

    private ChaosAdvisor() {
    }

    /** How to feed the route with messages, or null when it has no input. */
    static Feed feed(Route route) {
        Endpoint from = route.getEndpoint();
        if (from == null) {
            return null;
        }
        String uri = from.getEndpointUri();
        String scheme = ChaosPatterns.scheme(uri);
        if (SELF_FEEDING.contains(scheme)) {
            return new Feed("self", "the route feeds itself (" + scheme + ")", false);
        }
        if (HTTP_SERVERS.contains(scheme)) {
            return new Feed(
                    "http", "curl -X " + httpMethod(uri) + " http://localhost:" + serverPort(route.getCamelContext())
                            + httpPath(uri) + " -d @<message>",
                    false);
        }
        if (HTTP_SERVERS_WITH_ADDRESS.contains(scheme)) {
            String address = ChaosPatterns.withoutSlashes(uri(from)).substring(scheme.length() + 1);
            return new Feed("http", "curl -X POST " + address + " -d @<message>", false);
        }
        if (LOCAL_FILES.contains(scheme)) {
            String folder = ChaosPatterns.withoutSlashes(uri(from)).substring(scheme.length() + 1);
            return new Feed("file", "write a file into " + folder, false);
        }
        // the route id as the endpoint: the send console sends to the input of the route, with all its options
        String name = route.getCamelContext().getName();
        return new Feed(
                "send", "camel cmd send " + name + " --endpoint=" + route.getRouteId()
                        + " --body=<message> --repeat=20 --interval=200ms",
                from.isRemote());
    }

    /**
     * The route of the integration that receives a send: the route that consumes the endpoint (direct, seda), or for an
     * HTTP call to this machine the platform-http route of its path; null when it is another system.
     */
    static String receiver(String uri, Map<String, String> consumers) {
        String route = consumers.get(uri);
        if (route != null) {
            return route;
        }
        String scheme = ChaosPatterns.scheme(uri);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }
        String rest = uri.substring(uri.indexOf("//") + 2);
        int slash = rest.indexOf('/');
        String host = (slash >= 0 ? rest.substring(0, slash) : rest).split(":")[0];
        if (!"localhost".equals(host) && !"127.0.0.1".equals(host) && !"0.0.0.0".equals(host)) {
            return null;
        }
        String path = slash >= 0 ? rest.substring(slash) : "/";
        for (Map.Entry<String, String> e : consumers.entrySet()) {
            if (e.getKey().startsWith("platform-http:") && path.equals(httpPath(e.getKey()))) {
                return e.getValue();
            }
        }
        return null;
    }

    /** The method of a platform-http or rest input: the one it restricts to, else POST. */
    static String httpMethod(String uri) {
        if (uri.startsWith("rest:")) {
            String[] parts = uri.split(":", 3);
            return parts.length == 3 ? parts[1].toUpperCase(Locale.ROOT) : "POST";
        }
        try {
            Object method = URISupport.parseQuery(URISupport.extractQuery(uri)).get("httpMethodRestrict");
            if (method != null) {
                return method.toString().split(",")[0].trim().toUpperCase(Locale.ROOT);
            }
        } catch (Exception e) {
            // no options to read: the default method
        }
        return "POST";
    }

    /** The path of a platform-http or rest input, such as /orders. */
    static String httpPath(String uri) {
        String path;
        if (uri.startsWith("rest:")) {
            String[] parts = uri.split(":", 3);
            path = parts.length == 3 ? parts[2] : "";
        } else {
            path = uri.substring(uri.indexOf(':') + 1);
        }
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        while (path.startsWith("//")) {
            path = path.substring(1);
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    /** The port of the HTTP server of Camel (camel.server.port), 8080 by default. */
    private static String serverPort(CamelContext context) {
        try {
            return context.resolvePropertyPlaceholders("{{camel.server.port:8080}}");
        } catch (Exception e) {
            return "8080";
        }
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
        // the first call to a remote system (not a local file), for the faults to try two at a time
        String call = null;
        boolean httpCall = false;
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
            if (!ChaosStubs.isRemote(to)) {
                continue;
            }
            if (call == null && !LOCAL_FILES.contains(scheme)) {
                call = uri;
                httpCall = ChaosRuleParser.HTTP_SCHEMES.contains(scheme);
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
                int before = answer.size();
                add(answer, recipes, "duplicate", id, Kind.DUPLICATE, Map.of("endpoint", uri),
                        "the input of route " + id + " can deliver a message again: is the call to " + uri
                                                                                               + " safe to make twice?",
                        80);
                // a route of the integration receives the call: the expectation tells whether it did the work twice
                // (CAMEL-26791)
                String receiver = receiver(uri, consumers);
                if (receiver != null && answer.size() > before) {
                    Suggestion d = answer.remove(answer.size() - 1);
                    answer.add(new Suggestion(
                            d.route(), d.kind(), d.recipe(), d.title(), d.rule() + " expect=" + receiver + ":duplicates<=0",
                            d.why() + " Route " + receiver + " receives it, and the expectation counts what it does twice",
                            d.rank(), d.routes(), d.with()));
                }
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

        // two faults at once, after the single ones: most bugs that lose messages need two failures that meet
        if (call != null) {
            String fails = httpCall ? "error status=503" : "error exception=java.net.ConnectException";
            if (dead != null) {
                answer.add(new Suggestion(
                        id, Kind.COMBINED, null, "An outage while the dead letter channel fails",
                        "endpoint=" + call + " " + fails + " until=1m", "route " + id + " sends what fails to the dead"
                                                                        + " letter channel " + dead + ": when " + call
                                                                        + " is down for a minute and the dead letter channel"
                                                                        + " fails too, are messages lost? The report counts"
                                                                        + " what the dead letter channel did not take as lost",
                        52, List.of(id), List.of("endpoint=" + dead + " error at=dlq until=1m")));
            }
            answer.add(new Suggestion(
                    id, Kind.COMBINED, null, "A slow service that also fails",
                    "endpoint=" + call + " delay duration=1s..3s rate=0.5 until=2m",
                    "a call to " + call + " that is slow and fails now and then: do the redeliveries pile up, and how"
                                                                                     + " long does an exchange take? An expectation such as expect="
                                                                                     + id
                                                                                     + ":maxTime<=30s says how long is too long",
                    45, List.of(id), List.of("endpoint=" + call + " " + fails + " rate=0.2 until=2m")));
        }

        // the input of the route
        if (from != null && ChaosStubs.isRemote(from)) {
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

    /**
     * Why an expectation of the messages lost between two routes ({@code orders->shipping:lost<=0}) says nothing, or
     * null when it does (CAMEL-26797). Lost counts what the first route completed and the next did not, which tells
     * only when the first hands the message off to the next (over seda, a broker or a wireTap) and goes on: a route
     * that calls the next over direct waits for it and fails with it, and a route that does not send to it at all (such
     * as an HTTP call to a platform-http route) loses nothing that lost counts, while its own failures are not counted.
     * The routes reached through a send that is known only when it sends (toD, wireTap, recipientList...) are not
     * known, so then it says nothing.
     */
    static String lostWithoutHandOff(CamelContext camelContext, ChaosRule.Expectation expectation) {
        if (expectation == null || expectation.toRoute == null || camelContext.getRoute(expectation.route) == null
                || camelContext.getRoute(expectation.toRoute) == null) {
            return null;
        }
        Map<String, String> consumers = new HashMap<>();
        for (Route route : camelContext.getRoutes()) {
            if (route.getEndpoint() != null) {
                consumers.put(uri(route.getEndpoint()), route.getRouteId());
            }
        }
        // the routes the first one reaches, and whether a hand-off was on the way there (one that was wins)
        Map<String, Boolean> reached = new HashMap<>();
        reached.put(expectation.route, false);
        Deque<String> todo = new ArrayDeque<>(List.of(expectation.route));
        while (!todo.isEmpty()) {
            String id = todo.poll();
            Route route = camelContext.getRoute(id);
            if (route == null) {
                continue;
            }
            if (route.getRoute() instanceof RouteDefinition definition) {
                List<String> dynamic = new ArrayList<>();
                ChaosTargets.dynamicSends(definition.getOutputs(), dynamic);
                if (!dynamic.isEmpty()) {
                    return null;
                }
            }
            for (Processor processor : route.filter("*")) {
                if (!(processor instanceof EndpointAware aware) || aware.getEndpoint() == null) {
                    continue;
                }
                String uri = uri(aware.getEndpoint());
                String next = consumers.get(uri);
                if (next == null) {
                    continue;
                }
                boolean handedOff = reached.get(id) || !"direct".equals(ChaosPatterns.scheme(uri));
                Boolean before = reached.get(next);
                if (before == null || (!before && handedOff)) {
                    reached.put(next, handedOff);
                    todo.add(next);
                }
            }
        }
        Boolean handedOff = reached.get(expectation.toRoute);
        if (handedOff == null) {
            return "expects messages lost between routes " + expectation.route + " and " + expectation.toRoute
                   + ", but " + expectation.route + " does not hand messages to " + expectation.toRoute
                   + " (over seda, a broker or a wireTap), so lost counts nothing of what fails in " + expectation.route
                   + ": expect=" + expectation.route + ":failed<=0 counts it";
        }
        if (!handedOff) {
            return "expects messages lost between routes " + expectation.route + " and " + expectation.toRoute
                   + ", but " + expectation.route + " calls " + expectation.toRoute
                   + " over direct and waits for it, so a failure there fails " + expectation.route
                   + " and nothing is lost between them: expect=" + expectation.route + ":failed<=0 counts it";
        }
        return null;
    }

    /** The dead letter channel of the route, without its options, or null when its error handler has none. */
    static String deadLetterUri(Route route) {
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
