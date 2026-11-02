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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.SplitResult;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests that the Splitter's SplitFailureTracker does not leak into nested splits and silently swallow their failures.
 */
class SplitterNestedFailureTrackerLeakTest extends ContextTestSupport {

    @Test
    void testInnerSplitFailureNotSwallowedByOuterTracker() throws Exception {
        // Outer split has maxFailedRecords, inner split does not.
        // The inner split item throws — this must propagate as a failure,
        // not be silently swallowed by the outer tracker.
        MockEndpoint mock = getMockEndpoint("mock:inner");
        mock.expectedMinimumMessageCount(0);

        Exchange result = template.send("direct:outerMaxFailed",
                e -> e.getIn().setBody(List.of(List.of("a", "FAIL_INNER"))));

        mock.assertIsSatisfied();

        assertNotNull(result.getException(),
                "Inner split failure should propagate — must not be swallowed by the leaked outer tracker");
    }

    @Test
    void testInnerSplitSuccessStillWorks() throws Exception {
        // When inner split items all succeed, nesting with outer maxFailedRecords should work fine.
        MockEndpoint mock = getMockEndpoint("mock:inner");
        mock.expectedMessageCount(2);

        Exchange result = template.send("direct:outerMaxFailed",
                e -> e.getIn().setBody(List.of(List.of("a", "b"))));

        mock.assertIsSatisfied();
        assertNull(result.getException(), "No failures — exchange should succeed");
    }

    @Test
    void testBothSplitsWithThresholds() throws Exception {
        // Both outer and inner splits have maxFailedRecords.
        // Inner failure within its threshold should not corrupt the outer tracker.
        MockEndpoint mock = getMockEndpoint("mock:innerThreshold");
        mock.expectedMinimumMessageCount(1);

        Exchange result = template.send("direct:bothThresholds",
                e -> e.getIn().setBody(List.of(List.of("a", "FAIL_INNER", "b"))));

        mock.assertIsSatisfied();
        assertNull(result.getException(),
                "Inner split absorbs its failure within threshold, outer should succeed");
    }

    @Test
    void testInnerSplitWithoutThresholdIsNotCountedByOuterSplit() {
        // the 3 items of each inner split are not items of the outer split
        Exchange result = template.send("direct:outerMaxFailed",
                e -> e.getIn().setBody(List.of(List.of("a", "b", "c"), List.of("d", "e", "f"))));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertEquals(2, splitResult.getTotalItems());
        assertEquals(2, splitResult.getProcessedItems());
    }

    @Test
    void testInnerSplitWithThresholdDoesNotHideOuterItems() {
        // an inner split with its own tracker removes it from the exchange of the outer item when it completes
        Exchange result = template.send("direct:bothThresholds",
                e -> e.getIn().setBody(List.of(List.of("a", "b", "c"), List.of("d", "e", "f"))));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertEquals(2, splitResult.getTotalItems());
        assertEquals(2, splitResult.getProcessedItems());
    }

    @Test
    void testOuterErrorThresholdIsNotDilutedByInnerItems() throws Exception {
        // 1 failure of 2 outer items reaches the threshold of 0.5, so the third outer item must not run. Counting the
        // 9 items of the inner split would make that 1 of 11, and the outer split would continue
        MockEndpoint mock = getMockEndpoint("mock:outerRatio");
        mock.expectedMessageCount(9);

        template.send("direct:outerRatio", e -> e.getIn().setBody(List.of(
                List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"), List.of("FAIL_INNER"), List.of("not reached"))));

        mock.assertIsSatisfied();
    }

    @Test
    void testOuterErrorThresholdWorksWithInnerThreshold() throws Exception {
        // without processed items the ratio is never computed, so the outer split would never stop
        MockEndpoint mock = getMockEndpoint("mock:outerRatioInnerThreshold");
        mock.expectedMessageCount(1);

        template.send("direct:outerRatioInnerThreshold", e -> e.getIn().setBody(List.of(
                List.of("1"), List.of("FAIL_INNER"), List.of("not reached"))));

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Outer split with maxFailedRecords, inner split plain (no thresholds)
                from("direct:outerMaxFailed")
                        .split(body()).maxFailedRecords(1)
                        .split(body())
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL_INNER".equals(body)) {
                                throw new IllegalArgumentException("Inner failure: " + body);
                            }
                        })
                        .to("mock:inner")
                        .end()
                        .end();

                from("direct:outerRatio")
                        .split(body()).errorThreshold(0.5)
                        .split(body())
                        .process(exchange -> {
                            if ("FAIL_INNER".equals(exchange.getIn().getBody(String.class))) {
                                throw new IllegalArgumentException("Inner failure");
                            }
                        })
                        .to("mock:outerRatio")
                        .end()
                        .end();

                from("direct:outerRatioInnerThreshold")
                        .split(body()).errorThreshold(0.5)
                        .split(body()).maxFailedRecords(1)
                        .process(exchange -> {
                            if ("FAIL_INNER".equals(exchange.getIn().getBody(String.class))) {
                                throw new IllegalArgumentException("Inner failure");
                            }
                        })
                        .to("mock:outerRatioInnerThreshold")
                        .end()
                        .end();

                // Both outer and inner with maxFailedRecords
                from("direct:bothThresholds")
                        .split(body()).maxFailedRecords(1)
                        .split(body()).maxFailedRecords(2)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL_INNER".equals(body)) {
                                throw new IllegalArgumentException("Inner failure: " + body);
                            }
                        })
                        .to("mock:innerThreshold")
                        .end()
                        .end();
            }
        };
    }
}
