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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.langchain4j.agent.api.Agent;
import org.apache.camel.component.langchain4j.agent.api.AgentFactory;
import org.apache.camel.component.langchain4j.agent.api.AiAgentBody;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for CAMEL-23945: verifies that concurrent exchanges using an {@link AgentFactory} each receive the
 * response from their own agent, not from an agent created for a different exchange.
 *
 * <p>
 * The original bug was a race condition in {@code LangChain4jAgentProducer.process()}: the factory-created agent was
 * written to a shared instance field and then re-read, so concurrent exchanges could see each other's agents. The fix
 * uses a method-local variable instead.
 * </p>
 */
class LangChain4jAgentProducerConcurrencyTest extends CamelTestSupport {

    private static final String AGENT_SELECTOR_HEADER = "agentSelector";

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("concurrentFactory", new PerExchangeAgentFactory());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("langchain4j-agent:test?agentFactory=#concurrentFactory");
            }
        };
    }

    /**
     * Sends many concurrent exchanges through a single producer endpoint, each selecting a different agent via the
     * factory. If the producer incorrectly stores the factory-created agent in a shared field, some exchanges will
     * receive responses from the wrong agent.
     */
    @Test
    void concurrentExchangesShouldEachReceiveTheirOwnAgentResponse() throws Exception {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                futures.add(executor.submit(() -> {
                    // All threads wait here until the gate opens, maximising contention
                    startGate.await(10, TimeUnit.SECONDS);

                    AiAgentBody<?> body = new AiAgentBody<>("question-" + index, null, null);
                    return template.requestBodyAndHeader(
                            "direct:start", body, AGENT_SELECTOR_HEADER, index, String.class);
                }));
            }

            // Release all threads simultaneously
            startGate.countDown();

            // Collect and verify results
            for (int i = 0; i < threadCount; i++) {
                String result = futures.get(i).get(30, TimeUnit.SECONDS);
                assertThat(result)
                        .as("Exchange %d should have received its own agent's response", i)
                        .isEqualTo("response-from-agent-" + i);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * An {@link AgentFactory} that creates a distinct {@link Agent} per exchange. Each agent echoes the exchange's
     * {@code agentSelector} header back in its response, allowing the test to verify that each exchange was processed
     * by the correct agent.
     */
    static class PerExchangeAgentFactory implements AgentFactory {
        private CamelContext camelContext;

        @Override
        public Agent createAgent(Exchange exchange) {
            int selector = exchange.getIn().getHeader(AGENT_SELECTOR_HEADER, Integer.class);
            // Return an agent that produces a response uniquely tied to this selector
            return (AiAgentBody<?> body, dev.langchain4j.service.tool.ToolProvider toolProvider) -> "response-from-agent-"
                                                                                                    + selector;
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
