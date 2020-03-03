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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class ComposedMessageProcessorTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    @Test
    public void testValidatingCorrectOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedHeaderReceived("orderId", "myorderid");

        List<OrderItem> order = Arrays.asList(new OrderItem[] {new OrderItem("widget", 5), new OrderItem("gadget", 10)});

        template.sendBodyAndHeader("direct:start", order, "orderId", "myorderid");

        assertMockEndpointsSatisfied();

        List<OrderItem> validatedOrder = resultEndpoint.getExchanges().get(0).getIn().getBody(List.class);
        assertTrue(validatedOrder.get(0).valid);
        assertTrue(validatedOrder.get(1).valid);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testValidatingIncorrectOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedHeaderReceived("orderId", "myorderid");

        // START SNIPPET: e1
        List<OrderItem> order = Arrays.asList(new OrderItem[] {new OrderItem("widget", 500), new OrderItem("gadget", 200)});

        template.sendBodyAndHeader("direct:start", order, "orderId", "myorderid");
        // END SNIPPET: e1

        assertMockEndpointsSatisfied();

        List<OrderItem> validatedOrder = resultEndpoint.getExchanges().get(0).getIn().getBody(List.class);
        assertFalse(validatedOrder.get(0).valid);
        assertFalse(validatedOrder.get(1).valid);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry jndi = super.createRegistry();
        jndi.bind("orderItemHelper", new OrderItemHelper());
        jndi.bind("widgetInventory", new WidgetInventory());
        jndi.bind("gadgetInventory", new GadgetInventory());
        return jndi;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e2
                // split up the order so individual OrderItems can be validated
                // by the appropriate bean
                from("direct:start").split().body().choice().when().method("orderItemHelper", "isWidget").to("bean:widgetInventory").otherwise().to("bean:gadgetInventory").end()
                    .to("seda:aggregate");

                // collect and re-assemble the validated OrderItems into an
                // order again
                from("seda:aggregate").aggregate(new MyOrderAggregationStrategy()).header("orderId").completionTimeout(100).completionTimeoutCheckerInterval(10).to("mock:result");
                // END SNIPPET: e2
            }
        };
    }

    // START SNIPPET: e3
    public static final class OrderItem {
        String type; // type of the item
        int quantity; // how many we want
        boolean valid; // whether that many items can be ordered

        public OrderItem(String type, int quantity) {
            this.type = type;
            this.quantity = quantity;
        }
    }
    // END SNIPPET: e3

    public static final class OrderItemHelper {
        private OrderItemHelper() {
        }

        // START SNIPPET: e4
        public static boolean isWidget(@Body OrderItem orderItem) {
            return orderItem.type.equals("widget");
        }
        // END SNIPPET: e4
    }

    /**
     * Bean that checks whether the specified number of widgets can be ordered
     */
    // START SNIPPET: e5
    public static final class WidgetInventory {
        public void checkInventory(@Body OrderItem orderItem) {
            assertEquals("widget", orderItem.type);
            if (orderItem.quantity < 10) {
                orderItem.valid = true;
            }
        }
    }
    // END SNIPPET: e5

    /**
     * Bean that checks whether the specified number of gadgets can be ordered
     */
    // START SNIPPET: e6
    public static final class GadgetInventory {
        public void checkInventory(@Body OrderItem orderItem) {
            assertEquals("gadget", orderItem.type);
            if (orderItem.quantity < 20) {
                orderItem.valid = true;
            }
        }
    }
    // END SNIPPET: e6

    /**
     * Aggregation strategy that re-assembles the validated OrderItems into an
     * order, which is just a List.
     */
    // START SNIPPET: e7
    public static final class MyOrderAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            List<OrderItem> order = new ArrayList<>(2);
            order.add(oldExchange.getIn().getBody(OrderItem.class));
            order.add(newExchange.getIn().getBody(OrderItem.class));

            oldExchange.getIn().setBody(order);
            return oldExchange;
        }
    }
    // END SNIPPET: e7
}
