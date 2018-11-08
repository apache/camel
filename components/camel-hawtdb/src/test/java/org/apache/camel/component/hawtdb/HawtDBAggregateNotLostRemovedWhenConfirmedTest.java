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
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.fusesource.hawtbuf.Buffer;
import org.fusesource.hawtdb.api.Index;
import org.fusesource.hawtdb.api.Transaction;
import org.junit.Before;
import org.junit.Test;

public class HawtDBAggregateNotLostRemovedWhenConfirmedTest extends CamelTestSupport {

    private HawtDBAggregationRepository repo;

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data");
        repo = new HawtDBAggregationRepository("repo1", "target/data/hawtdb.dat");
        super.setUp();
    }

    @Test
    public void testHawtDBAggregateNotLostRemovedWhenConfirmed() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("ABCDE");

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "D", "id", 123);
        template.sendBodyAndHeader("direct:start", "E", "id", 123);

        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);

        Thread.sleep(1000);

        String exchangeId = getMockEndpoint("mock:result").getReceivedExchanges().get(0).getExchangeId();

        // the exchange should NOT be in the completed repo as it was confirmed
        final HawtDBFile hawtDBFile = repo.getHawtDBFile();
        final HawtDBCamelCodec codec = new HawtDBCamelCodec();
        final Buffer confirmKeyBuffer = codec.marshallKey(exchangeId);
        Buffer bf = hawtDBFile.execute(new Work<Buffer>() {
            public Buffer execute(Transaction tx) {
                Index<Buffer, Buffer> index = hawtDBFile.getRepositoryIndex(tx, "repo1-completed", false);
                return index.get(confirmKeyBuffer);
            }
        });

        // assert the exchange was deleted
        assertNull(bf);
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
            String body1 = oldExchange.getIn().getBody(String.class);
            String body2 = newExchange.getIn().getBody(String.class);

            oldExchange.getIn().setBody(body1 + body2);
            return oldExchange;
        }
    }
}