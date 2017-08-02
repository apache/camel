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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version 
 */
public class ThreadsRejectedExecutionTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testThreadsRejectedExecution() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use a custom pool which rejects any new tasks while currently in progress
                // this should force the ThreadsProcessor to run the tasks itself
                ExecutorService pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

                from("seda:start")
                    .to("log:before")
                    // will use our custom pool
                    .threads().executorService(pool)
                    .delay(200)
                    .to("log:after")
                    .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(3);

        template.sendBody("seda:start", "Hello World");
        template.sendBody("seda:start", "Hi World");
        template.sendBody("seda:start", "Bye World");

        assertMockEndpointsSatisfied();
    }

    public void testThreadsRejectedExecutionCallerNotRuns() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // use a custom pool which rejects any new tasks while currently in progress
                // this should force the ThreadsProcessor to run the tasks itself
                ExecutorService pool = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

                from("seda:start")
                    .to("log:before")
                    // will use our custom pool
                    .threads().executorService(pool).callerRunsWhenRejected(false)
                    .delay(200)
                    .to("log:after")
                    .to("mock:result");
            }
        });
        context.start();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);
        // wait at most 2 seconds
        mock.setResultWaitTime(2000);

        template.sendBody("seda:start", "Hello World");
        template.sendBody("seda:start", "Hi World");
        template.sendBody("seda:start", "Bye World");

        // should not be possible to route all 3
        mock.assertIsNotSatisfied();

        // only 1 should arrive
        assertEquals(1, mock.getReceivedCounter());
    }

    public void testThreadsRejectedDiscard() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                        .to("log:before")
                        .threads(1, 1).maxPoolSize(1).maxQueueSize(2).rejectedPolicy(ThreadPoolRejectedPolicy.Discard)
                        .delay(100)
                        .to("log:after")
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(10).create();
        
        getMockEndpoint("mock:result").expectedMinimumMessageCount(2);
        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:start", "Message " + i);
        }
        assertMockEndpointsSatisfied();

        assertTrue(notify.matchesMockWaitTime());

        int inflight = context.getInflightRepository().size();
        assertEquals(0, inflight);
    }
    
    public void testThreadsRejectedDiscardOldest() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                        .to("log:before")
                        .threads(1, 1).maxPoolSize(1).maxQueueSize(2).rejectedPolicy(ThreadPoolRejectedPolicy.DiscardOldest)
                        .delay(100)
                        .to("log:after")
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(10).create();

        getMockEndpoint("mock:result").expectedMinimumMessageCount(2);
        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:start", "Message " + i);
        }
        assertMockEndpointsSatisfied();

        assertTrue(notify.matchesMockWaitTime());

        int inflight = context.getInflightRepository().size();
        assertEquals(0, inflight);
    }

    public void testThreadsRejectedAbort() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                        .to("log:before")
                        .threads(1, 1).maxPoolSize(1).maxQueueSize(2).rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                        .delay(100)
                        .to("log:after")
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(10).create();

        getMockEndpoint("mock:result").expectedMinimumMessageCount(2);
        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:start", "Message " + i);
        }
        assertMockEndpointsSatisfied();

        assertTrue(notify.matchesMockWaitTime());

        int inflight = context.getInflightRepository().size();
        assertEquals(0, inflight);
    }

    public void testThreadsRejectedCallerRuns() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start")
                        .to("log:before")
                        .threads(1, 1).maxPoolSize(1).maxQueueSize(2).rejectedPolicy(ThreadPoolRejectedPolicy.CallerRuns)
                        .delay(100)
                        .to("log:after")
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(10).create();

        getMockEndpoint("mock:result").expectedMessageCount(10);
        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:start", "Message " + i);
        }
        assertMockEndpointsSatisfied();

        assertTrue(notify.matchesMockWaitTime());

        int inflight = context.getInflightRepository().size();
        assertEquals(0, inflight);
    }

    public void testThreadsRejectedAbortNoRedelivery() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                onException(Exception.class).redeliveryDelay(250).maximumRedeliveries(3).handled(true).to("mock:error");

                from("seda:start")
                        .to("log:before")
                        .threads(1, 1).maxPoolSize(1).maxQueueSize(2).rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                        .delay(250)
                        .to("log:after")
                        .to("mock:result");
            }
        });
        context.start();

        NotifyBuilder notify = new NotifyBuilder(context).whenDone(10).create();

        // there should be error handling for aborted tasks (eg no redeliveries and no error handling)
        getMockEndpoint("mock:error").expectedMessageCount(0);

        getMockEndpoint("mock:result").expectedMinimumMessageCount(2);
        for (int i = 0; i < 10; i++) {
            template.sendBody("seda:start", "Message " + i);
        }
        assertMockEndpointsSatisfied();

        assertTrue(notify.matchesMockWaitTime());

        int inflight = context.getInflightRepository().size();
        assertEquals(0, inflight);
    }

}