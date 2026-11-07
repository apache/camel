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
package org.apache.camel.processor;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Ordered;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.SendHook;
import org.apache.camel.spi.StepHook;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two step hooks and two send hooks active together (CAMEL-26607): a hook that fails the first attempt, as chaos does,
 * with the lowest order, and a hook that records, with order 0. Both run, and the recording hook sees each attempt of
 * the error handler, with the failure of the other hook.
 */
class MultipleHooksTest extends ContextTestSupport {

    private final List<String> recorded = new CopyOnWriteArrayList<>();
    private final AtomicInteger stepFailures = new AtomicInteger();
    private final AtomicInteger sendFailures = new AtomicInteger();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // the failing hooks are added first, but their order puts them last
        context.getCamelContextExtension().addStepHook(new FailingStepHook());
        context.getCamelContextExtension().addStepHook(new RecordingStepHook());
        context.getCamelContextExtension().addSendHook(new FailingSendHook());
        context.getCamelContextExtension().addSendHook(new RecordingSendHook());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0));

                from("direct:start").routeId("start")
                        .process(e -> e.getMessage().setHeader("worked", true)).id("work")
                        .to("mock:result").id("toResult");
            }
        };
    }

    @Test
    void testBothHooksRun() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello");
        getMockEndpoint("mock:result").expectedHeaderReceived("worked", true);

        template.sendBody("direct:start", "Hello");

        assertMockEndpointsSatisfied();
        // the failing hooks failed the first attempt of the step and of the send, and the error handler redelivered
        assertEquals(1, stepFailures.get());
        assertEquals(1, sendFailures.get());
        // the recording hooks were asked first, for each attempt, and saw the failure of the send after it
        assertEquals(List.of(
                "step:work", "step:work",
                "step:toResult",
                "before:mock://result", "after:mock://result:failed",
                "step:toResult",
                "before:mock://result", "after:mock://result:ok"),
                recorded);
    }

    private final class RecordingStepHook implements StepHook {

        @Override
        public long beforeStep(Exchange exchange, String nodeId) {
            recorded.add("step:" + nodeId);
            return PROCEED;
        }
    }

    private final class FailingStepHook implements StepHook, Ordered {

        @Override
        public long beforeStep(Exchange exchange, String nodeId) {
            if ("work".equals(nodeId) && stepFailures.get() == 0) {
                stepFailures.incrementAndGet();
                exchange.setException(new IllegalStateException("Chaos at the step"));
            }
            return PROCEED;
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST;
        }
    }

    private final class RecordingSendHook implements SendHook {

        @Override
        public long beforeSend(Exchange exchange, Endpoint endpoint) {
            if (endpoint.getEndpointUri().startsWith("mock:")) {
                recorded.add("before:" + endpoint.getEndpointUri());
            }
            return PROCEED;
        }

        @Override
        public void afterSend(Exchange exchange, Endpoint endpoint) {
            if (endpoint.getEndpointUri().startsWith("mock:")) {
                recorded.add("after:" + endpoint.getEndpointUri() + (exchange.getException() != null ? ":failed" : ":ok"));
            }
        }
    }

    private final class FailingSendHook implements SendHook, Ordered {

        @Override
        public long beforeSend(Exchange exchange, Endpoint endpoint) {
            if ("mock://result".equals(endpoint.getEndpointUri()) && sendFailures.get() == 0) {
                sendFailures.incrementAndGet();
                exchange.setException(new IllegalStateException("Chaos at the send"));
            }
            return PROCEED;
        }

        @Override
        public void afterSend(Exchange exchange, Endpoint endpoint) {
            // nothing
        }

        @Override
        public int getOrder() {
            return Ordered.LOWEST;
        }
    }
}
