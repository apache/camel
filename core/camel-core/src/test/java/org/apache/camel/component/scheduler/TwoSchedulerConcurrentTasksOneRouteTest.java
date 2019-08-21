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
package org.apache.camel.component.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class TwoSchedulerConcurrentTasksOneRouteTest extends ContextTestSupport {

    private AtomicBoolean sleep = new AtomicBoolean(true);

    @Test
    public void testTwoScheduler() throws Exception {
        getMockEndpoint("mock:done").expectedMinimumMessageCount(10);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // number of concurrent task a thread pool should have
                SchedulerComponent comp = context.getComponent("scheduler", SchedulerComponent.class);
                comp.setConcurrentTasks(2);

                // let this route scheduler use all 2 concurrent tasks at the
                // same time
                from("scheduler://foo?delay=250&scheduler.concurrentTasks=2").process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        if (sleep.compareAndSet(true, false)) {
                            log.info("Thread is sleeping");
                            Thread.sleep(1000);
                            log.info("Thread is done sleeping");
                        }
                    }
                }).to("log:done").to("mock:done");
            }
        };
    }

}
