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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ThreadPoolRejectedPolicy;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version
 */
public class ThreadsRejectedExecutionWithDeadLetterTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public void testThreadsRejectedExecution() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").errorHandler(deadLetterChannel("mock:failed"))
                        .to("log:before")
                        // will use our custom pool
                        .threads()
                        .maxPoolSize(1).poolSize(1) // 1 thread max
                        .maxQueueSize(1)            // 1 queued task
                        //(Test fails whatever the chosen policy below)
                        .rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                        .delay(1000)
                        .to("log:after")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(2);
        getMockEndpoint("mock:failed").expectedMessageCount(1);

        template.sendBody("seda:start", "Hello World"); // will block
        template.sendBody("seda:start", "Hi World");    // will be queued
        template.sendBody("seda:start", "Bye World");   // will be rejected

        assertMockEndpointsSatisfied();
    }

    public void testThreadsRejectedExecutionWithRedelivery() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").errorHandler(deadLetterChannel("mock:failed").maximumRedeliveries(5))
                        .to("log:before")
                        // will use our custom pool
                        .threads()
                        .maxPoolSize(1).poolSize(1) // 1 thread max
                        .maxQueueSize(1)            // 1 queued task
                        //(Test fails whatever the chosen policy below)
                        .rejectedPolicy(ThreadPoolRejectedPolicy.Abort)
                        .delay(1000)
                        .to("log:after")
                        .to("mock:result");
            }
        });
        context.start();

        getMockEndpoint("mock:result").expectedMessageCount(3);
        getMockEndpoint("mock:failed").expectedMessageCount(0);

        template.sendBody("seda:start", "Hello World"); // will block
        template.sendBody("seda:start", "Hi World");    // will be queued
        template.sendBody("seda:start", "Bye World");   // will be rejected and queued on redelivery later

        assertMockEndpointsSatisfied();
    }

}