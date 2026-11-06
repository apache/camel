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
                "An HTTP call fails with a status, as for a service that is down or overloaded: tests onException,"
                                                                              + " redelivery and the dead letter channel",
                "endpoint=${endpoint} error status=${status} rate=${rate} until=${until}",
                List.of(httpEndpoint(), new Placeholder("status", "integer", "The HTTP status", "503", "503", null),
                        rate("0.25"), until("10m"))));
        answer.add(new Recipe(
                "connection-refused", "Downstream", "Refuses the connection",
                "The send fails as when the other side is not there",
                "endpoint=${endpoint} error exception=java.net.ConnectException rate=${rate} until=${until}",
                List.of(producer(), rate("0.25"), until("10m"))));
        answer.add(new Recipe(
                "slow", "Downstream", "Is slow",
                "Each send waits a different time within a range, as a slow service does",
                "endpoint=${endpoint} delay duration=${duration} rate=${rate} until=${until}",
                List.of(producer(), new Placeholder(
                        "duration", "duration", "The time, or a range of times", "200ms..3s",
                        "200ms..3s", null), rate("0.5"), until("10m"))));
        answer.add(new Recipe(
                "timeout", "Downstream", "Times out",
                "The send waits, then fails without calling the producer, as a call that times out",
                "endpoint=${endpoint} error after=${after} exception=java.net.SocketTimeoutException rate=${rate}"
                                                                                                     + " until=${until}",
                List.of(producer(), new Placeholder("after", "duration", "How long before it times out", "5s", "5s", null),
                        rate("0.1"), until("10m"))));
        answer.add(new Recipe(
                "failed-after-success", "Downstream", "Did the work, then failed",
                "The send succeeds and then fails, as a reply that is lost: a redelivery sends it again, so this tests"
                                                                                   + " that a duplicate does no harm",
                "endpoint=${endpoint} error at=reply until=${until}",
                List.of(producer(), new Placeholder("until", "countOrDuration", "How many sends fail", "1", "1", null))));
        answer.add(new Recipe(
                "outage", "Downstream", "Is down for a while",
                "Every send fails during the first part of each period, as outage windows",
                "endpoint=${endpoint} error rate=burst:${outage}/${period} until=${until}",
                List.of(producer(), new Placeholder("outage", "duration", "How long each outage lasts", "10s", "10s", null),
                        new Placeholder("period", "duration", "The time from one outage to the next", "1m", "1m", null),
                        until("10m"))));
        // messages
        answer.add(new Recipe(
                "corrupt-entry", "Messages", "Arrives corrupt",
                "The messages that enter the route are changed: random headers, the wrong charset, cut in half or"
                                                                + " empty",
                "route=${route} corrupt mode=${mode} rate=${rate} until=${until}",
                List.of(route(), new Placeholder(
                        "mode", "enum", "How the message is changed", "headers", "headers",
                        pick("values", "values", corruptModes())),
                        rate("0.25"), until("10m"))));
        answer.add(new Recipe(
                "empty-body", "Messages", "Is sent with an empty body",
                "The message sent to the endpoint has an empty body",
                "endpoint=${endpoint} corrupt mode=empty rate=${rate} until=${until}",
                List.of(producer(), rate("0.25"), until("10m"))));
        answer.add(new Recipe(
                "missing-header", "Messages", "Is sent without a header",
                "A header is missing from the message sent to the endpoint",
                "endpoint=${endpoint} corrupt mode=remove-header header=${header} rate=${rate} until=${until}",
                List.of(producer(), new Placeholder(
                        "header", "string", "The header, or a pattern with *", "X-Trace-Id",
                        null, pick("headers", null, null)), rate("0.25"), until("10m"))));
        // consumers and routes
        answer.add(new Recipe(
                "poll-error", "Consumers and routes", "Polls fail",
                "The polls of a scheduled poll consumer fail, as when the source is not reachable",
                "endpoint=${endpoint} error at=poll rate=${rate} until=${until}",
                List.of(polled(), rate("0.5"), until("10m"))));
        answer.add(new Recipe(
                "poll-skip", "Consumers and routes", "Polls are skipped",
                "The polls of a scheduled poll consumer are skipped, as a consumer that falls behind",
                "endpoint=${endpoint} drop at=poll rate=${rate} until=${until}",
                List.of(polled(), rate("0.5"), until("10m"))));
        answer.add(new Recipe(
                "stall", "Consumers and routes", "The route stalls",
                "The route is suspended for a while, and its messages wait",
                "route=${route} stall duration=${duration}",
                List.of(route(), new Placeholder("duration", "duration", "How long it stalls", "20s", "20s", null))));
        answer.add(new Recipe(
                "flap", "Consumers and routes", "The route flaps",
                "The route stops and starts again several times",
                "route=${route} lifecycle action=stop duration=${duration} repeat=${repeat} every=${every}",
                List.of(route(), new Placeholder("duration", "duration", "How long it is down each time", "10s", "10s", null),
                        new Placeholder("repeat", "integer", "How many times", "3", "3", null),
                        new Placeholder("every", "duration", "The time from one time to the next", "1m", "1m", null))));
        answer.add(new Recipe(
                "removed", "Consumers and routes", "The route is removed",
                "The route is removed, and added back after a while",
                "route=${route} lifecycle action=remove duration=${duration}",
                List.of(route(), new Placeholder("duration", "duration", "How long it is gone", "30s", "30s", null))));
        // error handling
        answer.add(new Recipe(
                "dlq-unreachable", "Error handling", "The dead letter channel is unreachable",
                "The send of the dead letter channel fails, so the messages it handles are lost (add it before the rule"
                                                                                               + " that makes them fail)",
                "endpoint=${endpoint} error at=dlq",
                List.of(producer())));
        answer.add(new Recipe(
                "step-error", "Error handling", "A step fails",
                "A step of a route fails, as when its processor throws",
                "step=${step} error rate=${rate} until=${until}",
                List.of(step(), rate("0.1"), until("10m"))));
        answer.add(new Recipe(
                "step-slow", "Error handling", "A step is slow",
                "A step of a route waits before its processor is called",
                "step=${step} delay duration=${duration} rate=${rate} until=${until}",
                List.of(step(), new Placeholder("duration", "duration", "The time, or a range of times", "1s", "1s", null),
                        rate("0.25"), until("10m"))));
        return answer;
    }

    private static JsonArray recipes() {
        JsonArray answer = new JsonArray();
        for (Recipe recipe : recipeList()) {
            JsonObject jo = new JsonObject();
            jo.put("id", recipe.id());
            jo.put("group", recipe.group());
            jo.put("title", recipe.title());
            jo.put("description", recipe.description());
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
