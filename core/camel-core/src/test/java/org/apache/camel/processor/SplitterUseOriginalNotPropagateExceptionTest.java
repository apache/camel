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
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.CamelEvent.ExchangeFailedEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.junit.Test;

public class SplitterUseOriginalNotPropagateExceptionTest extends ContextTestSupport {

    private MyEventNotifier notifier = new MyEventNotifier();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().addEventNotifier(notifier);
        return context;
    }

    @Test
    public void testUseOriginalNotPropgateException() throws Exception {
        assertEquals(0, notifier.getErrors());

        getMockEndpoint("mock:line").expectedBodiesReceived("Hello", "World");
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello,Kaboom,World");

        try {
            template.sendBody("direct:start", "Hello,Kaboom,World");
        } catch (Exception e) {
            fail("Should not fail");
        }

        assertMockEndpointsSatisfied();

        // there should only be 1 error as we do not propagate errors to the
        // parent
        assertEquals(1, notifier.getErrors());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").split(body()).aggregationStrategy(AggregationStrategies.useOriginal(false)).filter(simple("${body} == 'Kaboom'"))
                    .throwException(new IllegalArgumentException("Forced error")).end().to("mock:line").end().to("mock:result");
            }
        };
    }

    private static class MyEventNotifier extends EventNotifierSupport {

        private int errors;

        @Override
        public void notify(CamelEvent event) throws Exception {
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
