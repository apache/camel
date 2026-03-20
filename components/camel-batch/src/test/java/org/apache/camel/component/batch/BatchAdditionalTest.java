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

class BatchAdditionalTest extends CamelTestSupport {

    @Test
    void testEmptyInput() throws Exception {
        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(new ArrayList<>());
        });

        assertNull(result.getException());
        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(0, batchResult.getTotalItems());
        assertEquals(0, batchResult.getSuccessCount());
        assertEquals(0, batchResult.getFailureCount());
        assertFalse(batchResult.isAborted());
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
        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(1, batchResult.getTotalItems());
        assertEquals(1, batchResult.getSuccessCount());
        assertEquals(0, batchResult.getFailureCount());
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
        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(50, batchResult.getTotalItems());
        assertEquals(50, batchResult.getSuccessCount());
        assertEquals(0, batchResult.getFailureCount());
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
        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(100, batchResult.getTotalItems());
        // Items where index % 5 == 0 fail (20 items)
        assertEquals(20, batchResult.getFailureCount());
        assertEquals(80, batchResult.getSuccessCount());
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
        assertNotNull(exception, "Expected BatchException to be thrown");
        assertInstanceOf(BatchException.class, exception);

        BatchResult batchResult = ((BatchException) exception).getResult();
        assertTrue(batchResult.isAborted());
        // maxFailedRecords=5, sequential processing, threshold checked after each failure
        // so exactly 6 failures trigger abort
        assertEquals(6, batchResult.getFailureCount(),
                "Should have exactly 6 failures (threshold exceeded at 6th)");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:process")
                        .to("mock:processed");

                from("direct:start")
                        .to("batch:testJob?chunkSize=100&processorRef=direct:process");

                from("direct:parallel-process")
                        .to("mock:parallel-processed");

                from("direct:parallel")
                        .to("batch:parallelJob?chunkSize=10&processorRef=direct:parallel-process&parallelProcessing=true");

                from("direct:parallel-fail-some")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BatchConstants.BATCH_INDEX, Integer.class);
                            if (index % 5 == 0) {
                                throw new RuntimeException("Fail item " + index);
                            }
                        });

                from("direct:parallel-with-failures")
                        .to("batch:parallelFailJob?chunkSize=10&processorRef=direct:parallel-fail-some&parallelProcessing=true");

                // Every 5th item fails sequentially
                from("direct:fail-every-5th")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BatchConstants.BATCH_INDEX, Integer.class);
                            if (index % 5 == 0) {
                                throw new RuntimeException("Fail item " + index);
                            }
                        });

                from("direct:maxfailed-tight")
                        .to("batch:maxFailedTightJob?chunkSize=100&processorRef=direct:fail-every-5th&maxFailedRecords=5");
            }
        };
    }
}
