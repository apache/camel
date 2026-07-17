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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SagaCoordinatorCleanupTest extends ContextTestSupport {

    private InMemorySagaService sagaService;

    @Test
    public void testCoordinatorRemovedAfterCompletion() throws Exception {
        getMockEndpoint("mock:complete").expectedMessageCount(1);

        String sagaId = context.createFluentProducerTemplate()
                .to("direct:saga-success")
                .request(String.class);

        MockEndpoint.assertIsSatisfied(context);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(sagaService.getSaga(sagaId).get(),
                        "Coordinator should be removed after completion"));
    }

    @Test
    public void testCoordinatorRemovedAfterCompensation() throws Exception {
        getMockEndpoint("mock:compensate").expectedMessageCount(1);

        try {
            context.createFluentProducerTemplate()
                    .to("direct:saga-fail")
                    .request();
        } catch (Exception e) {
            // expected
        }

        MockEndpoint.assertIsSatisfied(context);

        String sagaId = getMockEndpoint("mock:compensate").getReceivedExchanges().get(0)
                .getMessage().getHeader(Exchange.SAGA_LONG_RUNNING_ACTION, String.class);

        await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull(sagaService.getSaga(sagaId).get(),
                        "Coordinator should be removed after compensation"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                sagaService = new InMemorySagaService();
                context.addService(sagaService);

                from("direct:saga-success")
                        .saga().propagation(SagaPropagation.REQUIRES_NEW)
                        .completion("direct:complete")
                        .transform().header(Exchange.SAGA_LONG_RUNNING_ACTION);

                from("direct:complete")
                        .to("mock:complete");

                from("direct:saga-fail")
                        .saga().propagation(SagaPropagation.REQUIRES_NEW)
                        .compensation("direct:compensate")
                        .process(e -> {
                            throw new RuntimeException("forced failure");
                        });

                from("direct:compensate")
                        .to("mock:compensate");
            }
        };
    }
}
