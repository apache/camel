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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBAggregateLoadAndRecoverTest extends LevelDBTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(LevelDBAggregateLoadAndRecoverTest.class);
    private static final int SIZE = 200;
    private static AtomicInteger counter = new AtomicInteger();

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        super.setUp();
    }

    @Test
    public void testLoadAndRecoverLevelDBAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(SIZE / 10);
        mock.setResultWaitTime(50 * 1000);

        LOG.info("Starting to send {} messages.", SIZE);

        for (int i = 0; i < SIZE; i++) {
            final int value = 1;
            char id = 'A';
            Map<String, Object> headers = new HashMap<>();
            headers.put("id", id);
            headers.put("seq", i);
            LOG.debug("Sending {} with id {}", value, id);
            template.sendBodyAndHeaders("seda:start", value, headers);
            // simulate a little delay
            Thread.sleep(5);
        }

        LOG.info("Sending all {} message done. Now waiting for aggregation to complete.", SIZE);

        MockEndpoint.assertIsSatisfied(context);

        int recovered = 0;
        for (Exchange exchange : mock.getReceivedExchanges()) {
            if (exchange.getIn().getHeader(Exchange.REDELIVERED) != null) {
                recovered++;
            }
        }
        int expected = SIZE / 10 / 10;
        int delta = Math.abs(expected - recovered);
        if (delta == 0) {
            assertEquals(expected, recovered, "There should be " + expected + " recovered");
        } else {
            assertTrue(delta < 3, "We expected " + expected + " recovered but the delta is within accepted range " + delta);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                LevelDBAggregationRepository repo = getRepo();
                repo.setUseRecovery(true);
                // for faster unit testing
                repo.setRecoveryInterval(500);

                from("seda:start?size=" + SIZE)
                        .to("log:input?groupSize=500")
                        .aggregate(header("id"), new MyAggregationStrategy())
                        .aggregationRepository(repo)
                        .completionSize(10)
                        .to("log:output?showHeaders=true")
                        // have every 10th exchange fail which should then be recovered
                        .process(new Processor() {
                            public void process(Exchange exchange) {
                                int num = counter.incrementAndGet();
                                if (num % 10 == 0) {
                                    throw new IllegalStateException("Failed for num " + num);
                                }
                            }
                        })
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
