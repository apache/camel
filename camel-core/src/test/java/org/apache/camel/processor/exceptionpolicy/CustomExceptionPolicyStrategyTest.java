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

import java.util.Map;

import org.apache.camel.CamelException;
import org.apache.camel.CamelExchangeException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ExceptionType;

/**
 * Unit test with a user plugged in exception policy to use instead of default.
 */
public class CustomExceptionPolicyStrategyTest extends ContextTestSupport {

    private static final String MESSAGE_INFO = "messageInfo";
    private static final String ERROR_QUEUE = "mock:error";

    public static class MyPolicyException extends Exception {
    }

    // START SNIPPET e2
    public static class MyPolicy implements ExceptionPolicyStrategy {

        public ExceptionType getExceptionPolicy(Map<Class, ExceptionType> exceptionPolicices,
                                                Exchange exchange,
                                                Throwable exception) {
            // This is just an example that always forces the exception type configured
            // with MyPolicyException to win.
            return exceptionPolicices.get(MyPolicyException.class);
        }
    }
    // END SNIPPET e2

    public void testCustomPolicy() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_QUEUE);
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(MESSAGE_INFO, "Damm my policy exception");

        template.sendBody("direct:a", "Hello Camel");

        mock.assertIsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET e1
            public void configure() throws Exception {
                // configure the error handler to use my policy instead of the default from Camel
                errorHandler(deadLetterChannel().exceptionPolicyStrategy(new MyPolicy()));

                exception(MyPolicyException.class)
                    .maximumRedeliveries(1)
                    .setHeader(MESSAGE_INFO, "Damm my policy exception")
                    .to(ERROR_QUEUE);

                exception(CamelException.class)
                    .maximumRedeliveries(3)
                    .setHeader(MESSAGE_INFO, "Damm a Camel exception")
                    .to(ERROR_QUEUE);
                // END SNIPPET e1

                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String s = exchange.getIn().getBody(String.class);
                        if ("Hello Camel".equals(s)) {
                            throw new CamelExchangeException("Forced for testing", exchange);
                        }
                        exchange.getOut().setBody("Hello World");
                    }
                }).to("mock:result");
            }
        };
    }

}
