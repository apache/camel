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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.aesh.command.CommandDefinition;
import org.aesh.command.option.Argument;
import org.aesh.command.option.Mixin;
import org.aesh.command.option.Option;
import org.apache.camel.dsl.jbang.core.common.CliExitCode;
import org.apache.camel.dsl.jbang.core.common.JsonOutput;
import org.apache.camel.dsl.jbang.core.common.ProcessFinder.CamelProcess;
import org.apache.camel.dsl.jbang.core.common.TerminalWidthHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * The GenAI calls of an integration from the genai dev console of camel-ai-observability: the model calls per model,
 * and the newest agent runs with their model calls and tool calls (CAMEL-27313).
 */
@CommandDefinition(name = "genai",
                   description = "Display the model calls, agent runs and tool calls of the Camel AI components",
                   generateHelp = true,
                   footer = {
                           "Examples:",
                           "  camel get genai",
                           "  camel get genai --content",
                           "  camel get genai --id 7",
                           "  camel get genai --watch" })
public class CamelGenAiAction extends ActionWatchCommand {

    static final String NO_CONSOLE = "has no genai dev console: set camel.aiObservability.level (detailed records the "
                                     + "agent runs and tool calls, full the messages), and camel run adds "
                                     + "camel-ai-observability";
    private static final String AGENT_RUN = "invoke_agent";
    private static final String TOOL_CALL = "execute_tool";

    @Argument(description = "Name or pid of running Camel integration")
    String name = "*";

    @Option(name = "limit", description = "The number of the newest agent runs and model calls to show",
            defaultValue = "20")
    int limit = 20;

    @Option(name = "id", description = "Show one call (an agent run, a model call or a tool call) with its messages")
    String id;

    @Option(name = "content", hasValue = false,
            description = "Show the question of each agent run (recorded at camel.aiObservability.level=full)")
    boolean content;

    @Option(name = "json", hasValue = false, description = "Output the answer of the genai dev console as JSON")
    boolean jsonOutput;

    @Mixin
    ActionTimeoutMixin timeoutOption;

    @Override
    public Integer doWatchCall() throws Exception {
        CamelProcess process = findOne(name);
        JsonObject answer = executeAction(process, "genai", root -> {
            if (id != null) {
                root.put("id", id);
            } else {
                root.put("limit", String.valueOf(Math.max(0, limit)));
                if (content) {
                    root.put("content", "true");
                }
            }
        });
        if (answer == null || Boolean.FALSE.equals(answer.getBoolean("enabled")) || answer.getString("level") == null) {
            printer().printDiagnostic(process.name() + " " + NO_CONSOLE);
            return CliExitCode.OK;
        }
        if (jsonOutput) {
            JsonOutput.print(printer(), answer);
            return id != null && answer.getMap("call") == null ? CliExitCode.NOT_FOUND : CliExitCode.OK;
        }
        if (id != null) {
            JsonObject call = answer.getMap("call");
            if (call == null) {
                printer().printDiagnostic(answer.getStringOrDefault("error", "No GenAI call with id " + id));
                return CliExitCode.NOT_FOUND;
            }
            printCall(call);
            return CliExitCode.OK;
        }
        printSummary(answer);
        printModels(answer);
        printCalls(answer);
        return CliExitCode.OK;
    }

    private void printSummary(JsonObject answer) {
        StringBuilder sb = new StringBuilder();
        sb.append("GenAI level: ").append(answer.getString("level"));
        if (!Boolean.TRUE.equals(answer.getBoolean("recording"))) {
            sb.append(" (not recording: the dev console is off, camel.main.devConsoleEnabled=true)");
        }
        sb.append("   Model calls: ").append(number(answer, "modelCalls"))
                .append("   Agent runs: ").append(number(answer, "agentRuns"))
                .append("   Tool calls: ").append(number(answer, "toolCalls"))
                .append("   Failed: ").append(number(answer, "errors"));
        printer().println(sb.toString());
        printer().println("Tokens: in " + number(answer, "inputTokens") + " (cached " + number(answer, "cachedInputTokens")
                          + "), out " + number(answer, "outputTokens"));
    }

