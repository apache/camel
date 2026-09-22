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
package org.apache.camel.component.openai;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.infra.openai.mock.OpenAIMock;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The token budget, the trace and the lifecycle events of the tool loop of the responses operation, which reports them
 * as the chat-completion loop does.
 */
class OpenAIResponsesAgenticTest extends CamelTestSupport {

    private final List<CamelEvent> events = new CopyOnWriteArrayList<>();

    @RegisterExtension
    public OpenAIMock openAIMock = new OpenAIMock().builder()
            .when("one tool")
            .withUsage(10, 5)
            .invokeTool("get_weather")
            .withParam("city", "London")
            .replyWith("The weather in London is sunny.")
            .end()
            .when("expensive tool call")
            .withUsage(70, 50)
            .invokeTool("get_weather")
            .withParam("city", "Paris")
            .replyWith("Should not reach this response")
            .end()
            .when("expensive direct answer")
            .withUsage(70, 50)
            .replyWith("Direct answer over budget")
            .end()
            .when("accumulate over budget")
            .withUsage(40, 10)
            .invokeTool("get_weather")
            .withParam("city", "A")
            .andThenInvokeTool("get_weather")
            .withParam("city", "B")
            .replyWith("Should not reach this response")
            .end()
            .when("look up an order")
            .invokeTool("lookup_order")
            .withParam("id", "42")
            .replyWith("Should not reach this response, the tool returns directly")
            .end()
            .build();

    @Override
    protected RouteBuilder createRouteBuilder() {
        String base = openAIMock.getBaseUrl() + "/v1";
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("ai-tool:get_weather?tags=responses-agentic&description=Get the weather for a city"
                     + "&parameter.city=string&parameter.city.required=true")
                        .setBody(simple("Sunny in ${header.city}"));

                from("ai-tool:lookup_order?tags=responses-agentic-direct&description=Look up an order"
                     + "&parameter.id=string&parameter.id.required=true&returnDirect=true")
                        .setBody(simple("Order ${header.id} shipped"));

                from("direct:responses-agentic")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&tags=responses-agentic&baseUrl=" + base);

                from("direct:responses-agentic-budget")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&tags=responses-agentic"
                            + "&maxAgenticTokens=100&maxToolIterations=5&baseUrl=" + base);

