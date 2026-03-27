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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SplitterWatermarkTest extends ContextTestSupport {

    private final Map<String, String> store = new ConcurrentHashMap<>();

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

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:index")
                        .split(body()).watermarkStore(store, "testJob")
                        .to("mock:split");

                from("direct:index-abort")
                        .split(body()).watermarkStore(store, "testJob2").maxFailedRecords(1)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if ("FAIL".equals(body)) {
                                throw new IllegalArgumentException("Simulated failure");
                            }
                        })
                        .to("mock:split2");

                from("direct:value")
                        .split(body())
                        .watermarkStore(store, "dateJob")
                        .watermarkExpression("${body}")
                        .to("mock:split3");
            }
        };
    }
}
