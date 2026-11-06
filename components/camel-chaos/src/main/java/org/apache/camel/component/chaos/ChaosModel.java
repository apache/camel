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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.component.chaos.ChaosRule.Action;
import org.apache.camel.component.chaos.ChaosRule.CorruptMode;
import org.apache.camel.component.chaos.ChaosRule.Expectation;
import org.apache.camel.component.chaos.ChaosRule.Fault;
import org.apache.camel.component.chaos.ChaosRule.Hook;
import org.apache.camel.component.chaos.ChaosRule.Target;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The model of the chaos rules as data, for the tools that help to write them (the chaos screen of the terminal UI, an
 * AI that turns a prompt into rules, the docs): the targets, the hooks of each target and the faults each hook offers,
 * the parameters of each fault, the general options, the expectations, the limits, and the recipes (known rules in
 * plain words, as templates).
 * <p/>
 * The structure comes from the tables of {@link ChaosRuleParser}, so it cannot drift from what the parser accepts; the
 * descriptions and the recipes are written here. The build writes {@link #FILE} from {@link #toJson()} (the
 * {@code generate-chaos-model} goal of camel-package-maven-plugin), {@code ChaosModelTest} checks that every recipe
 * parses, and the catalog copies the file ({@code CamelCatalog.chaosModelJSon()}).
 */
final class ChaosModel {

    /** Where the model is, in the jar and under {@code src/generated/resources}. */
    static final String FILE = "META-INF/org/apache/camel/chaos/chaos-model.json";

    private ChaosModel() {
    }

    /** The model as JSON, pretty printed (the content of {@link #FILE}); the build calls it. */
    static String toJson() {
        return Jsoner.prettyPrint(model().toJson(), 2) + "\n";
    }

    static JsonObject model() {
        JsonObject chaos = new JsonObject();
        chaos.put("description", "The chaos rules of camel-chaos: a rule is one line, the target, the fault and its"
                                 + " parameters as name=value, then the options (such as at, rate, from, until) and"
                                 + " optionally an expectation");
        chaos.put("syntax", "<target>=<pattern> <fault> [<parameter>=<value> ...] [<option>=<value> ...]");
        chaos.put("targets", targets());
        chaos.put("hooks", hooks());
        chaos.put("faults", faults());
        chaos.put("options", options());
        chaos.put("expectation", expectation());
        chaos.put("limits", limits());
        chaos.put("recipes", recipes());
        JsonObject root = new JsonObject();
        root.put("chaos", chaos);
        return root;
    }

    // ---- the targets and the hooks ----

    private static JsonArray targets() {
        JsonArray answer = new JsonArray();
        for (Target target : Target.values()) {
            JsonObject jo = new JsonObject();
            jo.put("name", target.key);
            switch (target) {
                case ENDPOINT -> {
                    jo.put("description", "An endpoint URI pattern, with * for any characters; matched with or without"
                                          + " the query parameters, and the // after the scheme does not count"
                                          + " (kafka:orders* matches kafka://orders?brokers=b1)");
                    jo.put("pattern", true);
                    jo.put("pick", "endpoints");
                }
                case COMPONENT -> {
                    jo.put("description", "The scheme of the endpoint URIs, with * for any characters (no ':')");
                    jo.put("pattern", true);
                    jo.put("pick", "components");
                }
                case ROUTE -> {
                    jo.put("description", "A route id, with * for any characters; one route id (no pattern) for"
                                          + " stall and lifecycle");
                    jo.put("pattern", true);
                    jo.put("pick", "routes");
                }
                case STEP -> {
                    jo.put("description", "A step id (to1, or the id set with id(\"enrich\")), with * for any"
                                          + " characters");
                    jo.put("pattern", true);
                    jo.put("pick", "steps");
                }
                default -> throw new IllegalStateException("Unknown target " + target);
            }
            answer.add(jo);
        }
        return answer;
    }

