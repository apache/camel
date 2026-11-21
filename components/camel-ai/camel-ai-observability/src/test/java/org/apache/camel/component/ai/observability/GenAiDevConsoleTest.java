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
package org.apache.camel.component.ai.observability;

import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.console.DevConsole;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GenAiDevConsoleTest extends ExchangeTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setDevConsole(true);
        return context;
    }

    private void level(String level) {
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, level);
    }

    @Test
    void theConsoleIsFoundByName() throws Exception {
        DevConsole console = PluginHelper.getDevConsoleResolver(context).resolveDevConsole("genai");

        assertThat(console).isInstanceOf(GenAiDevConsole.class);
        assertThat(console.isReadOnly()).isTrue();
    }

    @Test
    void aModelCallIsRecordedWithoutATracer() {
        chat(new DefaultExchange(context), null, 120, 30, 100L, "stop");

        JsonObject json = call(Map.of());

        assertThat(json).containsEntry("recording", true).containsEntry("level", "basic")
                .containsEntry("modelCalls", 1L).containsEntry("agentRuns", 0L)
                .containsEntry("inputTokens", 120L).containsEntry("cachedInputTokens", 100L);
        List<JsonObject> models = json.getCollection("models");
        assertThat(models).singleElement().satisfies(model -> assertThat(model)
                .containsEntry("provider", "ollama").containsEntry("model", "granite4:3b").containsEntry("calls", 1L));
        List<JsonObject> calls = json.getCollection("calls");
        assertThat(calls).singleElement().satisfies(call -> assertThat(call)
                .containsEntry("operation", "chat").containsEntry("running", false)
                .containsEntry("finishReason", "stop").doesNotContainKey("content"));
    }

    @Test
    void anAgentRunHasItsModelCallsAndToolCallsInOrder() {
        level("detailed");
        Exchange exchange = new DefaultExchange(context);
        GenAiObservation run = agentRun(exchange);
        chat(exchange, run, 300, 40, 270L, "tool_calls");
        tool(exchange.copy(), run);
        chat(exchange, run, 400, 20, null, "stop");
        run.close();

        JsonObject json = call(Map.of());

        assertThat(json).containsEntry("level", "detailed").containsEntry("modelCalls", 2L)
                .containsEntry("agentRuns", 1L).containsEntry("toolCalls", 1L);
        JsonObject agent = list(json, "calls").get(0);
        assertThat(agent).containsEntry("operation", "invoke_agent").containsEntry("agentName", "support")
                .containsEntry("modelCalls", 2).containsEntry("toolCalls", 1)
                .containsEntry("inputTokens", 700L).containsEntry("cachedInputTokens", 270L);
        List<JsonObject> steps = agent.getCollection("steps");
        assertThat(steps).extracting(step -> step.getString("name"))
                .containsExactly("chat granite4:3b", "execute_tool lookupStock", "chat granite4:3b");
        assertThat(steps).allSatisfy(step -> assertThat(step).containsEntry("parentId", agent.getString("id")));
    }

    @Test
    void callsStartedOnTheExchangeOfARunNestUnderIt() {
        level("detailed");
        // as camel-openai: the model calls of the run are started without a parent
        Exchange exchange = new DefaultExchange(context);
        GenAiObservation run = agentRun(exchange);
        chat(exchange, null, 10, 5, null, "tool_calls");
        Exchange copy = exchange.copy();
        GenAiObservation tool = tool(copy, null);
        chat(copy, null, 7, 3, null, "stop");
        tool.close();
        run.close();
        chat(exchange, null, 1, 1, null, "stop");

        List<JsonObject> calls = call(Map.of()).getCollection("calls");

        assertThat(calls).hasSize(2);
        assertThat(calls.get(0)).containsEntry("operation", "chat").doesNotContainKey("steps");
        List<JsonObject> steps = calls.get(1).getCollection("steps");
        assertThat(steps).extracting(step -> step.getString("operation"))
                .containsExactly("chat", "execute_tool", "chat");
        assertThat(steps.get(2)).containsEntry("parentId", steps.get(1).getString("id"));
    }

    @Test
    void theContentIsOnlyThereAtLevelFullAndWhenAsked() {
        level("detailed");
        conversation();
        assertThat(list(call(Map.of("content", "true")), "calls").get(0))
                .doesNotContainKeys("content", "question");

        level("full");
        conversation();
        JsonObject listed = list(call(Map.of()), "calls").get(0);
        assertThat(listed).doesNotContainKeys("content", "question");

        JsonObject withContent = list(call(Map.of("content", "true")), "calls").get(0);
        assertThat(withContent).containsEntry("question", "Is order ORD-1001 paid?")
                .containsEntry("answer", "Yes, it is paid.");
        JsonObject tool = list(withContent, "steps").get(1);
        Map<String, Object> content = tool.getMap("content");
        assertThat(content).containsEntry("toolArguments", "{\"order\":\"ORD-1001\"}")
                .containsEntry("toolResult", "paid");

        Map<String, Object> one = call(Map.of("id", listed.getString("id"))).getMap("call");
        assertThat(one).containsEntry("question", "Is order ORD-1001 paid?");
        assertThat(call(Map.of("id", "999"))).doesNotContainKey("call").containsKey("error");
    }

    @Test
    void onlyTheNewestCallsAreKeptButTheTotalsCountAll() {
        for (int i = 0; i < GenAiRecorder.MAX_CALLS + 10; i++) {
            chat(new DefaultExchange(context), null, 1, 1, null, "stop");
        }

        JsonObject json = call(Map.of("limit", "5"));

        assertThat(json).containsEntry("modelCalls", (long) GenAiRecorder.MAX_CALLS + 10)
                .containsEntry("moreCalls", (long) GenAiRecorder.MAX_CALLS + 5);
        assertThat(list(json, "calls")).hasSize(5);
    }

    @Test
    void nothingIsRecordedWhenTheDevConsoleIsOff() {
        context.setDevConsole(false);
        chat(new DefaultExchange(context), null, 1, 1, null, "stop");

        JsonObject json = call(Map.of());

        assertThat(json).containsEntry("recording", false).containsEntry("modelCalls", 0L);
    }

    @Test
    void theTextAnswerSummarizesTheCalls() {
        level("full");
        conversation();

        String text = (String) console().call(DevConsole.MediaType.TEXT, Map.of("content", "true"));

        assertThat(text).contains("GenAI (level: full)", "Model calls: 2, agent runs: 1, tool calls: 1",
                "ollama/granite4:3b: 2 calls", "invoke_agent support", "question: Is order ORD-1001 paid?",
                "execute_tool lookupOrder");
    }

    private void conversation() {
        Exchange exchange = new DefaultExchange(context);
        GenAiObservation run = agentRun(exchange);
        GenAiObservation first = start(exchange, chatContext(run));
        first.recordInputMessages(() -> new GenAiMessages().text(GenAiMessages.SYSTEM, "You answer the customers.")
                .text(GenAiMessages.USER, "Is order ORD-1001 paid?"));
        first.recordSuccess(GenAiUsage.of(50L, 10L, "tool_calls", null));
        first.close();
        GenAiObservation tool = start(exchange, GenAiObservationContext.builder()
                .operationName(GenAiOperationName.EXECUTE_TOOL).toolName("lookupOrder").parent(run).build());
        tool.recordToolArguments(() -> "{\"order\":\"ORD-1001\"}");
        tool.recordToolResult(() -> "paid");
        tool.close();
        GenAiObservation second = start(exchange, chatContext(run));
        second.recordOutputMessages(() -> new GenAiMessages().text(GenAiMessages.ASSISTANT, "Yes, it is paid."));
        second.recordSuccess(GenAiUsage.of(60L, 8L, "stop", null));
        second.close();
        run.close();
    }

    private GenAiObservation agentRun(Exchange exchange) {
        return start(exchange, GenAiObservationContext.builder()
                .operationName(GenAiOperationName.INVOKE_AGENT).agentName("support").system("ollama")
                .requestModel("granite4:3b").build());
    }

    private void chat(Exchange exchange, GenAiObservation parent, long in, long out, Long cached, String finish) {
        GenAiObservation chat = start(exchange, chatContext(parent));
        chat.recordCachedInputTokens(cached);
        chat.recordSuccess(GenAiUsage.of(in, out, finish, null));
        chat.close();
    }

    private GenAiObservation tool(Exchange exchange, GenAiObservation parent) {
        GenAiObservation tool = start(exchange, GenAiObservationContext.builder()
                .operationName(GenAiOperationName.EXECUTE_TOOL).toolName("lookupStock").parent(parent).build());
        if (parent != null) {
            tool.close();
        }
        return tool;
    }

    private static GenAiObservationContext chatContext(GenAiObservation parent) {
        return GenAiObservationContext.builder().operationName(GenAiOperationName.CHAT).system("ollama")
                .requestModel("granite4:3b").componentScheme("openai").parent(parent).build();
    }

    private static GenAiObservation start(Exchange exchange, GenAiObservationContext context) {
        return GenAiObservability.start(exchange, context);
    }

    private GenAiDevConsole console() {
        GenAiDevConsole console = new GenAiDevConsole();
        console.setCamelContext(context);
        return console;
    }

    private static List<JsonObject> list(JsonObject json, String key) {
        return json.getCollection(key);
    }

    private JsonObject call(Map<String, Object> options) {
        return (JsonObject) console().call(DevConsole.MediaType.JSON, options);
    }
}
