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
package org.apache.camel.cli.connector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The {@code chaos} action: adds, changes and removes the rules of camel-chaos in the running integration, as
 * {@code camel cmd chaos-add}, {@code chaos-change}, {@code chaos-remove}, {@code chaos-clear} and {@code chaos-load}
 * (a scenario) send it, for example {@code {"action":"chaos","command":"add","rule":"endpoint=kafka:orders* error
 * rate=0.3 until=1m"}}.
 * <p/>
 * It works only when chaos is switched on in the integration (profile {@code dev} or {@code camel.chaos.enabled=true},
 * never profile {@code prod}): only then has the context a {@code ChaosManager}, and the action never creates one. A
 * rule goes through the same parser as {@code camel.chaos.rules}; a rule that is not valid is reported as an error with
 * the reason, and an id that does not exist as not found. The chaos dev console only reads, so rules cannot be added
 * over HTTP.
 * <p/>
 * camel-cli-connector does not depend on camel-chaos: with {@code camel run}, camel-chaos comes with {@code --chaos} or
 * {@code --dep camel:chaos} in the class loader of the integration, which the connector cannot link against. So the
 * manager is looked up by its class name through the class resolver of the context, and its public methods are called
 * by reflection.
 */
final class ChaosAction {

    static final String CHAOS_MANAGER = "org.apache.camel.component.chaos.ChaosManager";

    private ChaosAction() {
    }

    static void dispatch(CamelContext camelContext, JsonObject root, CliActionOutput output) throws Exception {
        Class<?> type = camelContext.getClassResolver().resolveClass(CHAOS_MANAGER);
        if (type == null) {
            output.error("camel-chaos is not on the classpath of this integration: run it with --chaos, or add the"
                         + " camel-chaos dependency (camel run --dep camel:chaos)");
            return;
        }
        Object chaos = camelContext.getCamelContextExtension().getContextPlugin(type);
        if (chaos == null) {
            output.error("Chaos is off in this integration: it is switched on with profile dev (unless"
                         + " camel.chaos.enabled=false) or with camel.chaos.enabled=true, and never with profile prod");
            return;
        }
        String command = root.getString("command");
        String id = root.getString("id");
        String rule = root.getString("rule");
        JsonObject answer = new JsonObject();
        try {
            if ("add".equals(command)) {
                if (isBlank(rule)) {
                    output.error("A rule is required");
                    return;
                }
                // one rule or several separated by ; (as camel.chaos.rules), added together
                List<?> ids = (List<?>) call(type, chaos, "addRules", rule);
                if (ids.size() == 1) {
                    answer.put("id", ids.get(0));
                }
                answer.put("ids", new JsonArray(ids));
                answer.put("version", 1);
                descriptions(type, chaos, ids, answer);
                warnings(type, chaos, ids, answer);
            } else if ("suggest".equals(command)) {
                // what to try: rules suggested per route from what the routes do (read only)
                Method suggest = type.getMethod("suggest");
                @SuppressWarnings("unchecked")
                Map<String, Object> suggested = (Map<String, Object>) suggest.invoke(chaos);
                answer.putAll(suggested);
            } else if ("load".equals(command)) {
                String text = root.getString("text");
                if (isBlank(text)) {
                    output.error("The scenario is required");
                    return;
                }
                String scenario = root.getString("name");
                // the rules there are now are removed and the report starts again, unless a line is not valid
                List<?> ids = (List<?>) call(type, chaos, "loadScenario", isBlank(scenario) ? "scenario" : scenario, text);
                answer.put("ids", new JsonArray(ids));
                answer.put("reset", true);
                descriptions(type, chaos, ids, answer);
                warnings(type, chaos, ids, answer);
            } else if ("check".equals(command)) {
                if (isBlank(rule)) {
                    output.error("A rule is required");
                    return;
                }
                // checked as add checks it, and not added: what it would hit now, in plain words
                @SuppressWarnings("unchecked")
                Map<String, Object> checked = (Map<String, Object>) call(type, chaos, "checkRules", rule);
                answer.putAll(checked);
            } else if ("change".equals(command)) {
                if (isBlank(id) || isBlank(rule)) {
                    output.error("The id of the rule and the new rule are required");
                    return;
                }
                if (!Boolean.TRUE.equals(call(type, chaos, "hasRule", id))) {
                    output.notFound("There is no chaos rule " + id);
                    return;
                }
                answer.put("id", id);
                answer.put("version", call(type, chaos, "changeRule", id, rule));
                descriptions(type, chaos, List.of(id), answer);
                warnings(type, chaos, List.of(id), answer);
            } else if ("remove".equals(command)) {
                if (isBlank(id)) {
                    output.error("The id of the rule is required");
                    return;
                }
                if (!Boolean.TRUE.equals(call(type, chaos, "removeRule", id))) {
                    output.notFound("There is no chaos rule " + id);
                    return;
                }
                answer.put("id", id);
                answer.put("removed", 1);
            } else if ("clear".equals(command)) {
                // reset also starts a new report: the removed rules, the counts per route and the verdict are gone
                boolean reset = root.getBooleanOrDefault("reset", false);
                answer.put("removed", call(type, chaos, reset ? "reset" : "clearRules"));
                if (reset) {
                    answer.put("reset", true);
                }
            } else if ("replay".equals(command)) {
                String route = root.getString("route");
                if (isBlank(route)) {
                    output.error("The id of the route is required");
                    return;
                }
                // the files a file route took are put back, so it takes the same files again (CAMEL-26817)
                @SuppressWarnings("unchecked")
                Map<String, Object> replayed = (Map<String, Object>) call(type, chaos, "replayFiles", route);
                answer.putAll(replayed);
            } else if ("explore-start".equals(command)) {
                // an exploration (CAMEL-26773): camel-chaos keeps its journal and ends its rules at the deadline
                @SuppressWarnings("unchecked")
                Map<String, Object> started = (Map<String, Object>) call(type, chaos, "startExploration",
                        root.getString("budget"), root.getString("focus"));
                answer.putAll(started);
            } else if ("explore-finish".equals(command)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> finished = (Map<String, Object>) call(type, chaos, "finishExploration");
                answer.putAll(finished);
            } else if ("experiment-begin".equals(command)) {
                if (isBlank(rule)) {
                    output.error("The rules of the experiment are required");
                    return;
                }
                // an experiment of the exploration (CAMEL-26774): its rules are added together, with an expectation
                // the route the feed reaches, so an experiment that would say nothing is refused (CAMEL-26803)
                String feedRoute = root.getString("feedRoute");
                @SuppressWarnings("unchecked")
                Map<String, Object> begun = (Map<String, Object>) (feedRoute != null
                        ? call(type, chaos, "beginExperiment", root.getString("hypothesis"), rule, feedRoute)
                        : call(type, chaos, "beginExperiment", root.getString("hypothesis"), rule));
                answer.putAll(begun);
                // a rule that matches nothing makes the experiment say nothing: as with add, the reason goes along
                warnings(type, chaos, (List<?>) begun.get("rules"), answer);
            } else if ("experiment-end".equals(command)) {
                // what came of it, and its rules are removed
                @SuppressWarnings("unchecked")
                Map<String, Object> ended = (Map<String, Object>) call(type, chaos, "endExperiment",
                        root.getString("note"));
                answer.putAll(ended);
            } else {
                output.error("Unknown chaos command: " + command
                             + " (add, check, change, remove, clear, load, suggest, replay, explore-start,"
                             + " explore-finish, experiment-begin or experiment-end)");
                return;
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            // a rule that is not valid, an id that does not exist, or too many rules: the reason goes to the tool
            if ("change".equals(command) && !Boolean.TRUE.equals(call(type, chaos, "hasRule", id))) {
                // the rule was removed after the check above (by chaos-remove, chaos-clear or addRule)
                output.notFound("There is no chaos rule " + id);
                return;
            }
            output.error(e.getMessage());
            return;
        }
        output.write(answer);
    }

    /**
     * The warnings of the rules that match nothing in the integration now (often a typo), each with the id of its rule,
     * for the tool to show: the rule is added anyway, as the routes may still change.
     */
    private static void warnings(Class<?> type, Object chaos, List<?> ids, JsonObject answer) throws Exception {
        JsonArray warnings = new JsonArray();
        for (Object id : ids) {
            for (Object warning : (List<?>) call(type, chaos, "checkRule", (String) id)) {
                warnings.add(id + ": " + warning);
            }
        }
        if (!warnings.isEmpty()) {
            answer.put("warnings", warnings);
        }
    }

    /**
     * The rules in plain words, in the order of their ids ({@code descriptions}), and as {@code description} for one
     * rule, such as {@code fail half of the sends to http:stock* with HTTP status 503, for 5 minutes}. A rule removed
     * meanwhile has none.
     */
    private static void descriptions(Class<?> type, Object chaos, List<?> ids, JsonObject answer) throws Exception {
        JsonArray words = new JsonArray();
        for (Object id : ids) {
            try {
                words.add(call(type, chaos, "describeRule", (String) id));
            } catch (IllegalArgumentException e) {
                // removed by another command meanwhile
                words.add(null);
            } catch (NoSuchMethodException e) {
                // a manager without the words
                return;
            }
        }
        answer.put("descriptions", words);
        if (words.size() == 1 && words.get(0) != null) {
            answer.put("description", words.get(0));
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /**
     * Calls a public method of the manager, and throws what it threw (such as the IllegalArgumentException of a rule
     * that is not valid).
     */
    private static Object call(Class<?> type, Object chaos, String name, String... args) throws Exception {
        Class<?>[] types = new Class<?>[args.length];
        Arrays.fill(types, String.class);
        Method method = type.getMethod(name, types);
        try {
            return method.invoke(chaos, (Object[]) args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            if (e.getCause() instanceof LinkageError cause) {
                // such as a NoClassDefFoundError of an exception= class: the reason goes to the tool
                throw new IllegalStateException(cause.toString(), cause);
            }
            throw e;
        }
    }
}
