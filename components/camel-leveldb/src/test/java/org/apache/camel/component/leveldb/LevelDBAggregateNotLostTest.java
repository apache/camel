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

import java.util.concurrent.TimeUnit;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.fusesource.hawtbuf.Buffer;
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.leveldb.LevelDBAggregationRepository.keyBuilder;

public class LevelDBAggregateNotLostTest extends CamelTestSupport {

    private LevelDBAggregationRepository repo;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        repo = new LevelDBAggregationRepository("repo1", "target/data/leveldb.dat");
        super.setUp();
    }

    @Test
    public void testLevelDBAggregateNotLost() throws Exception {
        getMockEndpoint("mock:aggregated").expectedBodiesReceived("ABCDE");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "D", "id", 123);
        template.sendBodyAndHeader("direct:start", "E", "id", 123);

        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);

        Thread.sleep(1000);

        String exchangeId = getMockEndpoint("mock:aggregated").getReceivedExchanges().get(0).getExchangeId();

        // the exchange should be in the completed repo where we should be able to find it
        final LevelDBFile levelDBFile = repo.getLevelDBFile();
        final LevelDBCamelCodec codec = new LevelDBCamelCodec();
        byte[] bf = levelDBFile.getDb().get(keyBuilder("repo1-completed", exchangeId));

        // assert the exchange was not lost and we got all the information still
        assertNotNull(bf);
        Exchange completed = codec.unmarshallExchange(context, new Buffer(bf));
        assertNotNull(completed);
        // should retain the exchange id
        assertEquals(exchangeId, completed.getExchangeId());
        assertEquals("ABCDE", completed.getIn().getBody());
        assertEquals(123, completed.getIn().getHeader("id"));
        assertEquals("size", completed.getProperty(Exchange.AGGREGATED_COMPLETED_BY));
        assertEquals(5, completed.getProperty(Exchange.AGGREGATED_SIZE));
        // will store correlation keys as String
        assertEquals("123", completed.getProperty(Exchange.AGGREGATED_CORRELATION_KEY));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .aggregate(header("id"), new MyAggregationStrategy())
                        .completionSize(5).aggregationRepository(repo)
                        .log("aggregated exchange id ${exchangeId} with ${body}")
                        .to("mock:aggregated")
                        // throw an exception to fail, which we then will loose this message
                        .throwException(new IllegalArgumentException("Damn"))
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
