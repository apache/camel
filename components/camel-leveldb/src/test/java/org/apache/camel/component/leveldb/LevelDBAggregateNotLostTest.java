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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisabledOnOs({ OS.AIX, OS.OTHER })
public class LevelDBAggregateNotLostTest extends LevelDBTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");
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

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        final List<Exchange> receivedExchanges = Awaitility.await().atMost(1, TimeUnit.SECONDS)
                .until(() -> getMockEndpoint("mock:aggregated").getReceivedExchanges(), Matchers.notNullValue());

        String exchangeId = receivedExchanges.get(0).getExchangeId();

        // the exchange should be in the completed repo where we should be able to find it
        final LevelDBFile levelDBFile = getRepo().getLevelDBFile();
        final LevelDBCamelCodec codec = new LevelDBCamelCodec(getRepo().getSerializer());
        byte[] bf = levelDBFile.getDb().get(keyBuilder("repo1-completed", exchangeId));

        // assert the exchange was not lost and we got all the information still
        assertNotNull(bf);
        Exchange completed = codec.unmarshallExchange(context, bf);
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
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .aggregate(header("id"), new StringAggregationStrategy())
                            .completionSize(5).aggregationRepository(getRepo())
                            .log("aggregated exchange id ${exchangeId} with ${body}")
                            .to("mock:aggregated")
                            // throw an exception to fail, which we then will loose this message
                            .throwException(new IllegalArgumentException("Damn"))
                            .to("mock:result")
                        .end();
            }
        };
    }
}
