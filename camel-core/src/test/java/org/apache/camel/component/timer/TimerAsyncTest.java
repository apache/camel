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
package org.apache.camel.component.timer;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * @version
 */
public class TimerAsyncTest extends ContextTestSupport {

    @Test
    public void testSync() throws Exception {
        TimerEndpoint endpoint = context.getEndpoint("timer:foo?synchronous=true", TimerEndpoint.class);
        assertTrue("Timer endpoint must be synchronous, but it isn't", endpoint.isSynchronous());
    }

    @Test
    public void testAsync() throws Exception {
        TimerEndpoint endpoint = context.getEndpoint("timer:foo", TimerEndpoint.class);
        assertFalse("Timer endpoint must be asynchronous, but it isn't", endpoint.isSynchronous());
    }

    @Test
    public void testAsyncRouting() throws Exception {
        final int threads = 5;

        // should trigger many tasks as we are async
        getMockEndpoint("mock:task").expectedMinimumMessageCount(20);

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("timer://foo?fixedRate=true&delay=0&period=10").id("timer")
                        .threads(threads, threads).maxQueueSize(1).rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)
                        .to("log:task")
                        .to("mock:task")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                // simulate long task
                                TimeUnit.MILLISECONDS.sleep(50);
                            }
                        });
            }
        });
        context.start();

        assertMockEndpointsSatisfied();
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }
}
