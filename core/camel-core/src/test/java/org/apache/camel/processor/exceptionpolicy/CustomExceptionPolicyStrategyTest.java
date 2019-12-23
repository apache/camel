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

import java.util.Set;

import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.errorhandler.ExceptionPolicyKey;
import org.apache.camel.processor.errorhandler.ExceptionPolicyStrategy;
import org.junit.Test;

/**
 * Unit test with a user plugged in exception policy to use instead of default.
 */
public class CustomExceptionPolicyStrategyTest extends ContextTestSupport {

    private static final String MESSAGE_INFO = "messageInfo";
    private static final String ERROR_QUEUE = "mock:error";

    public static class MyPolicyException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    // START SNIPPET e2
    public static class MyPolicy implements ExceptionPolicyStrategy {

        @Override
        public ExceptionPolicyKey getExceptionPolicy(Set<ExceptionPolicyKey> exceptionPolicices, Exchange exchange, Throwable exception) {
            // This is just an example that always forces the exception type
            // configured
            // with MyPolicyException to win.
            return new ExceptionPolicyKey(null, MyPolicyException.class, null);
        }
    }
    // END SNIPPET e2

    @Test
    public void testCustomPolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm my policy exception");

        try {
            template.sendBody("direct:a", "Hello Camel");
            fail("Should have thrown an exception");
        } catch (Exception e) {
            // expected
        }

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET e1
            public void configure() throws Exception {
                // configure the error handler to use my policy instead of the
                // default from Camel
                errorHandler(deadLetterChannel("mock:error").exceptionPolicyStrategy(new MyPolicy()));

                onException(MyPolicyException.class).maximumRedeliveries(1).redeliveryDelay(0).setHeader(MESSAGE_INFO, constant("Damm my policy exception")).to(ERROR_QUEUE);

                onException(CamelException.class).maximumRedeliveries(3).redeliveryDelay(0).setHeader(MESSAGE_INFO, constant("Damm a Camel exception")).to(ERROR_QUEUE);
                // END SNIPPET e1

                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String s = exchange.getIn().getBody(String.class);
                        if ("Hello Camel".equals(s)) {
                            throw new CamelExchangeException("Forced for testing", exchange);
                        }
                        exchange.getMessage().setBody("Hello World");
                    }
                }).to("mock:result");
            }
        };
    }

}
