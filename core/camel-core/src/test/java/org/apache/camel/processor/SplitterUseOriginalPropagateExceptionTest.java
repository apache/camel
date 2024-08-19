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

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

public class SplitterUseOriginalPropagateExceptionTest extends ContextTestSupport {

    private final MyEventNotifier notifier = new MyEventNotifier();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(notifier);
        return context;
    }

    @Test
    public void testUseOriginalPropagateException() throws Exception {
        assertEquals(0, notifier.getErrors());

        getMockEndpoint("mock:line").expectedBodiesReceived("Hello", "World");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Hello,Kaboom,World");
            fail("Should fail");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();

        // there should be 1+1 error as we propagate error back to the parent
        assertEquals(2, notifier.getErrors());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .onCompletion().process(e -> {
                            Exception caught = e.getException();
                            assertNull(caught);
                            caught = e.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                            assertIsInstanceOf(IllegalArgumentException.class, caught);
                            assertEquals("Forced error", caught.getMessage());
                        }).end()
                        .split(body()).aggregationStrategy(AggregationStrategies.useOriginal(true))
                        .filter(simple("${body} == 'Kaboom'"))
                        .throwException(new IllegalArgumentException("Forced error")).end().to("mock:line").end()
                        .to("mock:result");
            }
        };
    }

    private static class MyEventNotifier extends EventNotifierSupport {

        private int errors;

        @Override
        public void notify(CamelEvent event) {
            errors++;
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof ExchangeFailedEvent;
        }

        public int getErrors() {
            return errors;
        }
    }
}
