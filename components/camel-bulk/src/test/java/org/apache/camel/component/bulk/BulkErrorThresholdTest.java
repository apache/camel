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
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkErrorThresholdTest extends CamelTestSupport {

    @Test
    void testErrorThresholdAbortsBulk() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(items);
        });

        // The bulk operation should have been aborted due to exceeding the error threshold
        Exception exception = result.getException();
        assertNotNull(exception, "Expected BulkException to be thrown");
        assertInstanceOf(BulkException.class, exception);

        BulkException bulkException = (BulkException) exception;
        BulkResult bulkResult = bulkException.getResult();
        assertNotNull(bulkResult);
        assertTrue(bulkResult.isAborted());
        assertTrue(bulkResult.getFailureCount() > 0);

        // Verify headers indicate abort
        assertTrue(result.getIn().getHeader(BulkConstants.BULK_ABORTED, Boolean.class));
    }

    @Test
    void testNoAbortWhenBelowThreshold() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:lenient", exchange -> {
            exchange.getIn().setBody(items);
        });

        // With threshold=1.0, it should never abort
        assertNotNull(result);
        assertNull(result.getException());

        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(100, bulkResult.getTotalItems());
        // 20 items fail (index % 5 == 0: indices 0, 5, 10, ..., 95 = 20 items)
        assertEquals(20, bulkResult.getFailureCount());
        assertEquals(80, bulkResult.getSuccessCount());
    }

    @Test
    void testMaxFailedRecordsAbortsBulk() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:maxfailed", exchange -> {
            exchange.getIn().setBody(items);
        });

        Exception exception = result.getException();
        assertNotNull(exception, "Expected BulkException to be thrown");
        assertInstanceOf(BulkException.class, exception);

        BulkResult bulkResult = ((BulkException) exception).getResult();
        assertTrue(bulkResult.isAborted());
        // maxFailedRecords=5, so we should have aborted after 6 failures
        assertTrue(bulkResult.getFailureCount() <= 10, "Should have aborted early, but had " + bulkResult.getFailureCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Processor that throws on items where index % 5 == 0
                from("direct:failsome")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BulkConstants.BULK_INDEX, Integer.class);
                            if (index % 5 == 0) {
                                throw new RuntimeException("Simulated failure for item " + index);
                            }
                        });

                from("direct:start")
                        .to("bulk:errorJob?chunkSize=100&processorRef=direct:failsome&errorThreshold=0.15");

                from("direct:lenient")
                        .to("bulk:lenientJob?chunkSize=100&processorRef=direct:failsome&errorThreshold=1.0");

                from("direct:maxfailed")
                        .to("bulk:maxFailedJob?chunkSize=100&processorRef=direct:failsome&maxFailedRecords=5");
            }
        };
    }
}
