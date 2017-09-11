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
package org.apache.camel.processor.exceptionpolicy;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * Unit test for the when expression on the exception type.
 */
public class DefaultExceptionPolicyStrategyUsingOnlyWhenTest extends ContextTestSupport {

    private static final String ERROR_QUEUE = "mock:error";
    private static final String ERROR_USER_QUEUE = "mock:usererror";

    public static class MyUserException extends Exception {
        private static final long serialVersionUID = 1L;

        public MyUserException(String message) {
            super(message);
        }
    }

    public void testNoWhen() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("direct:a", "Hello Camel");

        assertMockEndpointsSatisfied();
    }

    public void testWithWhen() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_USER_QUEUE);
        mock.expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBodyAndHeader("direct:a", "Hello Camel", "user", "admin");
            fail("Should have thrown an Exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                errorHandler(deadLetterChannel(ERROR_QUEUE).maximumRedeliveries(0).redeliveryDelay(100));

                onException(MyUserException.class).onWhen(header("user").isNotNull())
                    .maximumRedeliveries(1).redeliveryDelay(0)
                    .to(ERROR_USER_QUEUE);

                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String s = exchange.getIn().getBody(String.class);
                        if ("Hello Camel".equals(s)) {
                            throw new MyUserException("Forced for testing");
                        }
                        exchange.getOut().setBody("Hello World");
                    }
                }).to("mock:result");
            }
        };
    }

}