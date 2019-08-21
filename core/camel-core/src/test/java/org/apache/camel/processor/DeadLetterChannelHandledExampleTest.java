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

import java.util.Map;

import org.apache.camel.Body;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Headers;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test to verify that handled policy is working as expected for wiki
 * documentation.
 */
public class DeadLetterChannelHandledExampleTest extends ContextTestSupport {

    @Test
    public void testOrderOK() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedBodiesReceived("Order OK");
        result.expectedHeaderReceived("orderid", "123");

        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(0);

        Object out = template.requestBodyAndHeader("direct:start", "Order: MacBook Pro", "customerid", "444");
        assertEquals("Order OK", out);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testOrderERROR() throws Exception {
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedBodiesReceived("Order ERROR");
        error.expectedHeaderReceived("orderid", "failed");

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(0);

        Object out = template.requestBodyAndHeader("direct:start", "Order: kaboom", "customerid", "555");
        assertEquals("Order ERROR", out);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // we do special error handling for when OrderFailedException is
                // thrown
                onException(OrderFailedException.class)
                    // we mark the exchange as handled so the caller doesn't
                    // receive the
                    // OrderFailedException but whatever we want to return
                    // instead
                    .handled(true)
                    // this bean handles the error handling where we can
                    // customize the error
                    // response using java code
                    .bean(OrderService.class, "orderFailed")
                    // and since this is an unit test we use mocks for testing
                    .to("mock:error");

                // this is just the generic error handler where we set the
                // destination
                // and the number of redeliveries we want to try
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(1));

                // this is our route where we handle orders
                from("direct:start")
                    // this bean is our order service
                    .bean(OrderService.class, "handleOrder")
                    // this is the destination if the order is OK
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

    // START SNIPPET: e2
    /**
     * Order service as a plain POJO class
     */
    public static class OrderService {

        /**
         * This method handle our order input and return the order
         *
         * @param headers the in headers
         * @param payload the in payload
         * @return the out payload
         * @throws OrderFailedException is thrown if the order cannot be
         *             processed
         */
        public Object handleOrder(@Headers Map headers, @Body String payload) throws OrderFailedException {
            headers.put("customerid", headers.get("customerid"));
            if ("Order: kaboom".equals(payload)) {
                throw new OrderFailedException("Cannot order: kaboom");
            } else {
                headers.put("orderid", "123");
                return "Order OK";
            }
        }

        /**
         * This method creates the response to the caller if the order could not
         * be processed
         * 
         * @param headers the in headers
         * @param payload the in payload
         * @return the out payload
         */
        public Object orderFailed(@Headers Map headers, @Body String payload) {
            headers.put("customerid", headers.get("customerid"));
            headers.put("orderid", "failed");
            return "Order ERROR";
        }
    }
    // END SNIPPET: e2

    // START SNIPPET: e3
    /**
     * Exception thrown if the order cannot be processed
     */
    public static class OrderFailedException extends Exception {

        private static final long serialVersionUID = 1L;

        public OrderFailedException(String message) {
            super(message);
        }

    }
    // END SNIPPET: e3

}
