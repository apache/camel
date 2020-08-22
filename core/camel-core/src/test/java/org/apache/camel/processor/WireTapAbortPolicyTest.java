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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.builder.ThreadPoolBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.concurrent.ThreadPoolRejectedPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Wire tap unit test
 */
public class WireTapAbortPolicyTest extends ContextTestSupport {
    protected MockEndpoint tap;
    protected MockEndpoint result;
    protected ExecutorService pool;

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        if (pool != null) {
            pool.shutdownNow();
        }
    }

    @Test
    public void testSend() throws Exception {
        // hello must come first, as we have delay on the tapped route
        result.expectedMinimumMessageCount(2);
        tap.expectedMinimumMessageCount(1);

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        try {
            template.sendBody("direct:start", "C");
            fail("Task should be rejected");
        } catch (Exception e) {
            assertIsInstanceOf(RejectedExecutionException.class, e.getCause());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        tap = getMockEndpoint("mock:tap");
        result = getMockEndpoint("mock:result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                // use a custom thread pool for sending tapped messages
                ExecutorService pool = new ThreadPoolBuilder(context)
                        // only allow 1 thread and 1 pending task
                        .poolSize(1)
                        .maxPoolSize(1)
                        .maxQueueSize(1)
                        // and about tasks
                        .rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                        .build();

                from("direct:start").to("log:foo")
                        // pass in the custom pool to the wireTap DSL
                        .wireTap("direct:tap").executorService(pool).to("mock:result");
                // END SNIPPET: e1

                from("direct:tap").delay(1000).to("mock:tap");
            }
        };
    }
}
