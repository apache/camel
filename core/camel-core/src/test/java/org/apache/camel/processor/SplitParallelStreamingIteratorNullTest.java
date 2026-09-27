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

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that a streaming split waits for its parts and aggregates all of them when the iterator returns true from
 * hasNext() and then null from next() (see {@link SplitIteratorNullTest}), also with parallel processing.
 */
class SplitParallelStreamingIteratorNullTest extends ContextTestSupport {

    private final CountDownLatch lastHasNext = new CountDownLatch(1);

    @Test
    void testSplitStreamingParallel() {
        String out = template.requestBody("direct:parallel", new MyIterator(lastHasNext), String.class);

        // the parts complete in any order
        assertEquals("ABC", sorted(out), "The split should return the aggregated parts, but returned: " + out);
    }

    @Test
    void testSplitStreaming() {
        String out = template.requestBody("direct:sequential", new MyIterator(lastHasNext), String.class);

        assertEquals("ABC", out, "The split should return the aggregated parts, but returned: " + out);
    }

    private static String sorted(String s) {
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                AggregationStrategy concat = (oldExchange, newExchange) -> {
                    if (oldExchange == null) {
                        return newExchange;
                    }
                    oldExchange.getMessage().setBody(
                            oldExchange.getMessage().getBody(String.class) + newExchange.getMessage().getBody(String.class));
                    return oldExchange;
                };

                from("direct:parallel")
                        .split(body(), concat).streaming().parallelProcessing()
                            // the parts wait until the splitter has found out there are no more parts
                            .process(e -> assertTrue(lastHasNext.await(10, TimeUnit.SECONDS)))
                        .end();

                from("direct:sequential")
                        .split(body(), concat).streaming()
                            .log("${body}")
                        .end();
            }
        };
    }

    private static class MyIterator implements Iterator<String> {

        private final CountDownLatch lastHasNext;
        private int count = 4;

        MyIterator(CountDownLatch lastHasNext) {
            this.lastHasNext = lastHasNext;
        }

        @Override
        public boolean hasNext() {
            // we return true one extra time, and cause next to return null
            boolean answer = count > 0;
            if (!answer) {
                lastHasNext.countDown();
            }
            return answer;
        }

        @Override
        public String next() {
            count--;
            if (count == 0) {
                return null;
            } else if (count == 1) {
                return "C";
            } else if (count == 2) {
                return "B";
            } else {
                return "A";
            }
        }
    }
}
