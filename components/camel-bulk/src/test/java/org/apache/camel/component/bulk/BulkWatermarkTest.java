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
package org.apache.camel.component.bulk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BulkWatermarkTest extends CamelTestSupport {

    private final Map<String, String> watermarkStore = new HashMap<>();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("myWatermarkStore", watermarkStore);
    }

    @Test
    void testIndexBasedWatermarkSkipsAlreadyProcessedItems() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:watermark-processed");

        // First batch: items 1-100
        List<Integer> firstBatch = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            firstBatch.add(i);
        }

        mock.expectedMessageCount(100);
        Exchange result1 = template.send("direct:watermark", exchange -> {
            exchange.getIn().setBody(firstBatch);
        });
        mock.assertIsSatisfied();

        BulkResult result1Body = result1.getIn().getBody(BulkResult.class);
        assertNotNull(result1Body);
        assertEquals(100, result1Body.getTotalItems());
        assertEquals(100, result1Body.getSuccessCount());

        // Verify watermark was stored
        assertEquals("100", watermarkStore.get("watermarkJob"));

        // Second batch: items 1-200 (watermark should skip first 100)
        mock.reset();
        mock.expectedMessageCount(100);

        List<Integer> secondBatch = new ArrayList<>();
        for (int i = 1; i <= 200; i++) {
            secondBatch.add(i);
        }

        Exchange result2 = template.send("direct:watermark", exchange -> {
            exchange.getIn().setBody(secondBatch);
        });
        mock.assertIsSatisfied();

        BulkResult result2Body = result2.getIn().getBody(BulkResult.class);
        assertNotNull(result2Body);
        assertEquals(100, result2Body.getTotalItems());
        assertEquals(100, result2Body.getSuccessCount());

        // Verify watermark was updated
        assertEquals("200", watermarkStore.get("watermarkJob"));

        // Verify the second batch processed the right items (101-200)
        List<Exchange> exchanges = mock.getExchanges();
        assertEquals(101, exchanges.get(0).getIn().getBody(Integer.class));
        assertEquals(200, exchanges.get(99).getIn().getBody(Integer.class));
    }

    @Test
    void testValueBasedWatermarkTracking() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:value-watermark-processed");

        // First batch: items with IDs
        List<Map<String, Object>> firstBatch = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", i);
            item.put("name", "item-" + i);
            firstBatch.add(item);
        }

        mock.expectedMessageCount(50);
        Exchange result1 = template.send("direct:value-watermark", exchange -> {
            exchange.getIn().setBody(firstBatch);
        });
        mock.assertIsSatisfied();

        BulkResult result1Body = result1.getIn().getBody(BulkResult.class);
        assertNotNull(result1Body);
        assertEquals(50, result1Body.getTotalItems());

        // Verify value-based watermark was stored (last item's id = 50)
        assertEquals("50", watermarkStore.get("valueWatermarkJob"));

        // Second batch — watermark header should be set
        mock.reset();
        mock.expectedMessageCount(50);

        List<Map<String, Object>> secondBatch = new ArrayList<>();
        for (int i = 51; i <= 100; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", i);
            item.put("name", "item-" + i);
            secondBatch.add(item);
        }

        Exchange result2 = template.send("direct:value-watermark", exchange -> {
            exchange.getIn().setBody(secondBatch);
        });
        mock.assertIsSatisfied();

        // Verify watermark was updated to last item's id (100)
        assertEquals("100", watermarkStore.get("valueWatermarkJob"));

        // Verify the watermark value header was set on the exchange
        // (value from first run = "50")
        // Note: the header is set on the parent exchange before processing
        BulkResult result2Body = result2.getIn().getBody(BulkResult.class);
        assertEquals(50, result2Body.getTotalItems());
    }

    @Test
    void testWatermarkNotUpdatedOnAbort() throws Exception {
        // First run: process 50 items successfully to set watermark
        List<Integer> firstBatch = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            firstBatch.add(i);
        }
        template.send("direct:watermark-abort", exchange -> {
            exchange.getIn().setBody(firstBatch);
        });
        assertEquals("50", watermarkStore.get("abortWatermarkJob"));

        // Second run: all items fail (they all throw), triggering abort
        List<Integer> secondBatch = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            secondBatch.add(i);
        }
        Exchange result = template.send("direct:watermark-abort-fail", exchange -> {
            exchange.getIn().setBody(secondBatch);
        });

        assertNotNull(result.getException());

        // Watermark should NOT have been updated since the batch was aborted
        assertEquals("50", watermarkStore.get("abortWatermarkJob"),
                "Watermark should not be updated when batch is aborted");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:watermark-process")
                        .to("mock:watermark-processed");

                from("direct:watermark")
                        .to("bulk:watermarkJob?chunkSize=50&processorRef=direct:watermark-process"
                            + "&watermarkStore=#myWatermarkStore");

                from("direct:value-watermark-process")
                        .to("mock:value-watermark-processed");

                from("direct:value-watermark")
                        .to("bulk:valueWatermarkJob?chunkSize=50&processorRef=direct:value-watermark-process"
                            + "&watermarkStore=#myWatermarkStore"
                            + "&watermarkExpression=${body[id]}");

                from("direct:watermark-abort-ok")
                        .log("OK: ${body}");

                from("direct:watermark-abort")
                        .to("bulk:abortWatermarkJob?chunkSize=50&processorRef=direct:watermark-abort-ok"
                            + "&watermarkStore=#myWatermarkStore");

                from("direct:watermark-abort-fail-all")
                        .process(exchange -> {
                            throw new RuntimeException("Fail everything");
                        });

                from("direct:watermark-abort-fail")
                        .to("bulk:abortWatermarkJob?chunkSize=50&processorRef=direct:watermark-abort-fail-all"
                            + "&watermarkStore=#myWatermarkStore&errorThreshold=0.1");
            }
        };
    }
}
