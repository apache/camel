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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class RecipientListParallelAggregateThreadPoolIssueTest extends ContextTestSupport {

    @Test
    public void testRecipientListParallelALot() throws Exception {
        String before = context.getExecutorServiceManager().resolveThreadName("foo");

        for (int i = 0; i < 10; i++) {
            MockEndpoint mock = getMockEndpoint("mock:result");
            mock.reset();
            mock.expectedBodiesReceivedInAnyOrder("c", "b", "a");

            template.sendBodyAndHeader("direct:start", "Hello World", "foo", "direct:a,direct:b,direct:c");

            assertMockEndpointsSatisfied();
        }

        String after = context.getExecutorServiceManager().resolveThreadName("foo");
        int num1 = context.getTypeConverter().convertTo(int.class, before);
        int num2 = context.getTypeConverter().convertTo(int.class, after);
        int diff = num2 - num1;
        // should be at least 10 + 1 other threads (10 in parallel pool + 1 in
        // aggregate pool)
        // we run unit test per jmv fork, so there may be a hanging thread
        assertTrue("There should be 12 or more threads in use, was: " + diff, diff >= 11);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getExecutorServiceManager().setThreadNamePattern("#counter#");

                from("direct:start").recipientList(header("foo")).parallelProcessing();

                from("direct:a").to("log:a").transform(constant("a")).to("mock:result");
                from("direct:b").to("log:b").transform(constant("b")).to("mock:result");
                from("direct:c").to("log:c").transform(constant("c")).to("mock:result");
            }
        };
    }
}
