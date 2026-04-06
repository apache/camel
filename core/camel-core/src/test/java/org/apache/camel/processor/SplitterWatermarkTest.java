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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.SplitResult;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.resume.ResumeStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SplitterWatermarkTest extends ContextTestSupport {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final ResumeStrategy strategy = new SplitterTestResumeStrategy(store);

    @Test
    void testIndexBasedWatermarkFirstRun() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(5);

        template.sendBody("direct:index", Arrays.asList("a", "b", "c", "d", "e"));

        mock.assertIsSatisfied();

        // watermark should be stored as last index (4)
        assertEquals("4", store.get("testJob"));
    }

    @Test
    void testIndexBasedWatermarkSecondRun() throws Exception {
        // simulate a previous run that processed items 0-2
        store.put("testJob", "2");

        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(2); // only items 3 and 4

        template.sendBody("direct:index", Arrays.asList("a", "b", "c", "d", "e"));

        mock.assertIsSatisfied();

        // bodies should be d, e (items at index 3 and 4)
        assertEquals("d", mock.getReceivedExchanges().get(0).getIn().getBody(String.class));
        assertEquals("e", mock.getReceivedExchanges().get(1).getIn().getBody(String.class));

        // watermark updated to 4
        assertEquals("4", store.get("testJob"));
    }

    @Test
    void testIndexBasedWatermarkNoUpdateOnAbort() throws Exception {
        // watermark at 0, maxFailedRecords=1
        store.put("testJob2", "0");

        MockEndpoint mock = getMockEndpoint("mock:split2");
        mock.expectedMinimumMessageCount(0);

        // items after skipping index 0: FAIL, c, d — FAIL triggers abort
        template.send("direct:index-abort",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "c", "d")));

        mock.assertIsSatisfied();

        // watermark should NOT be updated (still 0)
        assertEquals("0", store.get("testJob2"));
    }

    @Test
    void testValueBasedWatermark() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split3");
        mock.expectedMessageCount(3);

        Exchange result = template.send("direct:value",
                e -> e.getIn().setBody(Arrays.asList("2024-01-01", "2024-01-02", "2024-01-03")));

        mock.assertIsSatisfied();

        // no previous watermark, so SPLIT_WATERMARK should not be set
        assertNull(result.getProperty(Exchange.SPLIT_WATERMARK));

        // watermark expression evaluated per-item: last successful item's body is stored
        assertEquals("2024-01-03", store.get("dateJob"));
    }

    @Test
    void testValueBasedWatermarkWithPreviousValue() throws Exception {
        store.put("dateJob", "2024-01-01");

        MockEndpoint mock = getMockEndpoint("mock:split3");
        mock.expectedMessageCount(3);

        Exchange result = template.send("direct:value",
                e -> e.getIn().setBody(Arrays.asList("2024-01-02", "2024-01-03", "2024-01-04")));

        mock.assertIsSatisfied();

        // previous watermark should be exposed as exchange property
        assertEquals("2024-01-01", result.getProperty(Exchange.SPLIT_WATERMARK));

        // watermark updated to the last processed date
        assertEquals("2024-01-04", store.get("dateJob"));
    }

    @Test
    void testValueBasedWatermarkWithParallelProcessing() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:parallel-wm");
        mock.expectedMessageCount(5);

        template.sendBody("direct:parallel-value",
                Arrays.asList("val-0", "val-1", "val-2", "val-3", "val-4"));

        mock.assertIsSatisfied();

        // In parallel mode, the watermark should be from the highest-indexed item (val-4)
        assertEquals("val-4", store.get("parallelJob"));
    }

    @Test
    void testIndexBasedWatermarkWithGroup() throws Exception {
        // 10 items, group=3 → 4 chunks: [a,b,c], [d,e,f], [g,h,i], [j]
        // Watermark should store "9" (last raw item index), not "3" (last chunk index)
        MockEndpoint mock = getMockEndpoint("mock:group-wm");
        mock.expectedMessageCount(4);

        template.sendBody("direct:group-index",
                Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));

        mock.assertIsSatisfied();

        assertEquals("9", store.get("groupJob"), "Watermark should be last raw item index, not chunk index");
    }

    @Test
    void testIndexBasedWatermarkWithGroupSecondRun() throws Exception {
        // Pre-populate watermark at 4 → skip items 0-4 (a,b,c,d,e)
        // Remaining: f,g,h,i,j → chunks [f,g,h], [i,j]
        store.put("groupJob", "4");

        MockEndpoint mock = getMockEndpoint("mock:group-wm");
        mock.expectedMessageCount(2);

        template.sendBody("direct:group-index",
                Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));

        mock.assertIsSatisfied();

        // first chunk should be [f,g,h]
        List<?> firstChunk = mock.getReceivedExchanges().get(0).getIn().getBody(List.class);
        assertEquals(Arrays.asList("f", "g", "h"), firstChunk);

        // watermark updated to 9 (last raw item index)
        assertEquals("9", store.get("groupJob"));
    }

    @Test
    void testIndexBasedWatermarkWithParallelProcessing() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:parallel-idx");
        mock.expectedMessageCount(5);

        template.sendBody("direct:parallel-index",
                Arrays.asList("a", "b", "c", "d", "e"));

        mock.assertIsSatisfied();

        assertEquals("4", store.get("parallelIdxJob"));
    }

    @Test
    void testValueBasedWatermarkExpressionReturnsNull() throws Exception {
        // watermarkExpression=${header.wm} — some items won't have the header, so expression returns null
        MockEndpoint mock = getMockEndpoint("mock:null-expr");
        mock.expectedMessageCount(3);

        template.sendBody("direct:null-expr", Arrays.asList("a", "b", "c"));

        mock.assertIsSatisfied();

        // only items where the header is set contribute to the watermark
        // the processor sets the header only for "b", so watermark should be "wm-b"
        assertEquals("wm-b", store.get("nullExprJob"));
    }

    @Test
    void testValueBasedWatermarkAllItemsFail() throws Exception {
        // all items fail → no watermark expression evaluated → latestRef stays null → no watermark persisted
        MockEndpoint mock = getMockEndpoint("mock:all-fail-wm");
        mock.expectedMessageCount(0);

        template.send("direct:all-fail-wm",
                e -> e.getIn().setBody(Arrays.asList("FAIL", "FAIL", "FAIL")));

        mock.assertIsSatisfied();

        // watermark should NOT be persisted (all items failed)
        assertNull(store.get("allFailJob"), "Watermark should not be persisted when all items fail");
    }

    @Test
    void testCombinedErrorThresholdAndWatermark() throws Exception {
        // both errorThreshold and watermark together
        MockEndpoint mock = getMockEndpoint("mock:combined-wm");
        mock.expectedMessageCount(4);

        Exchange result = template.send("direct:combined-wm",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "b", "c", "d")));

        mock.assertIsSatisfied();

        // watermark should be updated (processing succeeded overall)
        assertEquals("4", store.get("combinedJob"));

        // SplitResult should be set (errorThreshold is configured)
        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);
        assertEquals(5, splitResult.getTotalItems());
        assertEquals(1, splitResult.getFailureCount());
        assertFalse(splitResult.isAborted());
    }

    @Test
    void testIndexBasedWatermarkEmptyInput() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(0);

        template.sendBody("direct:index", Collections.emptyList());

        mock.assertIsSatisfied();

        // no items processed, watermark should not be set
        assertNull(store.get("testJob"), "Watermark should not be set for empty input");
    }

    @Test
    void testIndexBasedWatermarkFirstRunSingleItem() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.expectedMessageCount(1);

        template.sendBody("direct:index", List.of("only"));

        mock.assertIsSatisfied();

        assertEquals("0", store.get("testJob"), "Watermark should be 0 for single item");
    }

    @Test
    void testValueBasedWatermarkNoUpdateOnAbort() throws Exception {
        // value-based watermark should NOT update when processing is aborted
        store.put("abortValJob", "previous");

        MockEndpoint mock = getMockEndpoint("mock:abort-val-wm");
        mock.expectedMinimumMessageCount(0);

        template.send("direct:abort-val-wm",
                e -> e.getIn().setBody(Arrays.asList("FAIL", "FAIL", "a")));

        mock.assertIsSatisfied();

        // watermark should retain its previous value
        assertEquals("previous", store.get("abortValJob"),
                "Watermark should not be updated when processing is aborted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:index")
                        .split(body()).resumeStrategy(strategy, "testJob")
                        .to("mock:split");

                from("direct:index-abort")
                        .split(body()).resumeStrategy(strategy, "testJob2").maxFailedRecords(1)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure");
                            }
                        })
                        .to("mock:split2");

                from("direct:value")
                        .split(body())
                        .resumeStrategy(strategy, "dateJob")
                        .watermarkExpression("${body}")
                        .to("mock:split3");

                from("direct:group-index")
                        .split(body()).group(3).resumeStrategy(strategy, "groupJob")
                        .to("mock:group-wm");

                from("direct:parallel-value")
                        .split(body()).parallelProcessing()
                        .resumeStrategy(strategy, "parallelJob")
                        .watermarkExpression("${body}")
                        .to("mock:parallel-wm");

                from("direct:parallel-index")
                        .split(body()).parallelProcessing()
                        .resumeStrategy(strategy, "parallelIdxJob")
                        .to("mock:parallel-idx");

                from("direct:null-expr")
                        .split(body())
                        .resumeStrategy(strategy, "nullExprJob")
                        .watermarkExpression("${header.wm}")
                        .process(exchange -> {
                            // only set the watermark header for specific items
                            String body = exchange.getIn().getBody(String.class);
                            if ("b".equals(body)) {
                                exchange.getIn().setHeader("wm", "wm-b");
                            }
                        })
                        .to("mock:null-expr");

                from("direct:all-fail-wm")
                        .split(body())
                        .resumeStrategy(strategy, "allFailJob")
                        .watermarkExpression("${body}")
                        .maxFailedRecords(10)
                        .process(exchange -> {
                            throw new IllegalArgumentException("All fail");
                        })
                        .to("mock:all-fail-wm");

                from("direct:combined-wm")
                        .split(body())
                        .resumeStrategy(strategy, "combinedJob")
                        .maxFailedRecords(5)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated");
                            }
                        })
                        .to("mock:combined-wm");

                from("direct:abort-val-wm")
                        .split(body())
                        .resumeStrategy(strategy, "abortValJob")
                        .watermarkExpression("${body}")
                        .maxFailedRecords(2)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure");
                            }
                        })
                        .to("mock:abort-val-wm");
            }
        };
    }
}
