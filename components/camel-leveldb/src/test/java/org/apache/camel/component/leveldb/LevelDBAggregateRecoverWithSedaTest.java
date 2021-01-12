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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.params.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.apache.camel.test.junit5.TestSupport.deleteDirectory;

public class LevelDBAggregateRecoverWithSedaTest extends LevelDBTestSupport {

    private static Map<SerializerType, AtomicInteger> counters = new ConcurrentHashMap();

    private static AtomicInteger getCounter(SerializerType serializerType) {
        AtomicInteger counter = counters.get(serializerType);
        if (counter == null) {
            counter = new AtomicInteger();
            counters.put(serializerType, counter);
        }
        return counter;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        deleteDirectory("target/data");

        super.setUp();
    }

    @Test
    public void testLevelDBAggregateRecoverWithSeda() throws Exception {
        // should fail the first 2 times and then recover
        getMockEndpoint("mock:aggregated").expectedMessageCount(3);
        getMockEndpoint("mock:result").expectedBodiesReceived("ABCDE");
        // should be marked as redelivered
        getMockEndpoint("mock:result").message(0).header(Exchange.REDELIVERED).isEqualTo(Boolean.TRUE);
        // on the 2nd redelivery attempt we success
        getMockEndpoint("mock:result").message(0).header(Exchange.REDELIVERY_COUNTER).isEqualTo(2);

        template.sendBodyAndHeader("direct:start", "A", "id", 123);
        template.sendBodyAndHeader("direct:start", "B", "id", 123);
        template.sendBodyAndHeader("direct:start", "C", "id", 123);
        template.sendBodyAndHeader("direct:start", "D", "id", 123);
        template.sendBodyAndHeader("direct:start", "E", "id", 123);

        assertMockEndpointsSatisfied(30, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable recovery
                LevelDBAggregationRepository repo = getRepo();
                repo.setUseRecovery(true);
                // check faster
                repo.setRecoveryInterval(500, TimeUnit.MILLISECONDS);

                from("direct:start")
                        .aggregate(header("id"), new StringAggregationStrategy())
                            .completionSize(5).aggregationRepository(repo)
                            .to("mock:aggregated")
                            .to("seda:foo")
                        .end();

                // should be able to recover when we send over SEDA as its a OnCompletion
                // which confirms the exchange when its complete.
                from("seda:foo")
                        .delay(1000)
                        // simulate errors the first two times
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                int count = getCounter(getSerializerType()).incrementAndGet();
                                if (count <= 2) {
                                    throw new IllegalArgumentException("Damn");
                                }
                            }
                        })
                        .to("mock:result");
            }
        };
    }
}