    private static JsonArray hooks() {
        // the targets and the faults of each hook, from the hooks of each target the parser offers
        Map<Hook, List<String>> targets = new LinkedHashMap<>();
        Map<Hook, Set<Fault>> faults = new LinkedHashMap<>();
        for (Target target : Target.values()) {
            for (Map.Entry<Hook, Set<Fault>> entry : ChaosRuleParser.offered(target).entrySet()) {
                targets.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(target.key);
                faults.computeIfAbsent(entry.getKey(), k -> EnumSet.noneOf(Fault.class)).addAll(entry.getValue());
            }
        }
        JsonArray answer = new JsonArray();
        for (Hook hook : Hook.values()) {
            JsonObject jo = new JsonObject();
            jo.put("name", hook.key);
            jo.put("description", switch (hook) {
                case SEND -> "The sends to a matching endpoint (to, toD, wireTap, enrich, recipientList, a"
                             + " ProducerTemplate...), before the producer is called, inside the error handler";
                case REPLY -> "The reply of such a send, after the producer succeeded: its side effect happened (only"
                              + " with at=reply)";
                case DLQ -> "The send of the dead letter channel to a matching endpoint (only with at=dlq)";
                case POLL -> "The polls of the scheduled poll consumers of the routes";
                case ENTRY -> "Each exchange that enters a matching route, before the first step";
                case STEP -> "Each matching step of a route, right before its processor is called";
            });
            jo.put("targets", new JsonArray(targets.getOrDefault(hook, List.of())));
            jo.put("faults", keys(faults.getOrDefault(hook, Set.of())));
            jo.put("onlyWithAt", ChaosRuleParser.onlyWithAt(hook));
            answer.add(jo);
        }
        return answer;
    }

    // ---- the faults and their parameters ----

    private static JsonArray faults() {
        JsonArray answer = new JsonArray();
        for (Fault fault : Fault.values()) {
            if (fault == Fault.LIFECYCLE) {
                answer.add(fault("lifecycle", fault, false,
                        "Suspends, stops or removes the route of a route=<id> target, and brings it back after the"
                                                            + " duration; repeat= takes it down more than once"));
                answer.add(fault("stall", fault, true, "Short for lifecycle action=suspend: the route stalls for the"
                                                       + " duration"));
            } else {
                answer.add(fault(fault.key, fault, false, switch (fault) {
                    case ERROR -> "Fails the send, the poll or the step with an exception (a ChaosException without"
                                  + " exception=), or the failure of an HTTP component for status=";
                    case DELAY -> "Waits before the send, the poll or the step, as for a slow endpoint";
                    case DROP -> "A send that reports success without calling the producer, or a poll that is skipped";
                    case CORRUPT -> "Changes the message: its headers, the charset of its body, or its body";
                    default -> throw new IllegalStateException("Unknown fault " + fault);
                }));
            }
        }
        return answer;
    }

    private static JsonObject fault(String name, Fault fault, boolean stall, String description) {
        JsonObject jo = new JsonObject();
        jo.put("name", name);
        jo.put("description", description);
        // the targets that have a hook offering the fault; stall and lifecycle act on a route at no hook
        Set<String> targets = new TreeSet<>();
        Set<Hook> hooks = EnumSet.noneOf(Hook.class);
        for (Target target : Target.values()) {
            for (Map.Entry<Hook, Set<Fault>> entry : ChaosRuleParser.offered(target).entrySet()) {
                if (entry.getValue().contains(fault)) {
                    targets.add(target.key);
                    hooks.add(entry.getKey());
                }
            }
        }
        if (fault == Fault.LIFECYCLE) {
            targets.add(Target.ROUTE.key);
        }
        jo.put("targets", new JsonArray(List.copyOf(targets)));
        jo.put("hooks", keys(hooks));
        if (stall) {
            jo.put("shortFor", "lifecycle action=suspend");
        }
        JsonArray parameters = new JsonArray();
        for (String key : new TreeSet<>(ChaosRuleParser.faultKeys(fault, stall))) {
            parameters.add(parameter(fault, key));
        }
        jo.put("parameters", parameters);
        jo.put("options", new JsonArray(List.copyOf(new TreeSet<>(ChaosRuleParser.optionKeys(fault)))));
        return jo;
    }

