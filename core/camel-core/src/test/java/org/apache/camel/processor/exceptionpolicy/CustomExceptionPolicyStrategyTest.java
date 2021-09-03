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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.errorhandler.DefaultExceptionPolicyStrategy;
import org.apache.camel.processor.errorhandler.ExceptionPolicyStrategy;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for a custom ExceptionPolicy
 */
public class CustomExceptionPolicyStrategyTest extends ContextTestSupport {

    private static final String ERROR_QUEUE = "mock:error";
    private static final String ERROR_USER_QUEUE = "mock:usererror";

    public static class MyUserException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        public MyUserException(String message) {
            super(message);
        }

        protected MyUserException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("reverse-strategy", ExceptionPolicyStrategy.class, new DefaultExceptionPolicyStrategy() {
            @Override
            public Iterable<Throwable> createExceptionIterable(Throwable exception) {
                List<Throwable> answer = new ArrayList<>();
                // reversing default implementation order
                for (Throwable throwable : super.createExceptionIterable(exception)) {
                    answer.add(0, throwable);
                }
                return answer;
            }
        });
        return answer;
    }

    /**
     * With the default implementation throwing MyUserException is matched to the generic Exception.class policy because
     * of its cause. Whereas when reversing the order to check, MyUserException is matched to its superclass
     * IllegalStateException.class policy first
     */
    @Test
    public void testReverseBehavior() throws Exception {
        MockEndpoint mock = getMockEndpoint(ERROR_USER_QUEUE);
        mock.expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:a", "Hello Camel");
            fail("Should have thrown an Exception");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                onException(IllegalStateException.class).maximumRedeliveries(1).redeliveryDelay(0)
                        .to(ERROR_USER_QUEUE);

                onException(Exception.class).maximumRedeliveries(1).redeliveryDelay(0)
                        .to(ERROR_QUEUE);

                from("direct:a").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String s = exchange.getIn().getBody(String.class);
                        if ("Hello Camel".equals(s)) {
                            throw new MyUserException("Forced for testing", new IOException("Uh oh!"));
                        }
                        exchange.getMessage().setBody("Hello World");
                    }
                }).to("mock:result");
            }
        };
    }
}
