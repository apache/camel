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
package org.apache.camel.processor.async;

import java.util.Collection;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.junit.Test;

/**
 * @version 
 */
public class AsyncProcessorAwaitManagerInterruptTest extends ContextTestSupport {

    @Test
    public void testAsyncAwaitInterrupt() throws Exception {
        context.getAsyncProcessorAwaitManager().getStatistics().setStatisticsEnabled(true);

        assertEquals(0, context.getAsyncProcessorAwaitManager().size());

        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        try {
            template.requestBody("direct:start", "Hello Camel", String.class);
        } catch (CamelExecutionException e) {
            RejectedExecutionException cause = assertIsInstanceOf(RejectedExecutionException.class, e.getCause());
            assertTrue(cause.getMessage().startsWith("Interrupted while waiting for asynchronous callback"));
        }

        assertMockEndpointsSatisfied();

        assertEquals(0, context.getAsyncProcessorAwaitManager().size());
        assertEquals(1, context.getAsyncProcessorAwaitManager().getStatistics().getThreadsBlocked());
        assertEquals(1, context.getAsyncProcessorAwaitManager().getStatistics().getThreadsInterrupted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start").routeId("myRoute")
                        .to("mock:before")
                        .to("async:bye:camel?delay=2000").id("myAsync")
                        .to("mock:after")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                int size = context.getAsyncProcessorAwaitManager().size();
                                log.info("async inflight: {}", size);
                                assertEquals(1, size);

                                Collection<AsyncProcessorAwaitManager.AwaitThread> threads = context.getAsyncProcessorAwaitManager().browse();
                                AsyncProcessorAwaitManager.AwaitThread thread = threads.iterator().next();

                                // lets interrupt it
                                String id = thread.getExchange().getExchangeId();
                                context.getAsyncProcessorAwaitManager().interrupt(id);
                            }
                        })
                        .transform(constant("Hi Camel"))
                        .to("mock:result");
            }
        };
    }

}