    private static JsonObject parameter(Fault fault, String key) {
        String scope = fault.key + " " + key;
        return switch (scope) {
            case "error exception" -> param("exception", "class", false,
                    "The class of the exception: a public Throwable with a public constructor taking a String or none"
                                                                         + " (not an InterruptedException)",
                    "java.net.ConnectException");
            case "error message" -> param("message", "string", false,
                    "The message of the exception (the class needs a constructor taking a String)", "broker down");
            case "error after" -> withNote(param("after", "duration", false,
                    "Waits that long first, then fails, as a call that times out; the producer is not called", "5s"),
                    "notAt", new JsonArray(List.of(Hook.REPLY.key)));
            case "error status" -> {
                JsonObject p = param("status", "integer", false,
                        "The failure of an HTTP component for this status (not with exception= or message=)", "503");
                p.put("min", 300);
                p.put("max", 599);
                p.put("schemes", new JsonArray(List.copyOf(new TreeSet<>(ChaosRuleParser.HTTP_SCHEMES))));
                yield p;
            }
            case "error body" -> param("body", "string", false, "The body of the HTTP response (needs status=)",
                    "no stock");
            case "error header." -> {
                JsonObject p = param("header.<name>", "string", false,
                        "A header of the HTTP response, such as header.Retry-After=5 (needs status=)", "5");
                p.put("prefix", ChaosRuleParser.HEADER_PREFIX);
                yield p;
            }
            case "delay duration" -> {
                JsonObject p = param("duration", "duration", true,
                        "How long to wait, or a range such as 100ms..2s for a draw between the two times (from the"
                                                                   + " seeded sequence of the rule)",
                        "500ms");
                p.put("range", true);
                yield p;
            }
            case "corrupt mode" -> {
                JsonObject p = param("mode", "enum", true,
                        "headers: the headers that are not Camel* get random values; charset: a text body becomes"
                                                           + " UTF-16 bytes; truncate: a String, byte[] or stream body ends"
                                                           + " after half of it; empty: the body becomes empty of its kind"
                                                           + " (or null); remove-header: removes headers",
                        CorruptMode.HEADERS.key);
                p.put("enum", new JsonArray(corruptModes()));
                yield p;
            }
            case "corrupt header" -> param("header", "string", false,
                    "The headers mode=remove-header removes: a name or a pattern with *, ignoring case (without it"
                                                                      + " every header that is not Camel*)",
                    "X-Trace-*");
            case "lifecycle action" -> {
                JsonObject p = param("action", "enum", true, "What to do to the route", Action.STOP.key);
                JsonArray actions = new JsonArray();
                for (Action action : Action.values()) {
                    actions.add(action.key);
                }
                p.put("enum", actions);
                yield p;
            }
            case "lifecycle duration" -> param("duration", "duration", true,
                    "How long the route stays down (at most camel.chaos.maxDuration)", "20s");
            case "lifecycle repeat" -> {
                JsonObject p = param("repeat", "integer", false,
                        "How many times to take the route down (needs every= when more than 1)", "3");
                p.put("min", 1);
                p.put("max", ChaosRuleParser.MAX_REPEAT);
                yield p;
            }
            case "lifecycle every" -> param("every", "duration", false,
                    "The time from one time the route goes down to the next, longer than duration=", "1m");
            default -> throw new IllegalStateException("The chaos model has no description of the parameter " + scope);
        };
    }

    // ---- the options, the expectation and the limits ----

    private static JsonArray options() {
        JsonArray answer = new JsonArray();
        for (String key : ChaosRuleParser.GENERAL_KEYS.stream().sorted().toList()) {
            answer.add(switch (key) {
                case "at" -> {
                    JsonObject p = param("at", "enum", false,
                            "Narrows the rule to one hook of its target (reply and dlq are reached only with at=)",
                            Hook.SEND.key);
                    JsonArray hooks = new JsonArray();
                    for (Hook hook : Hook.values()) {
                        hooks.add(hook.key);
                    }
                    p.put("enum", hooks);
                    yield p;
                }
                case "rate" -> {
                    JsonObject p = param("rate", "rate", false,
                            "How often the rule fires: a probability above 0 and at most 1, every Nth match, or bursts"
                                                                + " (every match during the first part of each period);"
                                                                + " without it every match fires",
                            "0.25");
                    p.put("forms", new JsonArray(List.of("0.25", "every:4", "burst:5s/30s")));
                    yield p;
                }
                case "until" -> param("until", "countOrDuration", false,
                        "When the rule ends: a number of matches or a time from its start (at most"
                                                                         + " camel.chaos.maxDuration, its default)",
                        "10m");
                case "from" -> param("from", "duration", false,
                        "When the rule starts after it is added, such as after the warm-up (+30s or 30s)", "+30s");
                case "expect" -> param("expect", "expectation", false,
                        "An expectation of a route after the rule is added: <route>:<count> (at least that many"
                                                                       + " completed) or <route>:<kind><op><count>",
                        "orders:handled<=0");
                case "within" -> param("within", "duration", false,
                        "The time of the expectation (by default until the rule ends)", "2m");
                default -> throw new IllegalStateException("The chaos model has no description of the option " + key);
            });
        }
        return answer;
    }

