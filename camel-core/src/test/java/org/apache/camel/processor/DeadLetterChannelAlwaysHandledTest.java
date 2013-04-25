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

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.seda.SedaConsumer;
import org.apache.camel.spi.ExceptionHandler;

public class DeadLetterChannelAlwaysHandledTest extends ContextTestSupport {

    private static final AtomicBoolean called = new AtomicBoolean();

    public void testDeadLetterChannelAlwaysHandled() throws Exception {
        // need to set exception handler manually to work around an issue configuring from uri
        SedaConsumer seda = (SedaConsumer) context.getRoute("foo").getConsumer();
        seda.setExceptionHandler(new MyExceptionHandler());

        getMockEndpoint("mock:foo").expectedMessageCount(1);
        getMockEndpoint("mock:bar").expectedMessageCount(1);
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBody("seda:foo", "Hello World");

        assertMockEndpointsSatisfied();

        assertFalse("Should not have called", called.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                errorHandler(deadLetterChannel("mock:dead"));

                from("seda:foo?synchronous=true").routeId("foo")
                    .to("mock:foo")
                    .to("direct:bar")
                    .to("mock:result");

                from("direct:bar").routeId("bar")
                    .onException(IllegalArgumentException.class).maximumRedeliveries(3).redeliveryDelay(0).end()
                    .to("mock:bar")
                    .throwException(new IllegalArgumentException("Forced"));
            }
        };
    }

    private final class MyExceptionHandler implements ExceptionHandler {

        @Override
        public void handleException(Throwable exception) {
            called.set(true);
        }

        @Override
        public void handleException(String message, Throwable exception) {
            called.set(true);
        }

        @Override
        public void handleException(String message, Exchange exchange, Throwable exception) {
            called.set(true);
        }
    }
}
