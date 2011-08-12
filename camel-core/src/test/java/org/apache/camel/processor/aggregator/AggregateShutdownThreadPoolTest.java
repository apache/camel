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
package org.apache.camel.processor.aggregator;

import java.util.concurrent.ExecutorService;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.BodyInAggregatingStrategy;

/**
 * @version 
 */
public class AggregateShutdownThreadPoolTest extends ContextTestSupport {

    private ExecutorService myPool;

    public void testAggregateShutdownDefaultThreadPoolTest() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("A+B+C");

        template.sendBodyAndHeader("direct:foo", "A", "id", 123);
        template.sendBodyAndHeader("direct:foo", "B", "id", 123);
        template.sendBodyAndHeader("direct:foo", "C", "id", 123);

        assertMockEndpointsSatisfied();

        context.stopRoute("foo");

        resetMocks();

        context.startRoute("foo");

        getMockEndpoint("mock:aggregated").expectedBodiesReceived("D+E+F");

        template.sendBodyAndHeader("direct:foo", "D", "id", 123);
        template.sendBodyAndHeader("direct:foo", "E", "id", 123);
        template.sendBodyAndHeader("direct:foo", "F", "id", 123);

        assertMockEndpointsSatisfied();

        context.stop();
    }

    public void testAggregateShutdownCustomThreadPoolTest() throws Exception {
        assertEquals(false, myPool.isShutdown());

        getMockEndpoint("mock:aggregated").expectedBodiesReceived("A+B+C");

        template.sendBodyAndHeader("direct:bar", "A", "id", 123);
        template.sendBodyAndHeader("direct:bar", "B", "id", 123);
        template.sendBodyAndHeader("direct:bar", "C", "id", 123);

        assertMockEndpointsSatisfied();
        assertEquals(false, myPool.isShutdown());

        context.stopRoute("bar");
        assertEquals(false, myPool.isShutdown());

        resetMocks();

        context.startRoute("bar");
        assertEquals(false, myPool.isShutdown());

        getMockEndpoint("mock:aggregated").expectedBodiesReceived("D+E+F");

        template.sendBodyAndHeader("direct:bar", "D", "id", 123);
        template.sendBodyAndHeader("direct:bar", "E", "id", 123);
        template.sendBodyAndHeader("direct:bar", "F", "id", 123);

        assertMockEndpointsSatisfied();
        assertEquals(false, myPool.isShutdown());

        context.stop();
        // now it should be shutdown when CamelContext is stopped/shutdown
        assertEquals(true, myPool.isShutdown());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                myPool = context.getExecutorServiceManager().newDefaultThreadPool(this, "myPool");

                from("direct:foo").routeId("foo")
                    .aggregate(header("id"), new BodyInAggregatingStrategy()).completionSize(3)
                        .to("mock:aggregated");

                from("direct:bar").routeId("bar")
                    .aggregate(header("id"), new BodyInAggregatingStrategy()).executorService(myPool).completionSize(3)
                        .to("mock:aggregated");
            }
        };
    }
}