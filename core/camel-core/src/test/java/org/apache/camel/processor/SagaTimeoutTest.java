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

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaCompletionMode;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.InMemorySagaService;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SagaTimeoutTest extends ContextTestSupport {

    private InMemorySagaService sagaService;

    @Test
    public void testTimeoutCalledCorrectly() throws Exception {
        MockEndpoint compensate = getMockEndpoint("mock:compensate");
        compensate.expectedMessageCount(1);
        compensate.expectedHeaderReceived("id", "myid");

        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        template.sendBody("direct:saga", "Hello");

        end.assertIsSatisfied();
        compensate.assertIsSatisfied();
    }

    @Test
    public void testTimeoutHasNoEffectIfCompleted() throws Exception {
        MockEndpoint compensate = getMockEndpoint("mock:compensate");
        compensate.expectedMessageCount(1);
        compensate.setResultWaitTime(2000);

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedMessageCount(1);
        complete.expectedHeaderReceived("id", "myid");

        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        template.sendBody("direct:saga-auto", "Hello");

        end.assertIsSatisfied();
        complete.assertIsSatisfied();
        compensate.assertIsNotSatisfied();
    }

    @Test
    public void testTimeoutMultiParticipants() throws Exception {
        MockEndpoint compensate = getMockEndpoint("mock:compensate");
        compensate.expectedMessageCount(1);

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedMessageCount(0);

        MockEndpoint end = getMockEndpoint("mock:end");
        end.expectedMessageCount(1);

        CamelExecutionException ex = assertThrows(CamelExecutionException.class,
                () -> {
                    template.sendBody("direct:saga-multi-participants", "Hello");
                });

        String msg = ex.getCause().getMessage();
        assertTrue(msg.contains("Cannot begin: status is COMPENSATING")
                || msg.contains("Cannot begin: status is COMPENSATED")
                || msg.contains("Exchange is not part of a saga"));

        end.assertIsSatisfied();
        complete.assertIsSatisfied();
        compensate.assertIsSatisfied();
    }

    @Test
    void testRequiredStepAfterTimeoutDoesNotStartNewSaga() throws Exception {
        assertStepAfterTimeoutFails("direct:saga-timeout-required");
    }

    @Test
    void testSupportsStepAfterTimeoutDoesNotRunOutsideSaga() throws Exception {
        assertStepAfterTimeoutFails("direct:saga-timeout-supports");
    }

    private void assertStepAfterTimeoutFails(String uri) throws Exception {
        MockEndpoint compensate = getMockEndpoint("mock:compensate");
        compensate.expectedMessageCount(1);

        MockEndpoint payment = getMockEndpoint("mock:payment");
        payment.expectedMessageCount(0);

        MockEndpoint complete = getMockEndpoint("mock:complete");
        complete.expectedMessageCount(0);

        CamelExecutionException ex = assertThrows(CamelExecutionException.class, () -> template.sendBody(uri, "Hello"));

        MockEndpoint.assertIsSatisfied(context);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().endsWith("is not active or not known"), ex.getCause().getMessage());
    }

    private void awaitSagaEnded(Exchange exchange) {
        // a slow call outlasts the saga timeout: the saga is compensated and removed from the saga service
        String sagaId = exchange.getExchangeExtension().getSagaLongRunningAction();
        await().atMost(5, TimeUnit.SECONDS).until(() -> sagaService.getSaga(sagaId).get() == null);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                sagaService = new InMemorySagaService();
                context.addService(sagaService);

                from("direct:saga").saga().timeout(100, TimeUnit.MILLISECONDS).option("id", constant("myid"))
                        .completionMode(SagaCompletionMode.MANUAL)
                        .compensation("mock:compensate").to("mock:end");

                from("direct:saga-auto").saga().timeout(2000, TimeUnit.MILLISECONDS).option("id", constant("myid"))
                        .compensation("mock:compensate").completion("mock:complete")
                        .to("mock:end");

                from("direct:saga-multi-participants")
                        .process(exchange -> {
                            exchange.getMessage().setHeader("id", UUID.randomUUID().toString());
                        })
                        .saga()
                        .propagation(SagaPropagation.REQUIRES_NEW)
                        .to("direct:service1")
                        .to("direct:service2");

                from("direct:service1")
                        .saga().option("id", header("id"))
                        .propagation(SagaPropagation.MANDATORY).timeout(100, TimeUnit.MILLISECONDS)
                        .compensation("mock:compensate").completion("mock:complete")
                        .delay(300L)
                        .to("mock:end");

                from("direct:service2")
                        .saga().option("id", header("id"))
                        .propagation(SagaPropagation.MANDATORY).timeout(500, TimeUnit.MILLISECONDS)
                        .compensation("mock:compensate").completion("mock:complete")
                        .to("mock:end");

                from("direct:saga-timeout-required")
                        .saga().timeout(100, TimeUnit.MILLISECONDS).compensation("mock:compensate")
                        .process(SagaTimeoutTest.this::awaitSagaEnded)
                        .to("direct:payment-required");

                from("direct:payment-required")
                        .saga().propagation(SagaPropagation.REQUIRED)
                        .compensation("mock:compensate-payment").completion("mock:complete")
                        .to("mock:payment");

                from("direct:saga-timeout-supports")
                        .saga().timeout(100, TimeUnit.MILLISECONDS).compensation("mock:compensate")
                        .process(SagaTimeoutTest.this::awaitSagaEnded)
                        .to("direct:payment-supports");

                from("direct:payment-supports")
                        .saga().propagation(SagaPropagation.SUPPORTS)
                        .compensation("mock:compensate-payment")
                        .to("mock:payment");
            }
        };
    }

}
