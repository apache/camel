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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.InMemorySagaService;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A step that joins a saga while the saga times out must either be compensated with the saga, or fail to begin. It must
 * not run without ever being compensated.
 */
class SagaJoinDuringTimeoutTest extends ContextTestSupport {

    @Test
    void testStepJoiningWhileSagaTimesOut() throws Exception {
        MockEndpoint compensate = getMockEndpoint("mock:compensate");
        compensate.expectedMessageCount(1);

        MockEndpoint payment = getMockEndpoint("mock:payment");
        payment.expectedMessageCount(0);

        MockEndpoint compensatePayment = getMockEndpoint("mock:compensate-payment");
        compensatePayment.expectedMessageCount(0);

        CamelExecutionException ex
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:order", "Hello"));

        MockEndpoint.assertIsSatisfied(context);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().startsWith("Cannot begin: status is COMPENSAT"), ex.getCause().getMessage());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addService(new InMemorySagaService());

                from("direct:order")
                        .saga().timeout(100, TimeUnit.MILLISECONDS).compensation("mock:compensate")
                        .to("direct:payment");

                from("direct:payment")
                        .saga().propagation(SagaPropagation.MANDATORY)
                        .option("orderId", new ExpressionAdapter() {
                            @Override
                            public Object evaluate(Exchange exchange) {
                                // the options are evaluated when the step joins the saga, and this takes until the
                                // saga has timed out and its compensation is running
                                await().atMost(5, TimeUnit.SECONDS)
                                        .until(() -> getMockEndpoint("mock:compensate").getReceivedCounter() > 0);
                                return "order-1";
                            }
                        })
                        .compensation("mock:compensate-payment")
                        .to("mock:payment");
            }
        };
    }
}
