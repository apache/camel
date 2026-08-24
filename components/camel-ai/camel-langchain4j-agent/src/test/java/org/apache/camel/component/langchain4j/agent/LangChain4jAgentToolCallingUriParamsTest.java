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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.apache.camel.component.langchain4j.agent.api.AgentFactory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for CAMEL-23929: tool-calling options exposed as {@code @UriParam} on the langchain4j-agent endpoint.
 */
class LangChain4jAgentToolCallingUriParamsTest extends CamelTestSupport {

    private static final String TAG = "uri-tool-calling";

    private final TwoToolRoundTripChatModel twoToolChatModel = new TwoToolRoundTripChatModel();
    private final SingleToolRoundTripChatModel singleToolChatModel = new SingleToolRoundTripChatModel();
    private final AlwaysToolChatModel alwaysToolChatModel = new AlwaysToolChatModel();
    private final FailingToolRoundTripChatModel failingToolChatModel = new FailingToolRoundTripChatModel();
    private final AtomicInteger chatRound = new AtomicInteger();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxConcurrent = new AtomicInteger();
    private CyclicBarrier overlapBarrier = new CyclicBarrier(2);

    @BeforeEach
    void resetState() {
        chatRound.set(0);
        maxConcurrent.set(0);
        inFlight.set(0);
        overlapBarrier = new CyclicBarrier(2);
        failingToolChatModel.reset();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:concurrent-uri")
                        .to("langchain4j-agent:test?agentConfiguration=#concurrentConfig&tags=" + TAG
                            + "&executeToolsConcurrently=true");

                from("direct:disable-concurrent-uri")
                        .to("langchain4j-agent:test?agentConfiguration=#singleToolConfig&tags=" + TAG
                            + "&executeToolsConcurrently=false");

                from("direct:limited-uri")
                        .to("langchain4j-agent:test?agentConfiguration=#singleToolConfig&tags=" + TAG
                            + "&maxToolCallingRoundTrips=2");

                from("direct:override-uri")
                        .to("langchain4j-agent:test?agentConfiguration=#highLimitConfig&tags=" + TAG
                            + "&maxToolCallingRoundTrips=1");

                from("direct:compensate-uri")
                        .to("langchain4j-agent:test?agentConfiguration=#failingToolConfig&tags=" + TAG
                            + "&compensateOnToolErrors=true&maxToolCallingRoundTrips=2");

                from("ai-tool:countTool?tags=" + TAG + "&description=Counting tool")
                        .setBody(constant("ok"));

                from("ai-tool:slowToolA?tags=" + TAG + "&description=Slow tool A")
                        .process(exchange -> recordConcurrentEntry("A", exchange));

                from("ai-tool:slowToolB?tags=" + TAG + "&description=Slow tool B")
                        .process(exchange -> recordConcurrentEntry("B", exchange));

                from("ai-tool:failingTool?tags=" + TAG + "&description=A tool that always fails")
                        .throwException(new RuntimeException("Simulated tool failure"));
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("agentConfig", new AgentConfiguration()
                .withChatModel(twoToolChatModel)
                .withExecuteToolsConcurrently()
                .withMaxToolCallingRoundTrips(5));
        registry.bind("concurrentConfig", new AgentConfiguration()
                .withChatModel(twoToolChatModel)
                .withMaxToolCallingRoundTrips(5));
        registry.bind("singleToolConfig", new AgentConfiguration()
                .withChatModel(singleToolChatModel)
                .withExecuteToolsConcurrently()
                .withMaxToolCallingRoundTrips(5));
        registry.bind("highLimitConfig", new AgentConfiguration()
                .withChatModel(alwaysToolChatModel)
                .withMaxToolCallingRoundTrips(10));
        registry.bind("failingToolConfig", new AgentConfiguration()
                .withChatModel(failingToolChatModel)
                .withMaxToolCallingRoundTrips(5));
    }

