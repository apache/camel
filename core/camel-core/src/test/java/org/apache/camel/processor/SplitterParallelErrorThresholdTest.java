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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.SplitResult;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for error threshold features with parallel processing.
 */
class SplitterParallelErrorThresholdTest extends ContextTestSupport {

    @Test
    void testMaxFailedRecordsWithParallel() throws Exception {
        // 20 items, every 4th fails (indices 3, 7, 11, 15, 19)
        // maxFailedRecords=3 → should abort after 3rd failure
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(i % 4 == 3 ? "FAIL" : "item-" + i);
        }

        MockEndpoint mock = getMockEndpoint("mock:parallel-max");
        mock.expectedMinimumMessageCount(0);

        Exchange result = template.send("direct:parallel-max",
                e -> e.getIn().setBody(items));

        mock.assertIsSatisfied();

        assertNotNull(result.getException(), "Should have exception when maxFailedRecords exceeded");

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set");
        assertTrue(splitResult.isAborted());
        assertTrue(splitResult.getFailureCount() >= 3, "Should have at least 3 failures");
    }

    @Test
    void testMaxFailedRecordsAllSucceedWithParallel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:parallel-max");
        mock.expectedMessageCount(10);

        List<String> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add("item-" + i);
        }

        Exchange result = template.send("direct:parallel-max",
                e -> e.getIn().setBody(items));

        mock.assertIsSatisfied();

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);
        assertEquals(10, splitResult.getTotalItems());
        assertEquals(0, splitResult.getFailureCount());
        assertFalse(splitResult.isAborted());
    }

    @Test
    void testErrorThresholdWithParallelHighFailureRate() throws Exception {
        // 10 items, first 8 fail → very high failure rate
        // errorThreshold=0.5 → should abort early
        List<String> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i < 8 ? "FAIL" : "item-" + i);
        }

        MockEndpoint mock = getMockEndpoint("mock:parallel-threshold");
        mock.expectedMinimumMessageCount(0);

        Exchange result = template.send("direct:parallel-threshold",
                e -> e.getIn().setBody(items));

        mock.assertIsSatisfied();

        assertNotNull(result.getException(), "Should abort with high failure rate");

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);
        assertTrue(splitResult.isAborted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:parallel-max")
                        .split(body()).parallelProcessing().maxFailedRecords(3)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure: " + body);
                            }
                        })
                        .to("mock:parallel-max");

                from("direct:parallel-threshold")
                        .split(body()).parallelProcessing().errorThreshold(0.5)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure: " + body);
                            }
                        })
                        .to("mock:parallel-threshold");
            }
        };
    }
}
