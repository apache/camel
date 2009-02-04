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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.aggregate.AggregationStrategy;

public class ComposedMessageProcessorTest extends ContextTestSupport {

    @SuppressWarnings("unchecked")
    public void testValidatingCorrectOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedHeaderReceived("orderId", "myorderid");
                
        List<OrderItem> order = Arrays.asList(new OrderItem[] {
            new OrderItem("widget", 5), 
            new OrderItem("gadget", 10)});

        template.sendBodyAndHeader("direct:start", order, "orderId", "myorderid");
                
        assertMockEndpointsSatisfied();
        
        List<OrderItem> validatedOrder = resultEndpoint.getExchanges().get(0).getIn().getBody(List.class);
        assertTrue(validatedOrder.get(0).valid);
        assertTrue(validatedOrder.get(1).valid);
    }

    @SuppressWarnings("unchecked")
    public void testValidatingIncorrectOrder() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedHeaderReceived("orderId", "myorderid");
        
        List<OrderItem> order = Arrays.asList(new OrderItem[] {
            new OrderItem("widget", 500), 
            new OrderItem("gadget", 200)});

        template.sendBodyAndHeader("direct:start", order, "orderId", "myorderid");
                
        assertMockEndpointsSatisfied();
        
        List<OrderItem> validatedOrder = resultEndpoint.getExchanges().get(0).getIn().getBody(List.class);
        assertFalse(validatedOrder.get(0).valid);
        assertFalse(validatedOrder.get(1).valid);
    }   
    
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        jndi.bind("orderItemHelper", new OrderItemHelper());
        jndi.bind("widgetInventory", new WidgetInventory());
        jndi.bind("gadgetInventory", new GadgetInventory());
        return jndi;
    }    
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // split up the order so individual OrderItems can be validated by the appropriate bean
                from("direct:start").split().body().choice() 
                    .when().method("orderItemHelper", "isWidget").to("bean:widgetInventory", "seda:aggregate")
                    .otherwise().to("bean:gadgetInventory", "seda:aggregate");
                
                // collect and re-assemble the validated OrderItems into an order again
                from("seda:aggregate").aggregate(new MyOrderAggregationStrategy()).header("orderId").to("mock:result");
            }
        };
    }
    
    public static final class OrderItem {
        String type; // type of the item
        int quantity; // how many we want
        boolean valid; // whether that many items can be ordered             
        
        public OrderItem(String type, int quantity) {
            this.type = type;
            this.quantity = quantity;        
        }
    }
    
    public static final class OrderItemHelper {
        private OrderItemHelper() {
        }
        
        public static boolean isWidget(@Body OrderItem orderItem) {
            return orderItem.type.equals("widget");
        }
    }

    /**
     * Bean that checks whether the specified number of widgets can be ordered
     */
    public static final class WidgetInventory {
        public void checkInventory(@Body OrderItem orderItem) {
            assertEquals("widget", orderItem.type);
            if (orderItem.quantity < 10) {
                orderItem.valid = true;
            }
        }
    }    
    
    /**
     * Bean that checks whether the specified number of gadgets can be ordered
     */
    public static final class GadgetInventory {
        public void checkInventory(@Body OrderItem orderItem) {
            assertEquals("gadget", orderItem.type);
            if (orderItem.quantity < 20) {
                orderItem.valid = true;
            }
        }
    }
    
    /**
     * Aggregation strategy that re-assembles the validated OrderItems 
     * into an order, which is just a List.
     */
    public static final class MyOrderAggregationStrategy implements AggregationStrategy {
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            List<OrderItem> order = new ArrayList<OrderItem>(2);
            order.add(oldExchange.getIn().getBody(OrderItem.class));
            order.add(newExchange.getIn().getBody(OrderItem.class));
            oldExchange.getIn().setBody(order);
            return oldExchange;
        }
    }
}