    private static JsonObject expectation() {
        JsonObject jo = new JsonObject();
        JsonArray kinds = new JsonArray();
        for (Expectation.Kind kind : Expectation.Kind.values()) {
            JsonObject k = new JsonObject();
            k.put("name", kind.key);
            k.put("description", "The " + kind.words(2) + " in the route");
            kinds.add(k);
        }
        jo.put("kinds", kinds);
        JsonArray ops = new JsonArray();
        for (Expectation.Op op : Expectation.Op.values()) {
            JsonObject o = new JsonObject();
            o.put("name", op.key);
            o.put("description", op.words);
            ops.add(o);
        }
        jo.put("ops", ops);
        return jo;
    }

    private static JsonObject limits() {
        JsonObject jo = new JsonObject();
        jo.put("maxLength", ChaosRuleParser.MAX_LENGTH);
        jo.put("maxRepeat", ChaosRuleParser.MAX_REPEAT);
        jo.put("maxDuration", "camel.chaos.maxDuration (default 10m): the longest until, delay, after, from, burst period"
                              + " and stall or lifecycle duration");
        jo.put("time", "a number with a unit (ms, s, m, h, d), also combined as 1m30s; a number alone is milliseconds");
        jo.put("quoting", "a value with spaces or ; is quoted with \" or ' (\\\", \\' and \\\\ inside quotes)");
        return jo;
    }

    // ---- the recipes ----

    /**
     * A placeholder of a recipe: {@code ${name}} in its rule. The example is what the model test fills in to parse the
     * recipe; the default is what a tool offers first.
     */
    record Placeholder(String name, String type, String description, String example, String defaultValue,
            JsonObject pick) {
    }

    /** A known rule in plain words, as a template. */
    record Recipe(String id, String group, String title, String description, String rule, List<Placeholder> placeholders) {
    }

