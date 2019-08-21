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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.BodyInAggregatingStrategy;
import org.junit.Test;

public class AggregateConcurrentPerCorrelationKeyTest extends ContextTestSupport {

    private final int size = 200;
    private final String uri = "direct:start";

    @Test
    public void testAggregateConcurrentPerCorrelationKey() throws Exception {
        ExecutorService service = Executors.newFixedThreadPool(20);
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            final int id = i % 5;
            final int count = i;
            tasks.add(new Callable<Object>() {
                public Object call() throws Exception {
                    template.sendBodyAndHeader(uri, "" + count, "id", id);
                    return null;
                }
            });
        }

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(5);

        // submit all tasks
        service.invokeAll(tasks);

        assertMockEndpointsSatisfied();
        service.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").aggregate(header("id"), new BodyInAggregatingStrategy()).completionSize(40).to("mock:result");
            }
        };
    }
}