    @Test
    void executeToolsConcurrentlyCanBeEnabledViaEndpointUri() {
        String response = template.requestBody("direct:concurrent-uri", new AiAgentBody<>("run tools"), String.class);

        assertThat(response).isEqualTo("done");
        assertThat(maxConcurrent.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void executeToolsConcurrentlyFalseViaEndpointUriOverridesBean() {
        String response = template.requestBody("direct:disable-concurrent-uri", new AiAgentBody<>("run tools"), String.class);

        assertThat(response).isEqualTo("done");
    }

    @Test
    void maxToolCallingRoundTripsViaEndpointUriIsApplied() {
        String response = template.requestBody("direct:limited-uri", new AiAgentBody<>("run tools"), String.class);

        assertThat(response).isEqualTo("done");
    }

    @Test
    void endpointUriOverridesAgentConfigurationToolCallingLimit() {
        assertThatThrownBy(() -> template.requestBody("direct:override-uri", new AiAgentBody<>("run tools"), String.class))
                .rootCause()
                .hasMessageContaining("exceeded 1 tool calling round trips");
    }

    @Test
    void negativeMaxToolCallingRoundTripsIsRejected() {
        assertThatThrownBy(() -> new LangChain4jAgentConfiguration().setMaxToolCallingRoundTrips(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxToolCallingRoundTrips");
    }

    @Test
    void toolCallingUriParamsRequireAgentConfiguration() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            ctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:x").to("langchain4j-agent:test?maxToolCallingRoundTrips=3");
                }
            });

            assertThatThrownBy(ctx::start)
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("agentConfiguration");
        }
    }

    @Test
    void compensateOnToolErrorsCanBeEnabledViaEndpointUri() {
        String response = template.requestBody("direct:compensate-uri", new AiAgentBody<>("run tools"), String.class);

        assertThat(response).isEqualTo("recovered");
        assertThat(failingToolChatModel.getRoundCount()).isEqualTo(2);
    }

    @Test
    void toolCallingUriParamsCannotBeCombinedWithAgentFactory() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            ctx.getRegistry().bind("agentConfig", new AgentConfiguration().withChatModel(twoToolChatModel));
            ctx.getRegistry().bind("myFactory", new StubAgentFactory());
            ctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:x").to(
                            "langchain4j-agent:test?agentConfiguration=#agentConfig&agentFactory=#myFactory&maxToolCallingRoundTrips=3");
                }
            });

            assertThatThrownBy(ctx::start)
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("agentFactory");
        }
    }

    @Test
    void toolCallingUriParamsCannotBeCombinedWithAgentBean() throws Exception {
        try (DefaultCamelContext ctx = new DefaultCamelContext()) {
            ctx.getRegistry()
                    .bind("myAgent",
                            (org.apache.camel.component.langchain4j.agent.api.Agent) (
                                    body,
                                    toolProvider) -> dev.langchain4j.service.Result.<String> builder().content("ok").build());
            ctx.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:x").to("langchain4j-agent:test?agent=#myAgent&maxToolCallingRoundTrips=3");
                }
            });

            assertThatThrownBy(ctx::start)
                    .isInstanceOf(Exception.class)
                    .hasMessageContaining("agentConfiguration");
        }
    }

    private void recordConcurrentEntry(String label, Exchange exchange) {
        int concurrent = inFlight.incrementAndGet();
        maxConcurrent.updateAndGet(current -> Math.max(current, concurrent));
        try {
            overlapBarrier.await(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        inFlight.decrementAndGet();
        exchange.getMessage().setBody(label);
    }

    private final class SingleToolRoundTripChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            if (chatRound.getAndIncrement() == 0) {
                ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                        .id("t1")
                        .name("countTool")
                        .arguments("{}")
                        .build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build())
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }
    }

    private final class TwoToolRoundTripChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            if (chatRound.getAndIncrement() == 0) {
                List<ToolExecutionRequest> requests = List.of(
                        ToolExecutionRequest.builder().id("a").name("slowToolA").arguments("{}").build(),
                        ToolExecutionRequest.builder().id("b").name("slowToolB").arguments("{}").build());
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(requests).build())
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("done")).build();
        }
    }

    private final class AlwaysToolChatModel implements ChatModel {
        @Override
        public ChatResponse doChat(ChatRequest request) {
            ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                    .id("t1")
                    .name("countTool")
                    .arguments("{}")
                    .build();
            return ChatResponse.builder()
                    .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build())
                    .build();
        }
    }

    private final class FailingToolRoundTripChatModel implements ChatModel {
        private final AtomicInteger round = new AtomicInteger();

        @Override
        public ChatResponse doChat(ChatRequest request) {
            if (round.getAndIncrement() == 0) {
                ToolExecutionRequest toolRequest = ToolExecutionRequest.builder()
                        .id("fail1")
                        .name("failingTool")
                        .arguments("{}")
                        .build();
                return ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolRequest)).build())
                        .build();
            }
            return ChatResponse.builder().aiMessage(AiMessage.from("recovered")).build();
        }

        int getRoundCount() {
            return round.get();
        }

        void reset() {
            round.set(0);
        }
    }

    private static final class StubAgentFactory implements AgentFactory {
        private CamelContext camelContext;

        @Override
        public Agent createAgent(Exchange exchange) {
            return (body, toolProvider) -> dev.langchain4j.service.Result.<String> builder().content("ok").build();
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }
    }
}
