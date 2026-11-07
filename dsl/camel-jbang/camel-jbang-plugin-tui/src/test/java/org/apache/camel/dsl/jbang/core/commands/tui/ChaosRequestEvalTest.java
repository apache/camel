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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.chaos.ChaosManager;
import org.apache.camel.dsl.jbang.core.commands.LlmClient;
import org.apache.camel.dsl.jbang.core.commands.ai.AppFeatures;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The eval set of the "describe the chaos you want" input of the Chaos screen (CAMEL-26721, S2-5 of CAMEL-26607): loose
 * requests ({@code chaos-eval/requests.json}), each sent to a local model as the AI panel sends it (its system prompt,
 * the core tools and the chaos tools, and the question of {@link ChaosAiPrompt#ofRequest}). The check tool is answered
 * by camel-chaos itself, over routes shaped like the chaos demo; the rules the model adds or changes are recorded, as
 * if the user confirmed them, and scored against what the request means. Skipped unless {@code CAMEL_TUI_CHAOS_EVAL}
 * names the model, for example:
 *
 * <pre>
 * CAMEL_TUI_CHAOS_EVAL=qwen3.6:35b-mlx mvn test -Dtest=ChaosRequestEvalTest
 * </pre>
 *
 * {@code CAMEL_TUI_CHAOS_EVAL_RUNS} runs each request more than once (default 1), {@code CAMEL_TUI_CHAOS_EVAL_ONLY}
 * runs only the requests whose id contains it, {@code CAMEL_TUI_CHAOS_EVAL_VERBOSE=true} prints each tool call, and
 * {@code OLLAMA_HOST} overrides the server (default {@code http://localhost:11434}). It prints a line per request and a
 * summary, and does not fail on a wrong answer: it measures a model.
 */
class ChaosRequestEvalTest {

    // the most turns of a conversation before it counts as stuck
    private static final int MAX_TURNS = 8;
    private static final String YES = "yes, add them";
    // the known rules of the catalog, as the Chaos screen loads them
    private static final ChaosRecipes RECIPES = ChaosRecipes.load();
    private static final boolean VERBOSE = Boolean.parseBoolean(System.getenv("CAMEL_TUI_CHAOS_EVAL_VERBOSE"));

    /** A rule the model added (change null), or the new rule of a rule it changed. */
    record Proposed(String change, String rule) {
    }

    /** How one request went. */
    record Outcome(String id, String failure, List<Proposed> proposed, int toolCalls, int checks, double seconds,
            long tokens) {

        boolean passed() {
            return failure == null;
        }
    }

    // ---- the scoring, which always runs ----

    @Test
    void theScoringReadsTheRulesAsTheParserDoes() throws Exception {
        JsonObject spain = (JsonObject) Jsoner.deserialize("""
                {"target": "route=orders", "fault": ["error"], "rate": null, "when": ["country", "ES"]}""");
        assertThat(mismatch(spain, new Proposed(null, "route=orders error when=\"${header.country} == 'ES'\""))).isNull();
        assertThat(mismatch(spain, new Proposed(null, "route=orders error when='${header.country} == \"ES\"'"))).isNull();
        assertThat(mismatch(spain, new Proposed(null, "route=orders error rate=0.05 when=\"${header.country} == 'ES'\"")))
                .isEqualTo("rate=0.05, expected none");
        assertThat(mismatch(spain, new Proposed(null, "route=orders error"))).isEqualTo("no when= with country, ES");
        // rate=1 fires at every match, as no rate does
        assertThat(mismatch(spain, new Proposed(null, "route=orders error rate=1 when=\"${header.country} == 'ES'\"")))
                .isNull();
        assertThat(mismatch(spain, new Proposed(null, "route=shipping error when=\"${header.country} == 'ES'\"")))
                .isEqualTo("target route=shipping, expected route=orders");

        JsonObject slow = (JsonObject) Jsoner.deserialize("""
                {"target": "endpoint=http.*pay.*", "fault": ["delay"], "duration": "(1s|1000ms)\\\\.\\\\.(3s|3000ms)"}""");
        assertThat(mismatch(slow, new Proposed(null, "endpoint=http://localhost:8765/pay delay duration=1s..3s"))).isNull();
        assertThat(mismatch(slow, new Proposed(null, "endpoint=http:*pay* error duration=1s..3s")))
                .isEqualTo("fault error, expected [delay]");

        JsonObject change = (JsonObject) Jsoner.deserialize("""
                {"change": "r1", "target": "endpoint=http.*pay.*", "fault": ["error"], "rate": "0\\\\.10?"}""");
        assertThat(mismatch(change, new Proposed("r1", "endpoint=http:*pay* error rate=0.1"))).isNull();
        assertThat(mismatch(change, new Proposed(null, "endpoint=http:*pay* error rate=0.1")))
                .isEqualTo("added, expected a change of r1");

        // every expected rule matches one rule, and no rule is left over
        List<JsonObject> two = List.of(spain, (JsonObject) Jsoner.deserialize("""
                {"target": "route=orders", "fault": ["error"], "rate": "0\\\\.05"}"""));
        List<Proposed> both = List.of(new Proposed(null, "route=orders error rate=0.05"),
                new Proposed(null, "route=orders error when=\"${header.country} == 'ES'\""));
        assertThat(score(two, both)).isNull();
        assertThat(score(two, both.subList(0, 1))).startsWith("2 rules expected, 1 added or changed");
        assertThat(score(two, List.of())).isEqualTo("no rule added or changed");
    }

    /** Why the rule is not the expected one, or null when it is. */
    static String mismatch(JsonObject expect, Proposed proposed) {
        String change = expect.getString("change");
        if (change != null && !change.equals(proposed.change())) {
            return proposed.change() == null ? "added, expected a change of " + change : "changed " + proposed.change();
        }
        if (change == null && proposed.change() != null) {
            return "changed " + proposed.change() + ", expected an added rule";
        }
        Map<String, String> rule = parse(proposed.rule());
        String target = rule.get("@target");
        if (target == null || !Pattern.matches(expect.getString("target"), target)) {
            return "target " + target + ", expected " + expect.getString("target");
        }
        List<Object> faults = expect.getCollection("fault");
        if (!faults.contains(rule.get("@fault"))) {
            return "fault " + rule.get("@fault") + ", expected " + faults;
        }
        for (Map.Entry<String, Object> e : expect.entrySet()) {
            String key = e.getKey();
            if (List.of("change", "target", "fault", "when").contains(key)) {
                continue;
            }
            String value = rule.get(key);
            if ("rate".equals(key) && value != null && List.of("1", "1.0", "every:1").contains(value)) {
                // fires at every match, as without a rate
                value = null;
            }
            if (e.getValue() == null) {
                if (value != null) {
                    return key + "=" + value + ", expected none";
                }
            } else if (value == null || !Pattern.matches((String) e.getValue(), value)) {
                return key + "=" + value + ", expected " + e.getValue();
            }
        }
        List<Object> when = expect.getCollection("when");
        if (when != null) {
            String condition = rule.get("when");
            if (condition == null || !when.stream().allMatch(w -> condition.contains((String) w))) {
                return (condition == null ? "no when=" : "when=" + condition + " without") + " with "
                       + String.join(", ", when.stream().map(String::valueOf).toList());
            }
        }
        return null;
    }

    /**
     * Whether the rules are the expected ones: each expected rule matches its own rule, and none is left over. Null
     * when they are, else why not.
     */
    static String score(List<JsonObject> expected, List<Proposed> proposed) {
        if (proposed.isEmpty()) {
            return "no rule added or changed";
        }
        if (expected.size() != proposed.size()) {
            return expected.size() + " rules expected, " + proposed.size() + " added or changed: "
                   + proposed.stream().map(Proposed::rule).toList();
        }
        List<Proposed> left = new ArrayList<>(proposed);
        for (JsonObject expect : expected) {
            String why = null;
            Proposed match = null;
            for (Proposed p : left) {
                String m = mismatch(expect, p);
                if (m == null) {
                    match = p;
                    break;
                }
                why = why == null ? m + " in [" + p.rule() + "]" : why;
            }
            if (match == null) {
                return why;
            }
            left.remove(match);
        }
        return null;
    }

    /** The tokens of a rule (quote aware, the quotes left out), by name; the target as @target, the fault as @fault. */
    static Map<String, String> parse(String rule) {
        Map<String, String> answer = new LinkedHashMap<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < rule.length(); i++) {
            char ch = rule.charAt(i);
            if (quote != 0) {
                if (ch == '\\' && i + 1 < rule.length()) {
                    current.append(rule.charAt(++i));
                } else if (ch == quote) {
                    quote = 0;
                } else {
                    current.append(ch);
                }
            } else if (ch == '"' || ch == '\'') {
                quote = ch;
            } else if (ch == ' ' || ch == '\t') {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) {
            tokens.add(current.toString());
        }
        for (String token : tokens) {
            int eq = token.indexOf('=');
            if (eq < 0) {
                answer.put("@fault", token);
                continue;
            }
            String key = token.substring(0, eq);
            if (List.of("endpoint", "component", "route", "step").contains(key)) {
                answer.put("@target", token);
            } else {
                answer.put(key, token.substring(eq + 1));
            }
        }
        return answer;
    }

    // ---- the eval, against a local model ----

    @Test
    void evalAgainstALocalModel() throws Exception {
        String model = System.getenv("CAMEL_TUI_CHAOS_EVAL");
        assumeTrue(model != null && !model.isBlank(), "set CAMEL_TUI_CHAOS_EVAL=<model> to run the eval");
        String host = System.getenv("OLLAMA_HOST");
        String url = host != null && !host.isBlank() ? host : "http://localhost:11434";
        url = url.startsWith("http") ? url : "http://" + url;
        int runs = Integer.parseInt(System.getenv().getOrDefault("CAMEL_TUI_CHAOS_EVAL_RUNS", "1"));
        String only = System.getenv("CAMEL_TUI_CHAOS_EVAL_ONLY");

        JsonObject set;
        try (InputStream is = getClass().getResourceAsStream("/chaos-eval/requests.json")) {
            set = (JsonObject) Jsoner.deserialize(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        try (CamelContext context = chaosDemo()) {
            ChaosManager chaos = ChaosManager.get(context);

            AiPanelToolGroupsTest.FakeApp app = new AiPanelToolGroupsTest.FakeApp();
            app.features = AppFeatures.fromStatus(new JsonObject(Map.of("chaos", Map.of("enabled", true))));
            AiPanel panel = AiPanelToolGroupsTest.panel(AiPanel.TOOL_MODE_CORE, app);
            panel.refreshToolGroupsForTesting();
            String system = panel.systemPromptForTesting();
            JsonArray tools = wireTools(panel.toolDefinitionsForTesting());

            System.out.println("Chaos request eval with " + model + " at " + url + ", " + tools.size() + " tools, "
                               + runs + " run(s)");
            List<Outcome> outcomes = new ArrayList<>();
            for (Object o : set.getCollection("requests")) {
                JsonObject request = (JsonObject) o;
                String id = request.getString("id");
                if (only != null && !only.isBlank() && !id.contains(only)) {
                    continue;
                }
                List<Object> expect = request.getCollection("expect");
                List<JsonObject> expected = expect.stream().map(JsonObject.class::cast).toList();
                // the rules the integration has when the request is made: only those of the request
                chaos.reset();
                JsonArray existing = new JsonArray();
                List<Object> rules = request.getCollection("existingRules");
                for (Object r : rules != null ? rules : List.of()) {
                    String rule = ((JsonObject) r).getString("rule");
                    String ruleId = chaos.addRule(rule);
                    existing.add(new JsonObject(Map.of("id", ruleId, "version", 1, "state", "active", "rule", rule)));
                }
                IntegrationInfo info = integration(context, existing);
                for (int run = 0; run < runs; run++) {
                    Outcome outcome = converse(http, url, model, system, tools, chaos, info, id,
                            request.getString("request"), expected);
                    outcomes.add(outcome);
                    System.out.println(line(outcome));
                }
            }
            long passed = outcomes.stream().filter(Outcome::passed).count();
            System.out.printf(Locale.ROOT, "%d of %d passed (%.0f%%), %.1fs and %d tool calls on average%n", passed,
                    outcomes.size(), 100.0 * passed / Math.max(1, outcomes.size()),
                    outcomes.stream().mapToDouble(Outcome::seconds).average().orElse(0),
                    Math.round(outcomes.stream().mapToInt(Outcome::toolCalls).average().orElse(0)));
            assertThat(outcomes).isNotEmpty();
        }
    }

    private static String line(Outcome o) {
        return String.format(Locale.ROOT, "%-4s %-24s %5.1fs %2d calls %2d checks %6d tok  %s",
                o.passed() ? "PASS" : "FAIL", o.id(), o.seconds(), o.toolCalls(), o.checks(), o.tokens(),
                o.passed()
                        ? o.proposed().stream().map(p -> (p.change() != null ? p.change() + " -> " : "") + p.rule()).toList()
                        : o.failure());
    }

    /** One request, as a conversation: the tool calls are answered until the model adds or changes rules. */
    private static Outcome converse(
            HttpClient http, String url, String model, String system, JsonArray tools, ChaosManager chaos,
            IntegrationInfo info, String id, String request, List<JsonObject> expected)
            throws Exception {
        JsonArray messages = new JsonArray();
        messages.add(message("system", system));
        messages.add(message("user",
                "[Monitoring chaosdemo (PID 4711)]\n" + ChaosAiPrompt.ofRequest(info, request, RECIPES)));
        List<Proposed> proposed = new ArrayList<>();
        int calls = 0;
        int checks = 0;
        long tokens = 0;
        boolean saidYes = false;
        long start = System.nanoTime();
        for (int turn = 0; turn < MAX_TURNS && proposed.isEmpty(); turn++) {
            JsonObject reply = chat(http, url, model, messages, tools);
            tokens += reply.getLongOrDefault("prompt_eval_count", 0L) + reply.getLongOrDefault("eval_count", 0L);
            JsonObject msg = (JsonObject) reply.get("message");
            messages.add(msg);
            List<Object> toolCalls = msg.getCollection("tool_calls");
            if (toolCalls == null || toolCalls.isEmpty()) {
                if (saidYes) {
                    break;
                }
                // it asks before adding, as in the live try: say yes once
                messages.add(message("user", YES));
                saidYes = true;
                continue;
            }
            for (Object tc : toolCalls) {
                JsonObject function = (JsonObject) ((JsonObject) tc).get("function");
                String name = function.getString("name");
                Object rawArgs = function.get("arguments");
                JsonObject args = rawArgs instanceof JsonObject jo
                        ? jo : (JsonObject) Jsoner.deserialize(String.valueOf(rawArgs));
                calls++;
                String result;
                switch (name) {
                    case TuiToolGroups.CHAOS_CHECK_TOOL -> {
                        checks++;
                        result = check(chaos, args.getString("rule"));
                    }
                    case TuiToolGroups.CHAOS_ADD_TOOL -> {
                        for (String rule : splitRules(args.getString("rule"))) {
                            proposed.add(new Proposed(null, rule));
                        }
                        result = "{\"added\": true, \"note\": \"the user confirmed\"}";
                    }
                    case TuiToolGroups.CHAOS_CHANGE_TOOL -> {
                        proposed.add(new Proposed(args.getString("id"), args.getString("rule")));
                        result = "{\"changed\": true, \"note\": \"the user confirmed\"}";
                    }
                    default -> result = "{\"error\": \"" + name + " is not available here: use the chaos tools\"}";
                }
                if (VERBOSE) {
                    System.out.println("     " + id + " " + name + " " + args.toJson() + " -> "
                                       + (result.length() > 300 ? result.substring(0, 300) + "..." : result));
                }
                JsonObject toolMessage = message("tool", result);
                toolMessage.put("tool_name", name);
                messages.add(toolMessage);
            }
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        return new Outcome(id, score(expected, proposed), proposed, calls, checks, seconds, tokens);
    }

    private static String check(ChaosManager chaos, String rule) {
        if (rule == null || rule.isBlank()) {
            return "{\"error\": \"rule is required\"}";
        }
        try {
            return Jsoner.serialize(chaos.checkRules(rule));
        } catch (IllegalArgumentException e) {
            return "{\"error\": " + Jsoner.serialize(e.getMessage()) + "}";
        }
    }

    // the rules of a text with several separated by ; (a ; in quotes does not separate)
    private static List<String> splitRules(String text) {
        List<String> answer = new ArrayList<>();
        if (text == null) {
            return answer;
        }
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (char ch : text.toCharArray()) {
            if (quote != 0) {
                quote = ch == quote ? 0 : quote;
                current.append(ch);
            } else if (ch == ';') {
                answer.add(current.toString().strip());
                current.setLength(0);
            } else {
                quote = ch == '"' || ch == '\'' ? ch : 0;
                current.append(ch);
            }
        }
        answer.add(current.toString().strip());
        answer.removeIf(String::isEmpty);
        return answer;
    }

    private static JsonObject chat(HttpClient http, String url, String model, JsonArray messages, JsonArray tools)
            throws Exception {
        JsonObject options = new JsonObject();
        options.put("temperature", 0.3);
        options.put("num_ctx", 32768);
        JsonObject request = new JsonObject();
        request.put("model", model);
        request.put("messages", messages);
        request.put("tools", tools);
        request.put("stream", false);
        request.put("think", false);
        request.put("keep_alive", "30m");
        request.put("options", options);
        HttpResponse<String> response = http.send(
                HttpRequest.newBuilder(URI.create(url + "/api/chat"))
                        .timeout(Duration.ofMinutes(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(request.toJson(), StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        return (JsonObject) Jsoner.deserialize(response.body());
    }

    private static JsonObject message(String role, String content) {
        JsonObject m = new JsonObject();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    private static JsonArray wireTools(List<LlmClient.ToolDef> defs) {
        JsonArray tools = new JsonArray();
        for (LlmClient.ToolDef def : defs) {
            JsonObject function = new JsonObject();
            function.put("name", def.name());
            function.put("description", def.description());
            function.put("parameters", def.parameters());
            JsonObject tool = new JsonObject();
            tool.put("type", "function");
            tool.put("function", function);
            tools.add(tool);
        }
        return tools;
    }

    // ---- the routes of the chaos demo, with camel-chaos on ----

    private static CamelContext chaosDemo() throws Exception {
        CamelContext context = new DefaultCamelContext();
        Properties props = new Properties();
        props.put("camel.chaos.enabled", "true");
        props.put("camel.chaos.seed", "42");
        context.getPropertiesComponent().setOverrideProperties(props);
        // the HTTP services, which the routes call but never reach: the routes do not run (the timer waits)
        context.addComponent("http", new SilentComponent());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("seda:dlq").maximumRedeliveries(2).redeliveryDelay(200));
                from("timer:orders?period=500&delay=3600000").routeId("orders")
                        .setHeader("country", constant("DK")).id("pickCountry")
                        .to("direct:inventory").id("callInventory")
                        .to("http://localhost:8765/stock").id("callStock")
                        .to("direct:payment").id("callPayment")
                        .to("seda:shipping").id("toShipping");
                from("direct:inventory").routeId("inventory")
                        .setHeader("reserved", constant(true)).id("reserve")
                        .log("reserved ${body}").id("logReserved");
                from("direct:payment").routeId("payment")
                        .to("http://localhost:8765/pay").id("callPay");
                from("seda:shipping").routeId("shipping")
                        .log("shipping ${header.orderId}").id("logShipping");
                from("file:target/chaos-eval/inbox?delay=3600000&initialDelay=3600000").routeId("returns")
                        .log("return ${header.CamelFileName}").id("logReturn");
                from("seda:dlq").routeId("dlq").log("DLQ ${body}");
            }
        });
        context.start();
        return context;
    }

    /** The integration as the TUI sees it: its routes, the endpoints each sends to, and its chaos rules. */
    private static IntegrationInfo integration(CamelContext context, JsonArray rules) {
        IntegrationInfo info = new IntegrationInfo();
        info.pid = "4711";
        info.name = "chaosdemo";
        for (Route route : context.getRoutes()) {
            RouteInfo r = new RouteInfo();
            r.routeId = route.getRouteId();
            r.from = route.getEndpoint().getEndpointUri();
            info.routes.add(r);
            for (Processor p : route.filter("*")) {
                if (p instanceof EndpointAware aware && aware.getEndpoint() != null) {
                    EndpointInfo e = new EndpointInfo();
                    e.uri = aware.getEndpoint().getEndpointUri();
                    e.direction = "out";
                    e.routeId = route.getRouteId();
                    info.endpoints.add(e);
                }
            }
        }
        info.chaos = ChaosInfo.parse(new JsonObject(Map.of("enabled", true, "rules", rules)));
        return info;
    }

    /** An HTTP component that answers nothing: the eval only needs its endpoints. */
    static final class SilentComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new DefaultEndpoint(uri, this) {
                @Override
                public Producer createProducer() {
                    return new DefaultProducer(this) {
                        @Override
                        public void process(Exchange exchange) {
                            // no answer
                        }
                    };
                }

                @Override
                public Consumer createConsumer(Processor processor) {
                    throw new UnsupportedOperationException("the eval has no HTTP server");
                }
            };
        }
    }
}
