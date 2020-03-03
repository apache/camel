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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class TwoSchedulerTest extends ContextTestSupport {

    @Test
    public void testTwoScheduler() throws Exception {
        getMockEndpoint("mock:a").expectedMinimumMessageCount(4);
        getMockEndpoint("mock:b").expectedMinimumMessageCount(2);

        assertMockEndpointsSatisfied();

        // should use same thread as they share the same scheduler
        String tn1 = getMockEndpoint("mock:a").getReceivedExchanges().get(0).getMessage().getHeader("tn", String.class);
        String tn2 = getMockEndpoint("mock:b").getReceivedExchanges().get(0).getMessage().getHeader("tn", String.class);
        assertSame(tn1, tn2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("scheduler://foo?delay=100")
                        .setHeader("tn", simple("${threadName}"))
                        .to("mock:a");

                from("scheduler://foo?delay=200")
                        .setHeader("tn", simple("${threadName}"))
                        .to("mock:b");
            }
        };
    }

}
