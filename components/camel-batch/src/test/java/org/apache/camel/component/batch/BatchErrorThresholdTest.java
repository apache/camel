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
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchErrorThresholdTest extends CamelTestSupport {

    @Test
    void testErrorThresholdAbortsBatch() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(items);
        });

        // The batch should have been aborted due to exceeding the error threshold
        Exception exception = result.getException();
        assertNotNull(exception, "Expected BatchException to be thrown");
        assertTrue(exception instanceof BatchException, "Expected BatchException but got: " + exception.getClass().getName());

        BatchException batchException = (BatchException) exception;
        BatchResult batchResult = batchException.getResult();
        assertNotNull(batchResult);
        assertTrue(batchResult.isAborted());
        assertTrue(batchResult.getFailureCount() > 0);

        // Verify headers indicate abort
        assertTrue(result.getIn().getHeader(BatchConstants.BATCH_ABORTED, Boolean.class));
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
        assertEquals(null, result.getException());

        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(100, batchResult.getTotalItems());
        // 20 items fail (index % 5 == 0: indices 0, 5, 10, ..., 95 = 20 items)
        assertEquals(20, batchResult.getFailureCount());
        assertEquals(80, batchResult.getSuccessCount());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Processor that throws on items where index % 5 == 0
                from("direct:failsome")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BatchConstants.BATCH_INDEX, Integer.class);
                            if (index % 5 == 0) {
                                throw new RuntimeException("Simulated failure for item " + index);
                            }
                        });

                from("direct:start")
                        .to("batch:errorJob?chunkSize=100&processorRef=direct:failsome&errorThreshold=0.15");

                from("direct:lenient")
                        .to("batch:lenientJob?chunkSize=100&processorRef=direct:failsome&errorThreshold=1.0");
            }
        };
    }
}
