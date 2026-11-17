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
package org.apache.camel.component.langchain4j.agent;

import java.util.List;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ai.observability.GenAiAttributes;
import org.apache.camel.component.ai.observability.GenAiObservabilityProperties;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The structure of an agent run at observability levels detailed and full: one span for the run, one generation per
 * model call and one span per Camel route tool.
 */
class LangChain4jAgentObservabilityLevelTest extends CamelTestSupport {

    private RecordingGenAiTracer tracer;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        tracer = new RecordingGenAiTracer();
        CamelContextAware.trySetCamelContext(tracer, context);
        tracer.init(context);
        return context;
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("agentConfig", new AgentConfiguration().withChatModel(new ToolCallingChatModel()));
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:agent")
                        .to("langchain4j-agent:order-agent?agentConfiguration=#agentConfig&tags=orders");
                from("ai-tool:getOrderStatus?tags=orders&description=Get the status of an order"
                     + "&parameter.orderId=string&parameter.orderId.required=true")
                        .setBody(simple("{\"orderId\":\"${header.orderId}\",\"status\":\"SHIPPED\"}"));
            }
        };
    }

    private void level(String level) {
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, level);
    }

    @Test
    void detailedShouldRecordTheRunWithOneGenerationPerModelCallAndTheTool() {
        level("detailed");

        String answer = template.requestBody("direct:agent", "Where is order A-1?", String.class);

        assertThat(answer).isEqualTo("Order A-1 is SHIPPED");
        RecordingGenAiTracer.RecordingSpan run = tracer.span("invoke_agent order-agent");
        List<RecordingGenAiTracer.RecordingSpan> calls = tracer.spans("chat gpt-4o");
        RecordingGenAiTracer.RecordingSpan tool = tracer.span("execute_tool getOrderStatus");

        assertThat(run.tags())
                .containsEntry(GenAiAttributes.OPERATION_NAME, "invoke_agent")
                .containsEntry(GenAiAttributes.AGENT_NAME, "order-agent")
                .doesNotContainKeys(GenAiAttributes.INPUT_TOKENS, GenAiAttributes.INPUT_MESSAGES);
        assertThat(calls).hasSize(2).allSatisfy(call -> assertThat(call.parent()).isSameAs(run));
        assertThat(calls.get(0).tags())
                .containsEntry(GenAiAttributes.INPUT_TOKENS, 10L)
                .containsEntry(GenAiAttributes.FINISH_REASONS, "tool_call");
        assertThat(calls.get(1).tags())
                .containsEntry(GenAiAttributes.INPUT_TOKENS, 20L)
                .containsEntry(GenAiAttributes.FINISH_REASONS, "stop");
        assertThat(tool.parent()).isSameAs(run);
        assertThat(tool.tags())
                .containsEntry(GenAiAttributes.TOOL_NAME, "getOrderStatus")
                .containsEntry(GenAiAttributes.TOOL_CALL_ID, "call-1")
                .doesNotContainKey(GenAiAttributes.TOOL_CALL_ARGUMENTS);
        // model call 1 ends before the tool runs
        assertThat(tracer.order("chat gpt-4o", "execute_tool getOrderStatus")).isTrue();
    }

    @Test
    void fullShouldAddMessagesAndToolArgumentsAndResult() {
        level("full");

        template.requestBody("direct:agent", "Where is order A-1?", String.class);

        assertThat(tracer.span("invoke_agent order-agent").tags())
                .containsEntry(GenAiAttributes.INPUT_MESSAGES,
                        "[{\"role\":\"user\",\"parts\":[{\"type\":\"text\",\"content\":\"Where is order A-1?\"}]}]")
                .containsEntry(GenAiAttributes.OUTPUT_MESSAGES,
                        "[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"Order A-1 is SHIPPED\"}]}]");
        List<RecordingGenAiTracer.RecordingSpan> calls = tracer.spans("chat gpt-4o");
        assertThat((String) calls.get(0).tags().get(GenAiAttributes.OUTPUT_MESSAGES))
                .contains("\"type\":\"tool_call\"", "\"name\":\"getOrderStatus\"", "\"arguments\":{\"orderId\":\"A-1\"}");
        assertThat((String) calls.get(1).tags().get(GenAiAttributes.INPUT_MESSAGES))
                .contains("\"type\":\"tool_call_response\"", "SHIPPED");
        assertThat(tracer.span("execute_tool getOrderStatus").tags())
                .containsEntry(GenAiAttributes.TOOL_CALL_ARGUMENTS, "{\"orderId\":\"A-1\"}")
                .containsEntry(GenAiAttributes.TOOL_CALL_RESULT, "{\"orderId\":\"A-1\",\"status\":\"SHIPPED\"}");
    }

    @Test
    void basicShouldKeepOneGenerationForTheWholeRun() {
        template.requestBody("direct:agent", "Where is order A-1?", String.class);

        assertThat(tracer.genAiSpans()).hasSize(1);
        assertThat(tracer.genAiSpans().get(0).tags())
                .containsEntry(GenAiAttributes.OPERATION_NAME, "generate_content")
                .containsEntry(GenAiAttributes.INPUT_TOKENS, 30L);
    }

    /** Asks for the getOrderStatus tool, then answers with its result. */
    private static final class ToolCallingChatModel implements ChatModel {
        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.builder().modelName("gpt-4o").build();
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            ChatMessage last = request.messages().get(request.messages().size() - 1);
            if (last instanceof ToolExecutionResultMessage result) {
                return ChatResponse.builder()
                        .aiMessage(AiMessage.from("Order A-1 is " + (result.text().contains("SHIPPED") ? "SHIPPED" : "?")))
                        .modelName("gpt-4o")
                        .tokenUsage(new TokenUsage(20, 6, 26))
                        .finishReason(FinishReason.STOP)
                        .build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                            .id("call-1").name("getOrderStatus").arguments("{\"orderId\":\"A-1\"}").build()))
                    .modelName("gpt-4o")
                    .tokenUsage(new TokenUsage(10, 4, 14))
                    .finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
    }
}
