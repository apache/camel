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
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

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
