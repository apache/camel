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
package org.apache.camel.processor.aggregate.jdbc;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbcAggregateLoadAndRecoverTest extends AbstractJdbcAggregationTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcAggregateLoadAndRecoverTest.class);
    private static final int SIZE = 200;
    private static AtomicInteger counter = new AtomicInteger();

    @Test
    public void testLoadAndRecoverJdbcAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(SIZE / 10);
        mock.setResultWaitTime(50 * 1000);

        LOG.info("Staring to send " + SIZE + " messages.");

        for (int i = 0; i < SIZE; i++) {
            final int value = 1;
            char id = 'A';
            Map<String, Object> headers = new HashMap<>();
            headers.put("id", id);
            headers.put("seq", i);
            LOG.debug("Sending {} with id {}", value, id);
            template.sendBodyAndHeaders("seda:start?size=" + SIZE, value, headers);
            // simulate a little delay
            Thread.sleep(3);
        }

        LOG.info("Sending all " + SIZE + " message done. Now waiting for aggregation to complete.");

        assertMockEndpointsSatisfied();

        int recovered = 0;
        for (Exchange exchange : mock.getReceivedExchanges()) {
            if (exchange.getIn().getHeader(Exchange.REDELIVERED) != null) {
                recovered++;
            }
        }
        int expected = SIZE / 10 / 10;
        assertEquals("There should be " + expected + " recovered", expected, recovered);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("seda:start?size=" + SIZE)
                        .to("log:input?groupSize=500")
                        .aggregate(header("id"), new MyAggregationStrategy())
                        .aggregationRepository(repo)
                        .completionSize(10)
                        .to("log:output?showHeaders=true")
                                // have every 10th exchange fail which should then be recovered
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                //Avoid same message to be discarded twice
                                if (exchange.getIn().getHeader(Exchange.REDELIVERED) == null) {
                                    int num = counter.incrementAndGet();
                                    if (num % 10 == 0) {
                                        throw new IllegalStateException("Failed for num " + num);
                                    }
                                }
                            }
                        })
                        .to("mock:result")
                        .end();
            }
        };
    }
}
