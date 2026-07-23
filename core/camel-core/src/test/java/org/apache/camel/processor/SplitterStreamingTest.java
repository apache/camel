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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplitterStreamingTest extends ContextTestSupport {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final ResumeStrategy strategy = new SplitterTestResumeStrategy(store);

    @Test
    void testStreamingSplitResultTotalItems() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:streaming");
        mock.expectedMessageCount(3);

        Exchange result = template.send("direct:streaming",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "b", "c")));

        mock.assertIsSatisfied();

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set in streaming mode");
        assertEquals(4, splitResult.getTotalItems(), "totalItems should reflect all iterated items");
        assertEquals(1, splitResult.getFailureCount());
        assertEquals(3, splitResult.getSuccessCount());
        assertFalse(splitResult.isAborted());
    }

    @Test
    void testStreamingSplitResultWhenAborted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:streaming-abort");
        mock.expectedMinimumMessageCount(0);

        Exchange result = template.send("direct:streaming-abort",
                e -> e.getIn().setBody(Arrays.asList("FAIL", "FAIL", "a", "b")));

        mock.assertIsSatisfied();

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult, "SplitResult should be set even when streaming and aborted");
        assertTrue(splitResult.isAborted());
        assertEquals(2, splitResult.getFailureCount());
        // totalItems reflects how many were iterated before abort (at least 2)
        assertTrue(splitResult.getTotalItems() >= 2, "totalItems should reflect items iterated before abort");
    }

    @Test
    void testStreamingGrouping() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:streaming-group");
        mock.expectedMessageCount(3);

        template.sendBody("direct:streaming-group", Arrays.asList("a", "b", "c", "d", "e", "f", "g"));

        mock.assertIsSatisfied();

        assertInstanceOf(List.class, mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(3, ((List<?>) mock.getReceivedExchanges().get(0).getIn().getBody()).size());
        assertEquals(1, ((List<?>) mock.getReceivedExchanges().get(2).getIn().getBody()).size());
    }

    @Test
    void testStreamingWatermark() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:streaming-wm");
        mock.expectedMessageCount(5);

        template.sendBody("direct:streaming-wm", Arrays.asList("a", "b", "c", "d", "e"));

        mock.assertIsSatisfied();

        assertEquals("4", store.get("streamJob"));
    }

    @Test
    void testStreamingWatermarkSecondRun() throws Exception {
        store.put("streamJob", "2");

        MockEndpoint mock = getMockEndpoint("mock:streaming-wm");
        mock.expectedMessageCount(2);

        template.sendBody("direct:streaming-wm", Arrays.asList("a", "b", "c", "d", "e"));

        mock.assertIsSatisfied();

        assertEquals("d", mock.getReceivedExchanges().get(0).getIn().getBody(String.class));
        assertEquals("e", mock.getReceivedExchanges().get(1).getIn().getBody(String.class));
        assertEquals("4", store.get("streamJob"));
    }

    @Test
    void testStreamingParallelMaxFailedRecords() throws Exception {
        // streaming + parallel + maxFailedRecords combination
        MockEndpoint mock = getMockEndpoint("mock:streaming-parallel");
        mock.expectedMinimumMessageCount(0);

        Exchange result = template.send("direct:streaming-parallel",
                e -> e.getIn().setBody(Arrays.asList("a", "FAIL", "b", "FAIL", "FAIL", "c")));

        mock.assertIsSatisfied();

        assertNotNull(result.getException(), "Should abort after 3 failures");

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);
        assertTrue(splitResult.isAborted());
        assertTrue(splitResult.getFailureCount() >= 3);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:streaming")
                        .split(body()).streaming().maxFailedRecords(10)
                        .process(exchange -> {
                            if ("FAIL".equals(exchange.getIn().getBody(String.class))) {
                                throw new IllegalArgumentException("Simulated");
                            }
                        })
                        .to("mock:streaming");

                from("direct:streaming-abort")
                        .split(body()).streaming().maxFailedRecords(2)
                        .process(exchange -> {
                            if ("FAIL".equals(exchange.getIn().getBody(String.class))) {
                                throw new IllegalArgumentException("Simulated");
                            }
                        })
                        .to("mock:streaming-abort");

                from("direct:streaming-group")
                        .split(body()).streaming().group(3)
                        .to("mock:streaming-group");

                from("direct:streaming-wm")
                        .split(body()).streaming().resumeStrategy(strategy, "streamJob")
                        .to("mock:streaming-wm");

                from("direct:streaming-parallel")
                        .split(body()).streaming().parallelProcessing().maxFailedRecords(3)
                        .process(exchange -> {
                            if ("FAIL".equals(exchange.getIn().getBody(String.class))) {
                                throw new IllegalArgumentException("Simulated");
                            }
                        })
                        .to("mock:streaming-parallel");
            }
        };
    }
}
