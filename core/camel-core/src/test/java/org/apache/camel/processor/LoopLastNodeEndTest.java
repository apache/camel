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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The statistics of a node start at the end time of the node before it, which is kept on the exchange. Each iteration
 * of a loop must clear that time, or the first output of the loop counts the predicate and the preparation of the
 * iteration (such as an onPrepare processor) as its own work.
 */
public class LoopLastNodeEndTest extends ContextTestSupport {

    private static final long END = 42;

    private final List<Long> seen = new CopyOnWriteArrayList<>();

    @Test
    public void testEachIterationStartsWithoutTheEndTimeOfTheLastIteration() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
        assertEquals(List.of(0L, 0L, 0L), seen);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .loop(3)
                        .process(e -> {
                            seen.add(e.getExchangeExtension().getLastNodeEndNanos());
                            // as the statistics of the last output of the loop would do
                            e.getExchangeExtension().setLastNodeEndNanos(END);
                        })
                        .end()
                        .to("mock:result");
            }
        };
    }
}