                from("direct:responses-agentic-budget-multi")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&tags=responses-agentic"
                            + "&maxAgenticTokens=80&maxToolIterations=5&baseUrl=" + base);

                from("direct:responses-agentic-direct")
                        .to("openai:responses?model=gpt-5&apiKey=dummy&tags=responses-agentic-direct&baseUrl=" + base);
            }
        };
    }

    @BeforeEach
    void registerEventNotifier() {
        events.clear();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event.getType() == CamelEvent.Type.Custom) {
                    events.add(event);
                }
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event.getType() == CamelEvent.Type.Custom;
            }
        });
    }

    @Test
    void theTokenHeadersCoverEveryCallOfTheLoop() {
        Exchange result = template.request("direct:responses-agentic", e -> e.getIn().setBody("one tool"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("The weather in London is sunny.");
        // the tool call and the answer that followed it, 10 prompt and 5 completion tokens each
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_PROMPT_TOKENS, Long.class)).isEqualTo(20L);
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_COMPLETION_TOKENS, Long.class)).isEqualTo(10L);
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, Long.class)).isEqualTo(30L);
        // the headers of the last response stay untouched
        assertThat(result.getMessage().getHeader(OpenAIConstants.PROMPT_TOKENS, Long.class)).isEqualTo(10L);
    }

    @Test
    void maxAgenticTokensRefusesTheNextCall() {
        Exchange result = template.request("direct:responses-agentic-budget",
                e -> e.getIn().setBody("expensive tool call"));

        assertThat(result.getException()).isInstanceOf(IllegalStateException.class);
        assertThat(result.getException().getMessage())
                .contains("Max agentic tokens (100) exceeded at iteration 0")
                .contains("prompt=70")
                .contains("completion=50")
                .contains("total=120");
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, Long.class)).isEqualTo(120L);
    }

    @Test
    void maxAgenticTokensCountsAcrossIterations() {
        Exchange result = template.request("direct:responses-agentic-budget-multi",
                e -> e.getIn().setBody("accumulate over budget"));

        assertThat(result.getException()).isInstanceOf(IllegalStateException.class);
        assertThat(result.getException().getMessage())
                .contains("Max agentic tokens (80) exceeded at iteration 1")
                .contains("total=100");
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, Long.class)).isEqualTo(100L);
    }

    @Test
    void anAnswerOverTheBudgetIsStillReturned() {
        Exchange result = template.request("direct:responses-agentic-budget",
                e -> e.getIn().setBody("expensive direct answer"));

        assertThat(result.getException()).isNull();
        assertThat(result.getMessage().getBody(String.class)).isEqualTo("Direct answer over budget");
        assertThat(result.getMessage().getHeader(OpenAIConstants.AGENTIC_TOTAL_TOKENS, Long.class)).isEqualTo(120L);
    }

    @Test
    void theTraceHoldsOneEntryPerCallOfTheLoop() {
        Exchange result = template.request("direct:responses-agentic", e -> e.getIn().setBody("one tool"));

        assertThat(result.getException()).isNull();
        @SuppressWarnings("unchecked")
        List<AgenticIterationTrace> trace = result.getProperty(OpenAIConstants.AGENTIC_TRACE, List.class);

        assertThat(trace).hasSize(2);
        assertThat(trace.get(0).iteration()).isEqualTo(1);
        assertThat(trace.get(0).promptTokens()).isEqualTo(10);
        assertThat(trace.get(0).completionTokens()).isEqualTo(5);
        assertThat(trace.get(0).toolCalls()).singleElement().satisfies(toolCall -> {
            assertThat(toolCall.toolName()).isEqualTo("get_weather");
            assertThat(toolCall.argumentsSummary()).contains("London");
            assertThat(toolCall.resultSummary()).contains("Sunny in London");
            assertThat(toolCall.success()).isTrue();
        });
        assertThat(trace.get(1).iteration()).isEqualTo(2);
        assertThat(trace.get(1).toolCalls()).isEmpty();
    }

    @Test
    void theLifecycleEventsDescribeTheLoop() {
        Exchange result = template.request("direct:responses-agentic", e -> e.getIn().setBody("one tool"));

        assertThat(result.getException()).isNull();
        assertThat(eventsOfType(OpenAIAgenticLoopStartedEvent.class)).singleElement()
                .satisfies(started -> {
                    assertThat(started.getToolCount()).isEqualTo(1);
                    assertThat(started.getMaxIterations()).isPositive();
                });
        assertThat(eventsOfType(OpenAIAgenticToolCallExecutedEvent.class)).singleElement()
                .satisfies(toolCall -> {
                    assertThat(toolCall.getToolName()).isEqualTo("get_weather");
                    assertThat(toolCall.getIteration()).isEqualTo(1);
                    assertThat(toolCall.isSuccess()).isTrue();
                });
        assertThat(eventsOfType(OpenAIAgenticLoopCompletedEvent.class)).singleElement()
                .satisfies(completed -> {
                    assertThat(completed.getIterationCount()).isEqualTo(1);
                    assertThat(completed.getTotalTokens()).isEqualTo(30L);
                    assertThat(completed.getStopReason()).isEqualTo("stop");
                });
    }

    @Test
    void theStopReasonNamesTheBudgetAndTheToolThatReturnedDirectly() {
        template.request("direct:responses-agentic-budget", e -> e.getIn().setBody("expensive tool call"));

        assertThat(eventsOfType(OpenAIAgenticLoopCompletedEvent.class)).singleElement()
                .satisfies(completed -> {
                    assertThat(completed.getStopReason()).isEqualTo("token_budget_exceeded");
                    assertThat(completed.getTotalTokens()).isEqualTo(120L);
                });

        events.clear();
        Exchange direct = template.request("direct:responses-agentic-direct",
                e -> e.getIn().setBody("look up an order"));

        assertThat(direct.getException()).isNull();
        assertThat(direct.getMessage().getBody(String.class)).isEqualTo("Order 42 shipped");
        assertThat(direct.getMessage().getHeader(OpenAIConstants.MCP_RETURN_DIRECT, Boolean.class)).isTrue();
        assertThat(eventsOfType(OpenAIAgenticLoopCompletedEvent.class)).singleElement()
                .satisfies(completed -> assertThat(completed.getStopReason()).isEqualTo("return_direct"));
    }

    private <T extends CamelEvent> List<T> eventsOfType(Class<T> type) {
        return events.stream().filter(type::isInstance).map(type::cast).toList();
    }
}