    static List<Recipe> recipeList() {
        List<Recipe> answer = new ArrayList<>();
        // downstream
        answer.add(new Recipe(
                "http-error", "Downstream", "Returns an error (HTTP status)",
                "An HTTP call fails with a status, as a service that is down (503), overloaded "
                                                                              + "(429) or broken (500) answers. The exchange fails with the exception of the HTTP "
                                                                              + "component, as the real call would",
                "endpoint=${endpoint} error status=${status} rate=${rate} until=${until}",
                List.of(httpEndpoint(), new Placeholder("status", "integer", "The HTTP status", "503", "503", null),
                        rate("0.25"), until("10m"))));
        answer.add(new Recipe(
                "connection-refused", "Downstream", "Refuses the connection",
                "The send fails with a ConnectException before anything is sent, as when the "
                                                                              + "service is not running or a firewall blocks it",
                "endpoint=${endpoint} error exception=java.net.ConnectException rate=${rate} until=${until}",
                List.of(producer(), rate("0.25"), until("10m"))));
        answer.add(new Recipe(
                "slow", "Downstream", "Is slow",
                "Each send waits a time within the range before it is made, as a slow service. "
                                                 + "The exchange then goes on as usual: the cost is time, not errors",
                "endpoint=${endpoint} delay duration=${duration} rate=${rate} until=${until}",
                List.of(producer(), new Placeholder(
                        "duration", "duration", "The time, or a range of times", "200ms..3s",
                        "200ms..3s", null), rate("0.5"), until("10m"))));
        answer.add(new Recipe(
                "timeout", "Downstream", "Times out",
                "The send waits, then fails with a SocketTimeoutException without calling the "
                                                      + "producer, as a call that times out",
                "endpoint=${endpoint} error after=${after} exception=java.net.SocketTimeoutException rate=${rate}"
                                                                                              + " until=${until}",
                List.of(producer(), new Placeholder("after", "duration", "How long before it times out", "5s", "5s", null),
                        rate("0.1"), until("10m"))));
        answer.add(new Recipe(
                "failed-after-success", "Downstream", "Did the work, then failed",
                "The send is made and succeeds, then the exchange fails, as when the reply is "
                                                                                   + "lost. The error handler sends it again, so the other side gets it twice",
                "endpoint=${endpoint} error at=reply until=${until}",
                List.of(producer(), new Placeholder("until", "countOrDuration", "How many sends fail", "1", "1", null))));
        answer.add(new Recipe(
                "outage", "Downstream", "Is down for a while",
                "Every send fails during the first part of each period and works for the rest, as "
                                                               + "a service that goes down and comes back",
                "endpoint=${endpoint} error rate=burst:${outage}/${period} until=${until}",
                List.of(producer(), new Placeholder("outage", "duration", "How long each outage lasts", "10s", "10s", null),
                        new Placeholder("period", "duration", "The time from one outage to the next", "1m", "1m", null),
                        until("10m"))));
        // messages
        answer.add(new Recipe(
                "corrupt-entry", "Messages", "Arrives corrupt",
                "The messages that enter the route are changed before its first step: random "
                                                                + "headers, the wrong charset, cut in half or empty",
                "route=${route} corrupt mode=${mode} rate=${rate} until=${until}",
                List.of(route(), new Placeholder(
                        "mode", "enum", "How the message is changed", "headers", "headers",
                        pick("values", "values", corruptModes())),
                        rate("0.25"), until("10m"))));
        answer.add(new Recipe(
                "empty-body", "Messages", "Is sent with an empty body",
                "The message sent to the endpoint has an empty body, as a sender with nothing to "
                                                                        + "send",
                "endpoint=${endpoint} corrupt mode=empty rate=${rate} until=${until}",
                List.of(producer(), rate("0.25"), until("10m"))));
        answer.add(new Recipe(
                "missing-header", "Messages", "Is sent without a header",
                "A header is removed from the message sent to the endpoint, such as a correlation "
                                                                          + "id, a trace id or a content type",
                "endpoint=${endpoint} corrupt mode=remove-header header=${header} rate=${rate} until=${until}",
                List.of(producer(), new Placeholder(
                        "header", "string", "The header, or a pattern with *", "X-Trace-Id",
                        null, pick("headers", null, null)), rate("0.25"), until("10m"))));
        // consumers and routes
        answer.add(new Recipe(
                "poll-error", "Consumers and routes", "Polls fail",
                "The polls of a scheduled poll consumer fail, as when the folder, the server or "
                                                                    + "the database is not reachable. No exchange is made: the consumer polls again on "
                                                                    + "its schedule",
                "endpoint=${endpoint} error at=poll rate=${rate} until=${until}",
                List.of(polled(), rate("0.5"), until("10m"))));
        answer.add(new Recipe(
                "poll-skip", "Consumers and routes", "Polls are skipped",
                "The polls of a scheduled poll consumer are skipped, as a consumer that falls "
                                                                          + "behind: the messages wait at the source",
                "endpoint=${endpoint} drop at=poll rate=${rate} until=${until}",
                List.of(polled(), rate("0.5"), until("10m"))));
        answer.add(new Recipe(
                "stall", "Consumers and routes", "The route stalls",
                "The route is suspended for the time, then resumed: its consumer takes no "
                                                                     + "messages, and they wait in a queue or at the source",
                "route=${route} stall duration=${duration}",
                List.of(route(), new Placeholder("duration", "duration", "How long it stalls", "20s", "20s", null))));
        answer.add(new Recipe(
                "flap", "Consumers and routes", "The route flaps",
                "The route stops and starts again, several times, as a consumer that keeps losing "
                                                                   + "its connection",
                "route=${route} lifecycle action=stop duration=${duration} repeat=${repeat} every=${every}",
                List.of(route(), new Placeholder("duration", "duration", "How long it is down each time", "10s", "10s", null),
                        new Placeholder("repeat", "integer", "How many times", "3", "3", null),
                        new Placeholder("every", "duration", "The time from one time to the next", "1m", "1m", null))));
        answer.add(new Recipe(
                "removed", "Consumers and routes", "The route is removed",
                "The route is removed from the integration and added back after the time, as when "
                                                                           + "one route is reloaded or redeployed",
                "route=${route} lifecycle action=remove duration=${duration}",
                List.of(route(), new Placeholder("duration", "duration", "How long it is gone", "30s", "30s", null))));
        // error handling
        answer.add(new Recipe(
                "dlq-unreachable", "Error handling", "The dead letter channel is unreachable",
                "The send of the dead letter channel fails, so the messages it should keep are "
                                                                                               + "lost. Add it before the rule that makes the messages fail",
                "endpoint=${endpoint} error at=dlq",
                List.of(producer())));
        answer.add(new Recipe(
                "step-error", "Error handling", "A step fails",
                "A step of a route fails, as when its processor throws because of a bug or bad "
                                                                + "data",
                "step=${step} error rate=${rate} until=${until}",
                List.of(step(), rate("0.1"), until("10m"))));
        answer.add(new Recipe(
                "step-slow", "Error handling", "A step is slow",
                "A step of a route waits before its processor is called, as a slow lookup or "
                                                                 + "calculation",
                "step=${step} delay duration=${duration} rate=${rate} until=${until}",
                List.of(step(), new Placeholder("duration", "duration", "The time, or a range of times", "1s", "1s", null),
                        rate("0.25"), until("10m"))));
        return answer;
    }

