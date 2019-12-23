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
package org.apache.camel.processor.exceptionpolicy;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test for the when expression on the exception type.
 */
public class DefaultExceptionPolicyStrategyUsingWhenTest extends ContextTestSupport {

    private static final String ERROR_QUEUE = "mock:error";
    private static final String ERROR_USER_QUEUE = "mock:usererror";

    public static class MyUserException extends Exception {
        private static final long serialVersionUID = 1L;

        public MyUserException(String message) {
            super(message);
        }
    }

    @Test
    public void testNoWhen() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);

        try {
            template.sendBody("direct:a", "Hello Camel");
            fail("Should have thrown an Exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testWithWhen() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_USER_QUEUE);
        mock.expectedMessageCount(1);

        try {
            template.sendBodyAndHeader("direct:a", "Hello Camel", "user", "admin");
            fail("Should have thrown an Exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET e1
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:error").redeliveryDelay(0).maximumRedeliveries(3));

                // here we define our onException to catch MyUserException when
                // there is a header[user] on the exchange that is not null
                onException(MyUserException.class).onWhen(header("user").isNotNull()).maximumRedeliveries(1)
                    // setting delay to zero is just to make unit testing faster
                    .redeliveryDelay(0).to(ERROR_USER_QUEUE);

                // here we define onException to catch MyUserException as a kind
                // of fallback when the above did not match.
                // Notice: The order how we have defined these onException is
                // important as Camel will resolve in the same order as they
                // have been defined
                onException(MyUserException.class).maximumRedeliveries(2)
                    // setting delay to zero is just to make unit testing faster
                    .redeliveryDelay(0).to(ERROR_QUEUE);
                // END SNIPPET e1

                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String s = exchange.getIn().getBody(String.class);
                        if ("Hello Camel".equals(s)) {
                            throw new MyUserException("Forced for testing");
                        }
                        exchange.getMessage().setBody("Hello World");
                    }
                }).to("mock:result");
            }
        };
    }

}
