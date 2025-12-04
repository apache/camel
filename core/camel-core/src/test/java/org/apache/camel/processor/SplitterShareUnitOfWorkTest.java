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

package org.apache.camel.processor;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SplitterShareUnitOfWorkTest extends ContextTestSupport {

    private final List<UnitOfWork> uows = new ArrayList<>();
    private final List<UnitOfWork> doneUows = new ArrayList<>();
    private final List<String> doneBodies = new ArrayList<>();

    @Test
    public void testShareUnitOfWork() throws Exception {
        getMockEndpoint("mock:line").expectedBodiesReceived("A", "B", "C");
        getMockEndpoint("mock:result").expectedBodiesReceived("A+B+C");

        template.sendBody("direct:start", "A,B,C");

        assertMockEndpointsSatisfied();

        Assertions.assertEquals(3, uows.size());

        // all in-flight uows should be the same
        Assertions.assertSame(uows.get(0), uows.get(1));
        Assertions.assertSame(uows.get(1), uows.get(2));
        Assertions.assertSame(uows.get(2), uows.get(0));

        // and done uow should be the same
        Assertions.assertSame(uows.get(0), doneUows.get(0));
        Assertions.assertSame(uows.get(1), doneUows.get(1));
        Assertions.assertSame(uows.get(2), doneUows.get(2));

        // uow is done after the entire route so the exchange body is the output from the aggregation strategy
        Assertions.assertEquals("A+B+C", doneBodies.get(0));
        Assertions.assertEquals("A+B+C", doneBodies.get(1));
        Assertions.assertEquals("A+B+C", doneBodies.get(2));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .split(body(), new MyStrategy())
                        .shareUnitOfWork()
                        .process(e -> {
                            var u = e.getUnitOfWork();
                            uows.add(u);
                            u.addSynchronization(new SynchronizationAdapter() {
                                @Override
                                public void onDone(Exchange exchange) {
                                    var b = exchange.getMessage().getBody(String.class);
                                    doneBodies.add(b);
                                    var u = exchange.getUnitOfWork();
                                    doneUows.add(u);

                                    // should only be invoked after all is complete (3 line and 1 result)
                                    Assertions.assertEquals(
                                            3, getMockEndpoint("mock:line").getReceivedCounter());
                                    Assertions.assertEquals(
                                            1, getMockEndpoint("mock:result").getReceivedCounter());
                                }
                            });
                        })
                        .to("mock:line")
                        .end()
                        .to("mock:result");
            }
        };
    }

    private static class MyStrategy implements AggregationStrategy {

        @Override
        public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
            if (oldExchange == null) {
                return newExchange;
            }
            String body =
                    oldExchange.getIn().getBody() + "+" + newExchange.getIn().getBody();
            oldExchange.getIn().setBody(body);
            return oldExchange;
        }
    }
}