    /**
     * What a recipe helps to find, when not to use it, and what to look at while it runs: to guide a person, or an AI,
     * that picks a recipe (CAMEL-26699).
     */
    record Guide(List<String> uncovers, List<String> avoid, List<String> watch) {
    }

    static Map<String, Guide> guides() {
        Map<String, Guide> answer = new LinkedHashMap<>();
        answer.put("http-error", new Guide(
                List.of(
                        "An error handler that does not retry, or retries a status that will not change, "
                        + "such as 400 or 404",
                        "Redeliveries without a delay, which hit an overloaded service again at once",
                        "Messages that are lost instead of reaching the dead letter channel"),
                List.of(
                        "The endpoint is not HTTP: use Refuses the connection",
                        "To test a timeout: a status comes back at once, use Times out"),
                List.of(
                        "The redeliveries of the step and the exchanges handled by the error handler",
                        "The dead letter channel, and a circuit breaker around the call if there is one")));
        answer.put("connection-refused", new Guide(
                List.of(
                        "Whether a connection error is retried: that is safe, as nothing reached the "
                        + "other side",
                        "Long redelivery delays that hold the consumer while the service is down",
                        "A circuit breaker that does not open"),
                List.of(
                        "To test an HTTP status from a service that is up: use Returns an error"),
                List.of(
                        "The redeliveries and the exchanges handled by the error handler",
                        "The state of a circuit breaker around the call")));
        answer.put("slow", new Guide(
                List.of(
                        "A consumer that falls behind: the throughput of the route drops and messages "
                        + "wait in front of it",
                        "Thread pools that fill up with waiting exchanges",
                        "Callers of the route (request/reply) that time out"),
                List.of(
                        "To test the timeout of the client: the wait is before the call, so the client's "
                        + "timeout does not fire. Use Times out",
                        "On a route started by a timer: the timer waits for the exchange, so there are "
                                                                  + "fewer messages, not more load"),
                List.of(
                        "The mean, p95 and p99 time of the routes, and their throughput",
                        "The exchanges in flight")));
        answer.put("timeout", new Guide(
                List.of(
                        "Whether a timeout is retried, and whether that is safe: in real life the other "
                        + "side may have done the work",
                        "How long a message takes when every attempt times out: the wait times the "
                                                         + "attempts",
                        "A consumer that stops taking messages while its threads wait"),
                List.of(
                        "A long wait with a high rate: each fault holds a thread for that time"),
                List.of(
                        "The time of the route and the exchanges in flight",
                        "The redeliveries and the dead letter channel")));
        answer.put("failed-after-success", new Guide(
                List.of(
                        "Duplicates: an order booked twice, a payment taken twice",
                        "Whether the other side or the route is idempotent, such as with an idempotent "
                                                                                    + "consumer or a key the other side checks"),
                List.of(
                        "Against a real service where a duplicate costs, such as one that charges or "
                        + "sends mail to people",
                        "With a high count: until=1 makes one send fail, which is enough to see one "
                                                  + "duplicate"),
                List.of(
                        "What the other side received: two of the same",
                        "The redeliveries of the step")));
        answer.put("outage", new Guide(
                List.of(
                        "A circuit breaker that opens during the outage and closes after it",
                        "Redelivery that gives up before the service is back, so the messages go to the "
                                                                                              + "dead letter channel",
                        "How long the route needs to catch up after an outage"),
                List.of(
                        "An outage longer than all the redeliveries together sends every message in it to "
                        + "the dead letter channel: make sure that is what you want to test"),
                List.of(
                        "The faults per second: on during the outage, off between",
                        "The state of a circuit breaker, and the exchanges handled by the error handler")));
        answer.put("corrupt-entry", new Guide(
                List.of(
                        "Validation that is missing, so a bad message gets deep into the route before it "
                        + "fails",
                        "A poison message that is redelivered again and again, instead of going to the "
                                   + "dead letter channel",
                        "Unmarshal and parse errors that are not handled"),
                List.of(
                        "When the body is a stream or a file: the charset and the cut change text and "
                        + "bytes only, so those messages are skipped (see the reasons it skipped)"),
                List.of(
                        "The errors of the steps that read the body",
                        "The messages the rule skipped, and why")));
        answer.put("empty-body", new Guide(
                List.of(
                        "A service, or the next route, that does not check for an empty body",
                        "An empty file or message that is accepted and passed on"),
                List.of(
                        "To test the route itself with bad input: use Arrives corrupt on the route"),
                List.of(
                        "The route or the service that receives it")));
        answer.put("missing-header", new Guide(
                List.of(
                        "A receiver that needs the header and fails without it, or worse, goes on and "
                        + "does the wrong thing",
                        "Tracing that breaks, as the trace id does not reach the next service"),
                List.of(
                        "With a header the endpoint does not send on, such as most Camel headers: nothing "
                        + "changes"),
                List.of(
                        "The route or the service that receives it")));
        answer.put("poll-error", new Guide(
                List.of(
                        "A consumer without a backoff, which fails every poll and fills the log",
                        "A health check that does not show a consumer that cannot poll"),
                List.of(
                        "On a consumer that does not poll on a schedule, such as a timer, Kafka or JMS: "
                        + "the rule fires 0 times"),
                List.of(
                        "The errors and the backoff of the consumer",
                        "The log and the health checks")));
        answer.put("poll-skip", new Guide(
                List.of(
                        "Messages that pile up at the source",
                        "Alerts on a consumer that goes quiet",
                        "Batching or ordering that counts on every poll"),
                List.of(
                        "On a consumer that does not poll on a schedule, such as a timer, Kafka or JMS: "
                        + "the rule fires 0 times"),
                List.of(
                        "The polls of the consumer, and the routes after it that get nothing")));
        answer.put("stall", new Guide(
                List.of(
                        "Senders that block or fail when the route does not take messages, such as direct "
                        + "callers or a full seda queue",
                        "Health checks and alerts for a route that stopped",
                        "How long the route needs to catch up"),
                List.of(
                        "On a route others call directly and wait for: they fail while it stalls, which "
                        + "may or may not be the test"),
                List.of(
                        "The state of the route",
                        "The callers, and the queue in front of the route")));
        answer.put("flap", new Guide(
                List.of(
                        "Messages lost or delivered twice across a restart",
                        "Connections or other resources that leak on each start",
                        "Health checks and alerts that flap with it"),
                List.of(
                        "On a route whose consumer takes long to start: make the time between longer than "
                        + "the start"),
                List.of(
                        "The state and the uptime of the route",
                        "The effect: completed against failed or lost messages")));
        answer.put("removed", new Guide(
                List.of(
                        "Routes that call it and fail while it is gone",
                        "State kept in memory that is lost with it, such as an aggregator or an "
                                                                         + "idempotent repository"),
                List.of(
                        "On a route whose state in memory you want to keep"),
                List.of(
                        "The callers of the route",
                        "Its counters, which start again from zero")));
        answer.put("dlq-unreachable", new Guide(
                List.of(
                        "That the dead letter channel is a single point of failure",
                        "Whether lost messages are noticed: the report counts them as lost, and the "
                                                                                     + "verdict fails"),
                List.of(
                        "On its own: without another rule, or a real error, that sends messages to the "
                        + "dead letter channel, nothing happens"),
                List.of(
                        "The messages lost, and the verdict")));
        answer.put("step-error", new Guide(
                List.of(
                        "onException clauses that do not match the exception",
                        "Redelivery of a step that will fail again: a bug is not a temporary error",
                        "Work done by the steps before it that is not undone"),
                List.of(
                        "To test a call to another system: put the fault on its endpoint, with Returns an "
                        + "error or Refuses the connection"),
                List.of(
                        "The redeliveries of the step and the exchanges handled by the error handler",
                        "The errors, with the step that failed")));
        answer.put("step-slow", new Guide(
                List.of(
                        "The step that the time of the route comes from",
                        "Callers of the route that wait for it, and time out"),
                List.of(
                        "In a route started by a timer: the timer waits for the exchange, so there are "
                        + "fewer messages, not more load"),
                List.of(
                        "The times of the steps and of the route")));
        return answer;
    }

