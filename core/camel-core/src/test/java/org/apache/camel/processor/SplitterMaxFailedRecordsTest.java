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

class SplitterMaxFailedRecordsTest extends ContextTestSupport {

    @Test
    void testMaxFailedRecordsStopsAfterThreshold() throws Exception {
        // items: a, FAIL, b, FAIL, c, FAIL, d
        // maxFailedRecords=2, so processing should stop after the 2nd failure
        MockEndpoint mock = getMockEndpoint("mock:split");
        // With maxFailedRecords=2: items a (ok), FAIL (fail #1, continue), b (ok), FAIL (fail #2, stop)
        // Items c, FAIL, d should NOT be processed
        mock.expectedMinimumMessageCount(2); // at least a, b

        Exchange result = template.send("direct:start",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "b", "FAIL", "c", "FAIL", "d")));

        mock.assertIsSatisfied();

        // the exchange should have an exception because threshold was exceeded
        assertNotNull(result.getException(), "Should have an exception when max failed records exceeded");

        // verify that not all items were processed
        assertTrue(mock.getReceivedCounter() < 7, "Should have stopped before processing all items");
    }

    @Test
    void testMaxFailedRecordsAllSucceed() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(4);

        template.sendBody("direct:start", Arrays.asList("a", "b", "c", "d"));

        mock.assertIsSatisfied();
    }

    @Test
    void testMaxFailedRecordsSingleFailure() throws Exception {
        // maxFailedRecords=2, only 1 failure, should process all items
        // only 3 items reach mock:split (the failed one throws before reaching the endpoint)
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(3);

        template.sendBody("direct:start", Arrays.asList("a", "FAIL", "b", "c"));

        mock.assertIsSatisfied();
    }

    @Test
    void testStopOnExceptionAndMaxFailedRecordsAreMutuallyExclusive() throws Exception {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:invalid")
                            .split(body()).stopOnException().maxFailedRecords(3)
                            .to("mock:invalid");
                }
            });
            // should not reach here
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
                        .split(body()).maxFailedRecords(2)
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
