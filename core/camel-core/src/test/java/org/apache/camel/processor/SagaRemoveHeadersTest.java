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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

/**
 * Tests that saga coordination survives removeHeaders("*") in a sub-route. The coordinator ID is stored internally in
 * ExchangeExtension, so it is resilient to header removal.
 */
public class SagaRemoveHeadersTest extends ContextTestSupport {

    @Test
    public void testSagaRequiredSurvivesRemoveHeaders() throws Exception {
        getMockEndpoint("mock:saga-step2").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:saga-step2")
                .expectedMessagesMatches(ex -> ex.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION) != null);

        template.sendBody("direct:saga-start", "Hello Saga");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSagaCompensationAfterRemoveHeaders() throws Exception {
        getMockEndpoint("mock:compensate").expectedMinimumMessageCount(1);
        getMockEndpoint("mock:compensate")
                .expectedMessagesMatches(ex -> ex.getIn().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION) != null);

        try {
            template.sendBody("direct:saga-fail", "Hello Saga Fail");
        } catch (Exception e) {
            // expected
        }

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertMockEndpointsSatisfied());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                InMemorySagaService sagaService = new InMemorySagaService();
                context.addService(sagaService);

                from("direct:saga-start")
                        .saga()
                        .removeHeaders("*")
                        .to("direct:saga-step2");

                from("direct:saga-step2")
                        .saga()
                        .to("mock:saga-step2");

                from("direct:saga-fail")
                        .saga()
                        .compensation("direct:compensate")
                        .removeHeaders("*")
                        .throwException(new RuntimeException("Saga failure"));

                from("direct:compensate")
                        .to("mock:compensate");
            }
        };
    }
}