    private static JsonArray recipes() {
        JsonArray answer = new JsonArray();
        Map<String, Guide> guides = guides();
        for (Recipe recipe : recipeList()) {
            JsonObject jo = new JsonObject();
            jo.put("id", recipe.id());
            jo.put("group", recipe.group());
            jo.put("title", recipe.title());
            jo.put("description", recipe.description());
            Guide guide = guides.get(recipe.id());
            if (guide != null) {
                jo.put("uncovers", new JsonArray(guide.uncovers()));
                jo.put("avoid", new JsonArray(guide.avoid()));
                jo.put("watch", new JsonArray(guide.watch()));
            }
            jo.put("rule", recipe.rule());
            JsonArray placeholders = new JsonArray();
            for (Placeholder p : recipe.placeholders()) {
                JsonObject po = new JsonObject();
                po.put("name", p.name());
                po.put("type", p.type());
                po.put("description", p.description());
                if (p.defaultValue() != null) {
                    po.put("default", p.defaultValue());
                }
                po.put("example", p.example());
                if (p.pick() != null) {
                    po.put("pick", p.pick());
                }
                placeholders.add(po);
            }
            jo.put("placeholders", placeholders);
            answer.add(jo);
        }
        return answer;
    }

    private static Placeholder producer() {
        return new Placeholder(
                "endpoint", "endpoint", "The endpoint the route sends to", "kafka:orders", null,
                pick("producers", null, null));
    }

