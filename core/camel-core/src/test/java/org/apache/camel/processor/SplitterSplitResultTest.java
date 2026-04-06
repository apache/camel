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
import java.util.Collections;

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
    void testSplitResultInStreamingMode() throws Exception {
        Exchange result = template.send("direct:streaming",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "b", "c", "d")));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set in streaming mode");
        assertEquals(5, splitResult.getTotalItems());
        assertEquals(1, splitResult.getFailureCount());
        assertEquals(4, splitResult.getSuccessCount());
        assertFalse(splitResult.isAborted());
    }

    @Test
    void testSplitResultWithParallelProcessing() throws Exception {
        Exchange result = template.send("direct:parallel",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "b", "c")));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set in parallel mode");
        assertEquals(4, splitResult.getTotalItems());
        assertEquals(1, splitResult.getFailureCount());
        assertEquals(3, splitResult.getSuccessCount());
        assertFalse(splitResult.isAborted());
    }

    @Test
    void testSplitResultToString() throws Exception {
        Exchange result = template.send("direct:tolerant",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "b", "c")));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);

        String str = splitResult.toString();
        assertTrue(str.contains("total=4"), "toString should contain total: " + str);
        assertTrue(str.contains("success=3"), "toString should contain success: " + str);
        assertTrue(str.contains("failures=1"), "toString should contain failures: " + str);
        assertTrue(str.contains("aborted=false"), "toString should contain aborted: " + str);
    }

    @Test
    void testSplitResultToStringWhenAborted() throws Exception {
        Exchange result = template.send("direct:strict",
                e -> e.getIn().setBody(Arrays.asList("FAIL", "FAIL", "a")));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);

        String str = splitResult.toString();
        assertTrue(str.contains("aborted=true"), "toString should contain aborted=true: " + str);
    }

    @Test
    void testSplitResultConstructorWithNullFailures() {
        SplitResult result = new SplitResult(5, 0, null, false);
        assertEquals(5, result.getTotalItems());
        assertEquals(0, result.getFailureCount());
        assertEquals(5, result.getSuccessCount());
        assertFalse(result.isAborted());
        assertNotNull(result.getFailures());
        assertTrue(result.getFailures().isEmpty());
    }

    @Test
    void testSplitResultEmptyInput() throws Exception {
        Exchange result = template.send("direct:tolerant",
                e -> e.getIn().setBody(Collections.emptyList()));

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set even with empty input");
        assertEquals(0, splitResult.getTotalItems());
        assertEquals(0, splitResult.getFailureCount());
        assertEquals(0, splitResult.getSuccessCount());
        assertFalse(splitResult.isAborted());
    }

    @Test
    void testSplitResultFailureRecord() {
        Exception cause = new IllegalArgumentException("test error");
        SplitResult.Failure failure = new SplitResult.Failure(3, cause);
        assertEquals(3, failure.index());
        assertEquals(cause, failure.exception());
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

                from("direct:streaming")
                        .split(body()).streaming().maxFailedRecords(10)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure for: " + body);
                            }
                        })
                        .to("mock:split");

                from("direct:parallel")
                        .split(body()).parallelProcessing().maxFailedRecords(10)
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
