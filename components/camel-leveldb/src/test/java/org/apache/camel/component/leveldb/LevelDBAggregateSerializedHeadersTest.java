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
package org.apache.camel.component.leveldb;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.util.HeaderDto;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LevelDBAggregateSerializedHeadersTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(LevelDBAggregateSerializedHeadersTest.class);
    private static final int SIZE = 500;
    private LevelDBAggregationRepository repo;

    @Before
    @Override
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        repo = new LevelDBAggregationRepository("repo1", "target/data/leveldb.dat");
        repo.setAllowSerializedHeaders(true);
        super.setUp();
    }

    @Test
    public void testLoadTestLevelDBAggregate() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.setResultWaitTime(50 * 1000);

        LOG.info("Staring to send " + SIZE + " messages.");

        for (int i = 0; i < SIZE; i++) {
            final int value = 1;
            HeaderDto headerDto = new HeaderDto("test", "company", 1);
            char id = 'A';
            LOG.debug("Sending {} with id {}", value, id);
            Map<String, Object> headers = new HashMap<>();
            headers.put("id", headerDto);
            template.sendBodyAndHeaders("seda:start?size=" + SIZE, value, headers);
        }

        LOG.info("Sending all " + SIZE + " message done. Now waiting for aggregation to complete.");

        assertMockEndpointsSatisfied();
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
                        .completionSize(SIZE)
                        .to("log:output?showHeaders=true")
                        .to("mock:result")
                    .end();
            }
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {

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
