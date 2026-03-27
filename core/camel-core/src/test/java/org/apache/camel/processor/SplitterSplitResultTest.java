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
import org.apache.camel.SplitResult;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitterSplitResultTest extends ContextTestSupport {

    @Test
    void testSplitResultWithFailures() throws Exception {
        // items: a, FAIL, b, FAIL, c — maxFailedRecords=5 so all items are processed
        Exchange result = template.send("direct:tolerant",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "b", "FAIL", "c")));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set as exchange property");

        assertEquals(5, splitResult.getTotalItems());
        assertEquals(2, splitResult.getFailureCount());
        assertEquals(3, splitResult.getSuccessCount());
        assertFalse(splitResult.isAborted());

        // verify failure details
        assertEquals(2, splitResult.getFailures().size());
        assertEquals(1, splitResult.getFailures().get(0).index());
        assertEquals(3, splitResult.getFailures().get(1).index());
        assertNotNull(splitResult.getFailures().get(0).exception());
    }

    @Test
    void testSplitResultWhenAborted() throws Exception {
        // items: FAIL, FAIL, a, b — maxFailedRecords=2 so aborted after 2nd failure
        Exchange result = template.send("direct:strict",
                e -> e.getIn().setBody(Arrays.asList("FAIL", "FAIL", "a", "b")));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set even when aborted");
        assertTrue(splitResult.isAborted());
        assertEquals(2, splitResult.getFailureCount());
    }

    @Test
    void testSplitResultAllSuccess() throws Exception {
        Exchange result = template.send("direct:tolerant",
                e -> e.getIn().setBody(Arrays.asList("a", "b", "c")));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set even with no failures");

        assertEquals(3, splitResult.getTotalItems());
        assertEquals(0, splitResult.getFailureCount());
        assertEquals(3, splitResult.getSuccessCount());
        assertFalse(splitResult.isAborted());
        assertTrue(splitResult.getFailures().isEmpty());
    }

    @Test
    void testNoSplitResultWithoutErrorThreshold() throws Exception {
        // plain split without error threshold should not set SplitResult
        Exchange result = template.send("direct:plain",
                e -> e.getIn().setBody(Arrays.asList("a", "b", "c")));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNull(splitResult, "SplitResult should not be set without error threshold");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:tolerant")
                        .split(body()).maxFailedRecords(5)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure for: " + body);
                            }
                        })
                        .to("mock:split");

                from("direct:strict")
                        .split(body()).maxFailedRecords(2)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure for: " + body);
                            }
                        })
                        .to("mock:split");

                from("direct:plain")
                        .split(body())
                        .to("mock:split");
            }
        };
    }
}
