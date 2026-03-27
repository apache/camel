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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitterErrorThresholdTest extends ContextTestSupport {

    @Test
    void testErrorThresholdStopsWhenRatioExceeded() throws Exception {
        // items: FAIL, FAIL, a, b, c
        // errorThreshold=0.5 (50%)
        // item 0: FAIL → failCount=1, ratio=1/1=100% ≥ 50% → stop
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMinimumMessageCount(0);

        Exchange result = template.send("direct:start",
                e -> e.getIn().setBody(Arrays.asList("FAIL", "FAIL", "a", "b", "c")));

        mock.assertIsSatisfied();

        assertNotNull(result.getException(), "Should have an exception when error threshold exceeded");
    }

    @Test
    void testErrorThresholdBelowRatio() throws Exception {
        // items: a, b, FAIL, c, d, e, f, g, h, i
        // errorThreshold=0.5 (50%)
        // item 0: a → ok
        // item 1: b → ok
        // item 2: FAIL → failCount=1, ratio=1/3=33% < 50% → continue
        // items 3-9: ok
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(9); // 10 items minus 1 failure

        template.sendBody("direct:start", Arrays.asList("a", "b", "FAIL", "c", "d", "e", "f", "g", "h", "i"));

        mock.assertIsSatisfied();
    }

    @Test
    void testErrorThresholdAllSucceed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(5);

        template.sendBody("direct:start", Arrays.asList("a", "b", "c", "d", "e"));

        mock.assertIsSatisfied();
    }

    @Test
    void testStopOnExceptionAndErrorThresholdAreMutuallyExclusive() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:invalid")
                            .split(body()).stopOnException().errorThreshold(0.5)
                            .to("mock:invalid");
                }
            });
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException
                    || e instanceof IllegalArgumentException,
                    "Should throw IllegalArgumentException");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).errorThreshold(0.5)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure for: " + body);
                            }
                        })
                        .to("mock:split");
            }
        };
    }
}
