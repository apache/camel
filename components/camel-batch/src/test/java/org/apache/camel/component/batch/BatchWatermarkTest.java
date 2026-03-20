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
package org.apache.camel.component.batch;

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

class BatchWatermarkTest extends CamelTestSupport {

    private final Map<String, String> watermarkStore = new HashMap<>();

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("myWatermarkStore", watermarkStore);
    }

    @Test
    void testWatermarkSkipsAlreadyProcessedItems() throws Exception {
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

        BatchResult result1Body = result1.getIn().getBody(BatchResult.class);
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

        BatchResult result2Body = result2.getIn().getBody(BatchResult.class);
        assertNotNull(result2Body);
        // Only items 101-200 should be processed (watermark skips first 100)
        assertEquals(100, result2Body.getTotalItems());
        assertEquals(100, result2Body.getSuccessCount());

        // Verify watermark was updated
        assertEquals("200", watermarkStore.get("watermarkJob"));

        // Verify the second batch processed the right items (101-200)
        List<Exchange> exchanges = mock.getExchanges();
        // First item should be 101 (index 100 of the 200-item list)
        assertEquals(101, exchanges.get(0).getIn().getBody(Integer.class));
        // Last item should be 200
        assertEquals(200, exchanges.get(99).getIn().getBody(Integer.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:watermark-process")
                        .to("mock:watermark-processed");

                from("direct:watermark")
                        .to("batch:watermarkJob?chunkSize=50&processorRef=direct:watermark-process&watermarkStore=#myWatermarkStore");
            }
        };
    }
}
