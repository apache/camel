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
package org.apache.camel.processor.async;

import java.util.Collection;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.AsyncProcessorAwaitManager;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AsyncProcessorAwaitManagerTest extends ContextTestSupport {

    @Test
    public void testAsyncAwait() throws Exception {
        final AsyncProcessorAwaitManager asyncProcessorAwaitManager = PluginHelper.getAsyncProcessorAwaitManager(context);
        asyncProcessorAwaitManager.getStatistics().setStatisticsEnabled(true);

        assertEquals(0, asyncProcessorAwaitManager.size());

        getMockEndpoint("mock:before").expectedBodiesReceived("Hello Camel");
        getMockEndpoint("mock:after").expectedBodiesReceived("Bye Camel");
        getMockEndpoint("mock:result").expectedBodiesReceived("Bye Camel");

        String reply = template.requestBody("direct:start", "Hello Camel", String.class);
        assertEquals("Bye Camel", reply);

        assertMockEndpointsSatisfied();

        assertEquals(0, asyncProcessorAwaitManager.size());
        assertEquals(1,
                asyncProcessorAwaitManager.getStatistics().getThreadsBlocked());
        assertEquals(0, asyncProcessorAwaitManager.getStatistics()
                .getThreadsInterrupted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("async", new MyAsyncComponent());

                from("direct:start").routeId("myRoute").to("mock:before").to("async:bye:camel").id("myAsync").to("mock:after")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                int size = PluginHelper.getAsyncProcessorAwaitManager(context).size();
                                log.info("async inflight: {}", size);
                                assertEquals(1, size);

                                Collection<AsyncProcessorAwaitManager.AwaitThread> threads
                                        = PluginHelper.getAsyncProcessorAwaitManager(context).browse();
                                AsyncProcessorAwaitManager.AwaitThread thread = threads.iterator().next();

                                long wait = thread.getWaitDuration();
                                log.info("Thread {} has waited for {} msec.", thread.getBlockedThread().getName(), wait);

                                assertEquals("myRoute", thread.getRouteId());
                                // assertEquals("myAsync", thread.getNodeId());
                                assertThat(thread.getNodeId()).matches("process[0-9]+");
                            }
                        }).to("mock:result");
            }
        };
    }

}
