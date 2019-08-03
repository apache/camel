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
package org.apache.camel.component.leveldb;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LevelDBAggregateLoadConcurrentTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(LevelDBAggregateLoadConcurrentTest.class);
    private static final char[] KEYS = new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'};
    private static final int SIZE = 500;

    @Before
    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testLoadTestLevelDBAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);
        mock.setResultWaitTime(50 * 1000);

        ExecutorService executor = Executors.newFixedThreadPool(10);

        LOG.info("Staring to send " + SIZE + " messages.");

        for (int i = 0; i < SIZE; i++) {
            final int value = 1;
            final int key = i % 10;
            executor.submit(new Callable<Object>() {
                public Object call() throws Exception {
                    char id = KEYS[key];
                    LOG.debug("Sending {} with id {}", value, id);
                    template.sendBodyAndHeader("direct:start", value, "id", "" + id);
                    // simulate a little delay
                    Thread.sleep(3);
                    return null;
                }
            });
        }

        LOG.info("Sending all " + SIZE + " message done. Now waiting for aggregation to complete.");

        assertMockEndpointsSatisfied();
        executor.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                LevelDBAggregationRepository repo = new LevelDBAggregationRepository("repo1", "target/data/leveldb.dat");

                from("direct:start")
                    .to("log:input?groupSize=500")
                    .aggregate(header("id"), new MyAggregationStrategy())
                        .aggregationRepository(repo)
                        .completionSize(SIZE / 10)
                        .to("log:output?showHeaders=true")
                        .to("mock:result")
                    .end();
            }
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }

            Integer body1 = oldExchange.getIn().getBody(Integer.class);
            Integer body2 = newExchange.getIn().getBody(Integer.class);
            int sum = body1 + body2;

            oldExchange.getIn().setBody(sum);
            return oldExchange;
        }
    }
}
