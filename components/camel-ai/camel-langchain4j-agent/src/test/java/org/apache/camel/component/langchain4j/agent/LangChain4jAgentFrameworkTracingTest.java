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
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.request.DefaultChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.quarkiverse.langchain4j.runtime.listeners.SpanChatModelListener;
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
 * A model that traces its own calls (here with the quarkus-langchain4j listener) keeps them: Camel records the agent
 * run and the Camel route tools only, so each model call is counted once.
 */
class LangChain4jAgentFrameworkTracingTest extends CamelTestSupport {

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
        registry.bind("agentConfig", new AgentConfiguration().withChatModel(new TracedChatModel()));
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
                        .setBody(constant("SHIPPED"));
            }
        };
    }

    private void level(String level) {
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.LEVEL, level);
    }

    @Test
    void basicShouldLeaveTheModelCallsToTheFramework() {
        assertThat(template.requestBody("direct:agent", "Where is order A-1?", String.class)).isEqualTo("SHIPPED");

        assertThat(tracer.genAiSpans()).isEmpty();
    }

    @Test
    void detailedShouldRecordTheRunAndTheToolButNotTheModelCalls() {
        level("detailed");

        template.requestBody("direct:agent", "Where is order A-1?", String.class);

        RecordingGenAiTracer.RecordingSpan run = tracer.span("invoke_agent order-agent");
        assertThat(tracer.spans("chat gpt-4o")).isEmpty();
        assertThat(run.tags()).containsEntry(GenAiAttributes.OPERATION_NAME, "invoke_agent")
                .doesNotContainKey(GenAiAttributes.INPUT_TOKENS);
        assertThat(tracer.span("execute_tool getOrderStatus").parent()).isSameAs(run);
    }

    @Test
    void shouldRecordTheModelCallsWhenNotDeferringToTheFramework() {
        level("detailed");
        context.getPropertiesComponent().addOverrideProperty(GenAiObservabilityProperties.DEFER_TO_FRAMEWORK, "false");

        template.requestBody("direct:agent", "Where is order A-1?", String.class);

        assertThat(tracer.spans("chat gpt-4o")).hasSize(2);
    }

    /** Asks for the tool, then answers with its result; carries a tracing listener like a quarkus-langchain4j model. */
    private static final class TracedChatModel implements ChatModel {
        @Override
        public ModelProvider provider() {
            return ModelProvider.OPEN_AI;
        }

        @Override
        public List<ChatModelListener> listeners() {
            return List.of(new SpanChatModelListener());
        }

        @Override
        public ChatRequestParameters defaultRequestParameters() {
            return DefaultChatRequestParameters.builder().modelName("gpt-4o").build();
        }

        @Override
        public ChatResponse doChat(ChatRequest request) {
            ChatMessage last = request.messages().get(request.messages().size() - 1);
            if (last instanceof ToolExecutionResultMessage result) {
                return ChatResponse.builder().aiMessage(AiMessage.from(result.text())).modelName("gpt-4o")
                        .tokenUsage(new TokenUsage(20, 6, 26)).finishReason(FinishReason.STOP).build();
            }
            return ChatResponse.builder()
                    .aiMessage(AiMessage.from(ToolExecutionRequest.builder()
                            .id("call-1").name("getOrderStatus").arguments("{\"orderId\":\"A-1\"}").build()))
                    .modelName("gpt-4o").tokenUsage(new TokenUsage(10, 4, 14)).finishReason(FinishReason.TOOL_EXECUTION)
                    .build();
        }
    }
}
