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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.params.Test;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.component.leveldb.LevelDBAggregationRepository.keyBuilder;
import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBAggregateNotLostRemovedWhenConfirmedTest extends LevelDBTestSupport {

    private LevelDBAggregationRepository repo;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        repo = new LevelDBAggregationRepository("repo1", "target/data/leveldb.dat");
        super.setUp();
    }

    @Test
    public void testLevelDBAggregateNotLostRemovedWhenConfirmed() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("ABCDE");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "D", "id", 123);
        template.sendBodyAndHeader("direct:start", "E", "id", 123);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        final List<Exchange> receivedExchanges = Awaitility.await().atMost(1, TimeUnit.SECONDS)
                .until(() -> getMockEndpoint("mock:result").getReceivedExchanges(), Matchers.notNullValue());

        String exchangeId = receivedExchanges.get(0).getExchangeId();

        // the exchange should NOT be in the completed repo as it was confirmed
        final LevelDBFile levelDBFile = repo.getLevelDBFile();
        byte[] bf = levelDBFile.getDb().get(keyBuilder("repo1-completed", exchangeId));

        // assert the exchange was deleted
        assertNull(bf);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new MyAggregationStrategy())
                            .completionSize(5).aggregationRepository(repo)
                            .log("aggregated exchange id ${exchangeId} with ${body}")
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
            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }
}