    private static Placeholder httpEndpoint() {
        return new Placeholder(
                "endpoint", "endpoint", "The HTTP endpoint the route calls", "https://inventory/stock",
                null, pick("producers", "schemes", List.copyOf(new TreeSet<>(ChaosRuleParser.HTTP_SCHEMES))));
    }

    private static Placeholder polled() {
        return new Placeholder(
                "endpoint", "endpoint", "The endpoint a route consumes with a scheduled poll",
                "file:inbox", null, pick("pollingConsumers", null, null));
    }

    private static Placeholder route() {
        return new Placeholder("route", "route", "The route", "orders", null, pick("routes", null, null));
    }

    private static Placeholder step() {
        return new Placeholder("step", "step", "The step", "enrich", null, pick("steps", null, null));
    }

    private static Placeholder rate(String value) {
        return new Placeholder(
                "rate", "rate", "How often: a probability, every:N or burst:<time>/<period>", value, value,
                null);
    }

    private static Placeholder until(String value) {
        return new Placeholder(
                "until", "countOrDuration", "When the rule ends: a number of matches or a time", value,
                value, null);
    }

    /**
     * Where a tool picks the value of a placeholder from: the endpoints the routes send to (producers), the endpoints
     * the routes consume with a scheduled poll (pollingConsumers), the routes, the steps, the headers, or the values
     * listed.
     */
    private static JsonObject pick(String from, String key, List<String> values) {
        JsonObject jo = new JsonObject();
        jo.put("from", from);
        if (values != null) {
            jo.put(key, new JsonArray(values));
        }
        return jo;
    }

    private static List<String> corruptModes() {
        List<String> answer = new ArrayList<>();
        for (CorruptMode mode : CorruptMode.values()) {
            answer.add(mode.key);
        }
        return answer;
    }

    // ---- helpers ----

    private static JsonObject param(String name, String type, boolean required, String description, String example) {
        JsonObject jo = new JsonObject();
        jo.put("name", name);
        jo.put("type", type);
        jo.put("required", required);
        jo.put("description", description);
        jo.put("example", example);
        return jo;
    }

    private static JsonObject withNote(JsonObject jo, String key, Object value) {
        jo.put(key, value);
        return jo;
    }

    private static <E extends Enum<E>> JsonArray keys(Set<E> values) {
        JsonArray answer = new JsonArray();
        for (E value : values) {
            answer.add(switch (value) {
                case Hook h -> h.key;
                case Fault f -> f.key;
                default -> value.name();
            });
        }
        return answer;
    }
}
