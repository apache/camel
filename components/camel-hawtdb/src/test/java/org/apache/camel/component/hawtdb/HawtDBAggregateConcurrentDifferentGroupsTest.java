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
package org.apache.camel.component.hawtdb;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version 
 */
public class HawtDBAggregateConcurrentDifferentGroupsTest extends CamelTestSupport {

    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testNoConcurrentProducers() throws Exception {
        doSendMessages(2, 1);
    }

    @Test
    public void testConcurrentProducers() throws Exception {
        doSendMessages(10, 5);
    }

    @Test
    public void testMoreConcurrentProducers() throws Exception {
        doSendMessages(50, 10);
    }

    private void doSendMessages(int files, int poolSize) throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:aggregated");
        mock.expectedMessageCount(2);
        mock.setResultWaitTime(30 * 1000L);

        ExecutorService executor = Executors.newFixedThreadPool(poolSize);
        for (int i = 0; i < files; i++) {
            final int index = i;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    String id = index % 2 == 0 ? "A" : "B";
                    template.sendBodyAndHeader("direct:start", index, "id", id);
                    // simulate a little delay
                    Thread.sleep(3);
                    return null;
                }
            });
        }

        assertMockEndpointsSatisfied();
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                HawtDBAggregationRepository repo = new HawtDBAggregationRepository("repo1", "target/data/hawtdb.dat");

                from("direct:start")
                    .aggregate(header("id"), new MyAggregationStrategy())
                        .completionTimeout(1000).aggregationRepository(repo)
                        .to("mock:aggregated");
            }
        };
    }

    private static class MyAggregationStrategy implements AggregationStrategy {

        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }

}