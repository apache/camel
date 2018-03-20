/**
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.saga.InMemorySagaService;
import org.apache.camel.model.SagaPropagation;
import org.apache.camel.saga.CamelSagaService;
import org.junit.Assert;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;

public class SagaTest extends ContextTestSupport {

    private OrderManagerService orderManagerService;

    private CreditService creditService;

    public void testCreditExhausted() throws Exception {
        // total credit is 100
        buy(20, false, false);
        buy(70, false, false);
        buy(20, false, true); // fail
        buy(5, false, false);

        await().until(() -> orderManagerService.getOrders().size(), equalTo(3));
        await().until(() -> creditService.getCredit(), equalTo(5));
    }

    public void testTotalCompensation() throws Exception {
        // total credit is 100
        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                buy(10, false, false);
            } else {
                buy(10, true, true);
            }
        }

        await().until(() -> orderManagerService.getOrders().size(), equalTo(5));
        await().until(() -> creditService.getCredit(), equalTo(50));
    }

    private void buy(int amount, boolean failAtTheEnd, boolean shouldFail) {
        try {
            context.createFluentProducerTemplate()
                    .to("direct:saga")
                    .withHeader("amount", amount)
                    .withHeader("fail", failAtTheEnd)
                    .request();

            if (shouldFail) {
                Assert.fail("Exception not thrown");
            }
        } catch (Exception ex) {
            if (!shouldFail) {
                Assert.fail("Unexpected exception");
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                orderManagerService = new OrderManagerService();

                creditService = new CreditService(100);

                CamelSagaService sagaService = new InMemorySagaService();
                context.addService(sagaService);

                from("direct:saga")
                        .saga().propagation(SagaPropagation.REQUIRES_NEW)
                        .log("Creating a new order")
                        .to("direct:newOrder")
                        .log("Taking the credit")
                        .to("direct:reserveCredit")
                        .log("Finalizing")
                        .to("direct:finalize")
                        .log("Done!");


                // Order service

                from("direct:newOrder")
                        .saga()
                        .propagation(SagaPropagation.MANDATORY)
                        .compensation("direct:cancelOrder")
                        .transform().header(Exchange.SAGA_LONG_RUNNING_ACTION)
                        .bean(orderManagerService, "newOrder")
                        .log("Order ${body} created");

                from("direct:cancelOrder")
                        .transform().header(Exchange.SAGA_LONG_RUNNING_ACTION)
                        .bean(orderManagerService, "cancelOrder")
                        .log("Order ${body} cancelled");


                // Credit service

                from("direct:reserveCredit")
                        .saga()
                        .propagation(SagaPropagation.MANDATORY)
                        .compensation("direct:refundCredit")
                        .transform().header(Exchange.SAGA_LONG_RUNNING_ACTION)
                        .bean(creditService, "reserveCredit")
                        .log("Credit ${header.amount} reserved in action ${body}");

                from("direct:refundCredit")
                        .transform().header(Exchange.SAGA_LONG_RUNNING_ACTION)
                        .bean(creditService, "refundCredit")
                        .log("Credit for action ${body} refunded");


                // Final actions
                from("direct:finalize")
                        .saga().propagation(SagaPropagation.NOT_SUPPORTED)
                        .choice()
                        .when(header("fail").isEqualTo(true))
                        .process(x -> {
                            throw new RuntimeException("fail");
                        })
                        .end();

            }
        };
    }

    public static class OrderManagerService {

        private Set<String> orders = new HashSet<>();

        public synchronized void newOrder(String id) {
            orders.add(id);
        }

        public synchronized void cancelOrder(String id) {
            orders.remove(id);
        }

        public synchronized Set<String> getOrders() {
            return new TreeSet<>(orders);
        }
    }

    public static class CreditService {

        private int totalCredit;

        private Map<String, Integer> reservations = new HashMap<>();

        public CreditService(int totalCredit) {
            this.totalCredit = totalCredit;
        }

        public synchronized void reserveCredit(String id, @Header("amount") int amount) {
            int credit = getCredit();
            if (amount > credit) {
                throw new IllegalStateException("Insufficient credit");
            }
            reservations.put(id, amount);
        }

        public synchronized void refundCredit(String id) {
            reservations.remove(id);
        }

        public synchronized int getCredit() {
            return totalCredit - reservations.values().stream().reduce(0, (a, b) -> a + b);
        }
    }
}
