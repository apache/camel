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
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkAdditionalTest extends CamelTestSupport {

    @Test
    void testEmptyInput() throws Exception {
        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(new ArrayList<>());
        });

        assertNull(result.getException());
        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(0, bulkResult.getTotalItems());
        assertEquals(0, bulkResult.getSuccessCount());
        assertEquals(0, bulkResult.getFailureCount());
        assertFalse(bulkResult.isAborted());
    }

    @Test
    void testSingleItemInput() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:processed");
        mock.expectedMessageCount(1);

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody("single-item");
        });

        mock.assertIsSatisfied();

        assertNull(result.getException());
        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(1, bulkResult.getTotalItems());
        assertEquals(1, bulkResult.getSuccessCount());
        assertEquals(0, bulkResult.getFailureCount());
    }

    @Test
    void testParallelProcessing() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:parallel-processed");
        mock.expectedMessageCount(50);

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:parallel", exchange -> {
            exchange.getIn().setBody(items);
        });

        mock.assertIsSatisfied();

        assertNull(result.getException());
        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(50, bulkResult.getTotalItems());
        assertEquals(50, bulkResult.getSuccessCount());
        assertEquals(0, bulkResult.getFailureCount());
    }

    @Test
    void testParallelProcessingWithFailures() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:parallel-with-failures", exchange -> {
            exchange.getIn().setBody(items);
        });

        assertNull(result.getException());
        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(100, bulkResult.getTotalItems());
        // Items where index % 5 == 0 fail (20 items)
        assertEquals(20, bulkResult.getFailureCount());
        assertEquals(80, bulkResult.getSuccessCount());
    }

    @Test
    void testMaxFailedRecordsTightAssertion() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:maxfailed-tight", exchange -> {
            exchange.getIn().setBody(items);
        });

        Exception exception = result.getException();
        assertNotNull(exception, "Expected BulkException to be thrown");
        assertInstanceOf(BulkException.class, exception);

        BulkResult bulkResult = ((BulkException) exception).getResult();
        assertTrue(bulkResult.isAborted());
        // maxFailedRecords=5, sequential processing, threshold checked after each failure
        // so exactly 6 failures trigger abort
        assertEquals(6, bulkResult.getFailureCount(),
                "Should have exactly 6 failures (threshold exceeded at 6th)");
    }

    @Test
    void testNullBodyInput() throws Exception {
        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(null);
        });

        assertNull(result.getException());
        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(0, bulkResult.getTotalItems());
        assertEquals(0, bulkResult.getSuccessCount());
        assertEquals(0, bulkResult.getFailureCount());
        assertFalse(bulkResult.isAborted());
    }

    @Test
    void testIteratorInput() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:processed");
        mock.expectedMessageCount(3);

        Iterator<String> items = Arrays.asList("a", "b", "c").iterator();
        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(items);
        });

        mock.assertIsSatisfied();

        assertNull(result.getException());
        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(3, bulkResult.getTotalItems());
        assertEquals(3, bulkResult.getSuccessCount());
    }

    @Test
    void testIterableInput() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:processed");
        mock.expectedMessageCount(3);

        // LinkedHashSet implements Iterable but not List
        LinkedHashSet<String> items = new LinkedHashSet<>(Arrays.asList("x", "y", "z"));
        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(items);
        });

        mock.assertIsSatisfied();

        assertNull(result.getException());
        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(3, bulkResult.getTotalItems());
        assertEquals(3, bulkResult.getSuccessCount());
    }

    @Test
    void testMaxFailedRecordsWithErrorThreshold() throws Exception {
        // Both thresholds active: maxFailedRecords=3, errorThreshold=0.5
        // With 20 items, 4 fail (indices 0, 5, 10, 15)
        // maxFailedRecords=3 triggers abort at 4th failure (before errorThreshold at 50%)
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:combined-thresholds", exchange -> {
            exchange.getIn().setBody(items);
        });

        Exception exception = result.getException();
        assertNotNull(exception, "Expected BulkException to be thrown");
        assertInstanceOf(BulkException.class, exception);

        BulkResult bulkResult = ((BulkException) exception).getResult();
        assertTrue(bulkResult.isAborted());
        // maxFailedRecords=3 means abort at 4th failure
        assertEquals(4, bulkResult.getFailureCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:process")
                        .to("mock:processed");

                from("direct:start")
                        .to("bulk:testJob?chunkSize=100&processorRef=direct:process");

                from("direct:parallel-process")
                        .to("mock:parallel-processed");

                from("direct:parallel")
                        .to("bulk:parallelJob?chunkSize=10&processorRef=direct:parallel-process&parallelProcessing=true");

                from("direct:parallel-fail-some")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BulkConstants.BULK_INDEX, Integer.class);
                            if (index % 5 == 0) {
                                throw new RuntimeException("Fail item " + index);
                            }
                        });

                from("direct:parallel-with-failures")
                        .to("bulk:parallelFailJob?chunkSize=10&processorRef=direct:parallel-fail-some&parallelProcessing=true");

                // Every 5th item fails sequentially
                from("direct:fail-every-5th")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BulkConstants.BULK_INDEX, Integer.class);
                            if (index % 5 == 0) {
                                throw new RuntimeException("Fail item " + index);
                            }
                        });

                from("direct:maxfailed-tight")
                        .to("bulk:maxFailedTightJob?chunkSize=100&processorRef=direct:fail-every-5th&maxFailedRecords=5");

                from("direct:combined-thresholds")
                        .to("bulk:combinedJob?chunkSize=100&processorRef=direct:fail-every-5th&maxFailedRecords=3&errorThreshold=0.5");
            }
        };
    }
}
