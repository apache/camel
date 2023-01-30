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
package org.apache.camel.processor.aggregator;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;
import org.junit.jupiter.api.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.apache.camel.Exchange.SPLIT_COMPLETE;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SplitAggregateStackOverflowIssueTest extends ContextTestSupport {

    private final AtomicInteger count = new AtomicInteger();

    @Test
    public void testStackoverflow() throws Exception {
        int size = 50000;

        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(size / 10);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            sb.append("Line #").append(i);
            sb.append("\n");
        }

        template.sendBody("direct:start", sb);

        MockEndpoint.assertIsSatisfied(60, SECONDS, result);

        // the stackframe is 48 at this time of coding but can grow a little bit over time so lets just assume 70
        // is fine, as if we get the endless bug again then this test fails before and this counter goes to 1024
        assertTrue(count.get() < 70, "Stackframe must not be too high, was " + count.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                from("direct:start")
                        .split().tokenize("\n").streaming()
                        .to("log:input?groupSize=100")
                        .process(e -> {
                            if (e.getProperty(Exchange.SPLIT_INDEX, 0, int.class) % 1000 == 0) {
                                int frames = (int) Stream.of(Thread.currentThread().getStackTrace())
                                        .filter(st -> !st.getClassName().startsWith("org.junit."))
                                        .count();
                                count.set(frames);
                                log.info("Stackframe: {}", frames);
                            }
                        })
                        .aggregate(constant("foo"), new GroupedBodyAggregationStrategy())
                        .completeAllOnStop()
                        .eagerCheckCompletion()
                        .completionSize(10)
                        .completionTimeout(SECONDS.toMillis(5))
                        .completionPredicate(exchangeProperty(SPLIT_COMPLETE))
                        .to("log:result?groupSize=100", "mock:result");
            }
        };
    }
}
