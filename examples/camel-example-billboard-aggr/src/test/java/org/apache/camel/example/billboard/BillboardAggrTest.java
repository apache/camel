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
package org.apache.camel.example.billboard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.BindyType;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class BillboardAggrTest extends CamelTestSupport {

    private static final String BASEPATH = System.getProperty("user.dir") + "/src/test/data";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ctx.disableJMX();
        return ctx;
    }

    @Override
    protected int getShutdownTimeout() {
        return 300;
    }

    @Test
    public void test() throws Exception {
        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedMessageCount(1);
        mock.assertIsSatisfied();

        Map<String, Integer> top20 = ((MyAggregationStrategy) 
            mock.getReceivedExchanges().get(0).getIn().getHeader("myAggregation")).getTop20Artists();
        top20.forEach((k, v) -> log.info("{}, {}", k, v));
        assertEquals(20, top20.size());
        assertEquals(35, (int) top20.get("madonna"));
        assertEquals(26, (int) top20.get("elton john"));
        assertEquals(17, (int) top20.get("the beatles"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:" + BASEPATH + "?noop=true&idempotent=true")
                    .split(body().tokenize("\n")).streaming().parallelProcessing()
                        // skip first line with headers
                        .choice().when(simple("${exchangeProperty.CamelSplitIndex} > 0"))
                            .doTry()
                                .unmarshal().bindy(BindyType.Csv, SongRecord.class)
                                .to("seda:aggregate")
                            .doCatch(Exception.class)
                                // malformed record trace
                                .setBody(simple("${exchangeProperty.CamelSplitIndex}:${body}"))
                                .transform(body().append("\n")) 
                                .to("file:" + BASEPATH + "?fileName=waste.log&fileExist=append")
                            .end();

                from("seda:aggregate?concurrentConsumers=10")
                    .bean(MyAggregationStrategy.class, "setArtistHeader")
                    .aggregate(new MyAggregationStrategy()).header("artist")
                        .completionPredicate(header("CamelSplitComplete").isEqualTo(true))
                    .to("mock:result");
            }
        };
    }

    public static class MyAggregationStrategy implements AggregationStrategy {
        private static Map<String, Integer> map = new ConcurrentHashMap<>();

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            Message newIn = newExchange.getIn();
            String artist = (String) newIn.getHeader("artist");
            if (map.containsKey(artist)) {
                map.put(artist, map.get(artist) + 1);
            } else {
                map.put(artist, 1);
            }
            newIn.setHeader("myAggregation", this);
            return newExchange;
        }

        public void setArtistHeader(Exchange exchange, SongRecord song) {
            exchange.getMessage().setHeader("artist", song.getArtist());
        }

        public Map<String, Integer> getTop20Artists() {
            return map.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(20)
                .collect(Collectors.toMap(Map.Entry::getKey, 
                    Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        }
    }

}
