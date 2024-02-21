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
package org.apache.camel.issues;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.ExpressionAdapter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class ErrorHandlerOnRedeliveryStopTest extends ContextTestSupport {

    private final AtomicInteger counter = new AtomicInteger(5);

    @Test
    public void testRetryWhile() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exception e = assertThrows(Exception.class,
                () -> template.sendBody("direct:start", "Hello World"),
                "Should throw an exception");

        RejectedExecutionException ree = assertIsInstanceOf(RejectedExecutionException.class, e.getCause());
        Assertions.assertEquals("I do not want to do this anymore", ree.getMessage());

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                errorHandler(defaultErrorHandler()
                        .retryWhile(new ExpressionAdapter() {
                            @Override
                            public Object evaluate(Exchange exchange) {
                                return counter.getAndDecrement() > 0;
                            }
                        })
                        .onRedelivery(new MyRedeliveryProcessor())
                        .redeliveryDelay(0)); // run fast

                from("direct:start")
                        .throwException(new IllegalArgumentException("Forced"))
                        .to("mock:result");
            }
        };
    }

    private class MyRedeliveryProcessor implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            if (counter.get() == 0) {
                exchange.setException(new RejectedExecutionException("I do not want to do this anymore"));
                exchange.setRouteStop(true); // stop redelivery
            }
        }
    }
}