    private void printModels(JsonObject answer) {
        List<JsonObject> models = objects(answer, "models");
        if (models.isEmpty()) {
            return;
        }
        printer().println("");
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, models, List.of(
                new Column().header("PROVIDER").dataAlign(HorizontalAlign.LEFT).with(m -> text(m, "provider")),
                new Column().header("MODEL").dataAlign(HorizontalAlign.LEFT).with(m -> text(m, "model")),
                new Column().header("CALLS").dataAlign(HorizontalAlign.RIGHT).with(m -> "" + number(m, "calls")),
                new Column().header("FAILED").dataAlign(HorizontalAlign.RIGHT).with(m -> "" + number(m, "errors")),
                new Column().header("IN").dataAlign(HorizontalAlign.RIGHT).with(m -> "" + number(m, "inputTokens")),
                new Column().header("CACHED").dataAlign(HorizontalAlign.RIGHT)
                        .with(m -> "" + number(m, "cachedInputTokens")),
                new Column().header("OUT").dataAlign(HorizontalAlign.RIGHT).with(m -> "" + number(m, "outputTokens")),
                new Column().header("AVG").dataAlign(HorizontalAlign.RIGHT)
                        .with(m -> number(m, "calls") > 0
                                ? TimeUtils.printDuration(number(m, "durationMs") / number(m, "calls"), true) : ""))));
    }

    /** The top-level calls, newest first, each agent run followed by its steps. */
    private void printCalls(JsonObject answer) {
        List<Row> rows = new ArrayList<>();
        for (JsonObject call : objects(answer, "calls")) {
            rows.add(new Row(call, false));
            for (JsonObject step : objects(call, "steps")) {
                rows.add(new Row(step, true));
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        int tw = terminalWidth();
        int callWidth = TerminalWidthHelper.flexWidth(tw, 70, TerminalWidthHelper.noBorderOverhead(9), 20, 60);
        List<ColumnData<Row>> columns = new ArrayList<>();
        columns.add(new Column().header("ID").dataAlign(HorizontalAlign.RIGHT).with(r -> text(r.call(), "id")));
        columns.add(new Column().header("AGE").dataAlign(HorizontalAlign.RIGHT).with(Row::age));
        columns.add(new Column().header("ROUTE").dataAlign(HorizontalAlign.LEFT)
                .maxWidth(25, OverflowBehaviour.ELLIPSIS_RIGHT)
                .with(r -> r.step() ? "" : text(r.call(), "routeId")));
        columns.add(new Column().header("CALL").dataAlign(HorizontalAlign.LEFT)
                .maxWidth(callWidth, OverflowBehaviour.ELLIPSIS_RIGHT)
                .with(r -> (r.step() ? "  " : "") + text(r.call(), "name")));
        columns.add(new Column().header("IN").dataAlign(HorizontalAlign.RIGHT).with(r -> tokens(r.call(), "inputTokens")));
        columns.add(new Column().header("OUT").dataAlign(HorizontalAlign.RIGHT).with(r -> tokens(r.call(), "outputTokens")));
        columns.add(new Column().header("TIME").dataAlign(HorizontalAlign.RIGHT).with(Row::time));
        columns.add(new Column().header("FINISH").dataAlign(HorizontalAlign.LEFT).with(Row::finish));
        if (content) {
            columns.add(new Column().header("QUESTION").dataAlign(HorizontalAlign.LEFT)
                    .maxWidth(Math.max(20, tw / 3), OverflowBehaviour.ELLIPSIS_RIGHT)
                    .with(r -> r.step() ? "" : oneLine(r.call().getString("question"))));
        }
        printer().println("");
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, columns));
        long more = number(answer, "moreCalls");
        if (more > 0) {
            printer().println("+" + more + " older calls (the console keeps the newest 50)");
        }
    }

    /** One call with its messages: the question, each step's answer or tool arguments and result, the answer. */
    private void printCall(JsonObject call) {
        StringBuilder head = new StringBuilder("#").append(text(call, "id")).append(' ').append(text(call, "name"));
        if (call.getString("routeId") != null) {
            head.append("   route: ").append(call.getString("routeId"));
        }
        head.append("   ").append(new Row(call, false).time());
        if (call.get("inputTokens") != null) {
            head.append("   tokens: in ").append(number(call, "inputTokens")).append(", out ")
                    .append(number(call, "outputTokens"));
        }
        printer().println(head.toString());
        List<JsonObject> steps = objects(call, "steps");
        JsonObject first = steps.stream().filter(s -> !isRunOrTool(s)).findFirst().orElse(null);
        String system = first != null ? systemText(content(first).get("systemInstructions")) : null;
        if (system == null) {
            // camel-openai records the system message with the input of the run, not as system instructions
            system = lastText(content(call).get("inputMessages"), "system");
        }
        if (system != null) {
            printer().println("system: " + system);
        }
        String question = call.getString("question");
        if (question == null && steps.isEmpty()) {
            question = lastText(content(call).get("inputMessages"), "user");
        }
        if (question != null) {
            printer().println("question: " + question);
        }
        for (JsonObject step : steps) {
            printer().println("  #" + text(step, "id") + " " + text(step, "name") + "   " + new Row(step, true).time()
                              + stepText(step));
        }
        String answer = call.getString("answer");
        if (answer == null && steps.isEmpty()) {
            answer = lastText(content(call).get("outputMessages"), "assistant");
        }
        if (answer != null) {
            printer().println("answer: " + answer);
        }
        if (call.getMap("content") == null && steps.stream().allMatch(s -> s.getMap("content") == null)) {
            printer().println("(no messages: they are recorded at camel.aiObservability.level=full)");
        }
    }

    /** A model call's answer (text or tool calls), or a tool call's arguments and result. */
    private static String stepText(JsonObject step) {
        JsonObject c = content(step);
        if (TOOL_CALL.equals(step.getString("operation"))) {
            Object args = c.get("toolArguments");
            Object result = c.get("toolResult");
            return args == null && result == null ? "" : ": " + oneLine(str(args)) + " -> " + oneLine(str(result));
        }
        List<String> parts = new ArrayList<>();
        if (c.get("outputMessages") instanceof List<?> messages) {
            for (Object m : messages) {
                if (m instanceof Map<?, ?> message && message.get("parts") instanceof List<?> ps) {
                    for (Object p : ps) {
                        if (p instanceof Map<?, ?> part) {
                            if ("tool_call".equals(part.get("type"))) {
                                parts.add("tool call " + part.get("name") + " " + oneLine(str(part.get("arguments"))));
                            } else if ("text".equals(part.get("type")) && part.get("content") != null
                                    && !part.get("content").toString().isBlank()) {
                                parts.add(oneLine(part.get("content").toString()));
                            }
                        }
                    }
                }
            }
        }
        String finish = step.getString("finishReason");
        return (finish != null ? " (" + finish + ")" : "") + (parts.isEmpty() ? "" : ": " + String.join("; ", parts));
    }

    private static String systemText(Object value) {
        Object json = value instanceof String s ? parse(s) : value;
        if (json instanceof List<?> parts) {
            for (Object p : parts) {
                if (p instanceof Map<?, ?> part && part.get("content") != null) {
                    return oneLine(part.get("content").toString());
                }
            }
        }
        return json instanceof String s ? oneLine(s) : null;
    }

    private static String lastText(Object messages, String role) {
        String found = null;
        if (messages instanceof List<?> list) {
            for (Object m : list) {
                if (m instanceof Map<?, ?> message && role.equals(message.get("role"))
                        && message.get("parts") instanceof List<?> ps) {
                    for (Object p : ps) {
                        if (p instanceof Map<?, ?> part && "text".equals(part.get("type")) && part.get("content") != null) {
                            found = oneLine(part.get("content").toString());
                        }
                    }
                }
            }
        }
        return found;
    }

    private static boolean isRunOrTool(JsonObject call) {
        String operation = call.getString("operation");
        return AGENT_RUN.equals(operation) || TOOL_CALL.equals(operation);
    }

    private static JsonObject content(JsonObject call) {
        JsonObject c = call.getMap("content");
        return c != null ? c : new JsonObject();
    }

    private static List<JsonObject> objects(JsonObject json, String key) {
        List<JsonObject> list = new ArrayList<>();
        JsonArray array = json.getCollection(key);
        if (array != null) {
            for (Object o : array) {
                if (o instanceof JsonObject jo) {
                    list.add(jo);
                }
            }
        }
        return list;
    }

    private static Object parse(String text) {
        try {
            return Jsoner.deserialize(text);
        } catch (Exception e) {
            return text;
        }
    }

    private static String str(Object value) {
        if (value == null) {
            return "";
        }
        return value instanceof Map || value instanceof List ? Jsoner.serialize(value) : value.toString();
    }

    private static String oneLine(String text) {
        return text != null ? text.replaceAll("\\s+", " ").strip() : null;
    }

    private static String text(JsonObject json, String key) {
        String value = json.getString(key);
        return value != null ? value : "";
    }

    private static long number(JsonObject json, String key) {
        return json.get(key) instanceof Number n ? n.longValue() : 0;
    }

    private static String tokens(JsonObject call, String key) {
        return call.get(key) instanceof Number n ? n.toString() : "";
    }

    private record Row(JsonObject call, boolean step) {

        String age() {
            long timestamp = number(call, "timestamp");
            return step || timestamp <= 0 ? "" : TimeUtils.printSince(timestamp);
        }

        String time() {
            if (Boolean.TRUE.equals(call.getBoolean("running"))) {
                return "running";
            }
            return call.get("durationMs") instanceof Number n ? TimeUtils.printDuration(n.longValue(), true) : "";
        }

        String finish() {
            String error = call.getString("error");
            if (error != null) {
                return "failed: " + error;
            }
            String finish = call.getString("finishReason");
            return finish != null ? finish : "";
        }
    }
}
