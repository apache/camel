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
package org.apache.camel.processor.aggregator;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.apache.camel.processor.aggregate.MemoryAggregationRepository;
import org.junit.Test;

public class BeanBeforeAggregateIssueTest extends ContextTestSupport {

    private MyAggRepo myRepo = new MyAggRepo();

    @Test
    public void testBeanBeforeAggregation() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(3).create();

        getMockEndpoint("mock:result").expectedBodiesReceived("A+B");

        template.sendBody("seda:start", "A");
        template.sendBody("seda:start", "B");

        assertMockEndpointsSatisfied();

        // wait for all exchanges to be done (2 input + 1 aggregated)
        notify.matches(5, TimeUnit.SECONDS);

        // should have confirmed
        assertTrue("Should have confirmed", myRepo.isConfirm());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start").bean(TestBean.class).aggregate(constant("true"), new BodyInAggregatingStrategy()).aggregationRepository(myRepo).completionSize(2)
                    .to("mock:result");
            }
        };
    }

    public static final class TestBean {

        public String doNothing(String foo) {
            return foo;
        }
    }

    private static final class MyAggRepo extends MemoryAggregationRepository {

        private volatile boolean confirm;

        @Override
        public void confirm(CamelContext camelContext, String exchangeId) {
            // test that confirm is invoked
            super.confirm(camelContext, exchangeId);
            confirm = true;
        }

        public boolean isConfirm() {
            return confirm;
        }
    }
}
