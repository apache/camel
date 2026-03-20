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

class BatchTest extends CamelTestSupport {

    @Test
    void testBasicBatchProcessing() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:processed");
        mock.expectedMessageCount(250);

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(items);
        });

        mock.assertIsSatisfied();

        // Verify result
        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(250, batchResult.getTotalItems());
        assertEquals(250, batchResult.getSuccessCount());
        assertEquals(0, batchResult.getFailureCount());
        assertFalse(batchResult.isAborted());

        // Verify headers
        assertEquals("testJob", result.getIn().getHeader(BatchConstants.BATCH_JOB_NAME, String.class));
        assertEquals(250, result.getIn().getHeader(BatchConstants.BATCH_TOTAL, Integer.class));
        assertEquals(250, result.getIn().getHeader(BatchConstants.BATCH_SUCCESS, Integer.class));
        assertEquals(0, result.getIn().getHeader(BatchConstants.BATCH_FAILED, Integer.class));
        assertFalse(result.getIn().getHeader(BatchConstants.BATCH_ABORTED, Boolean.class));
        assertNotNull(result.getIn().getHeader(BatchConstants.BATCH_DURATION, Long.class));
    }

    @Test
    void testItemExchangeHeaders() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:processed");
        mock.expectedMessageCount(5);

        List<String> items = List.of("a", "b", "c", "d", "e");
        template.sendBody("direct:start", items);

        mock.assertIsSatisfied();

        // Verify item exchange headers
        for (int i = 0; i < 5; i++) {
            Exchange itemExchange = mock.getExchanges().get(i);
            assertEquals("testJob", itemExchange.getIn().getHeader(BatchConstants.BATCH_JOB_NAME));
            assertEquals(5, itemExchange.getIn().getHeader(BatchConstants.BATCH_SIZE));
            assertNotNull(itemExchange.getIn().getHeader(BatchConstants.BATCH_INDEX));
            assertNotNull(itemExchange.getIn().getHeader(BatchConstants.BATCH_CHUNK_INDEX));
        }
    }

    @Test
    void testBatchResultBody() throws Exception {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:start", exchange -> {
            exchange.getIn().setBody(items);
        });

        Object body = result.getIn().getBody();
        assertInstanceOf(BatchResult.class, body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:process")
                        .transform(simple("${body}"))
                        .to("mock:processed");

                from("direct:start")
                        .to("batch:testJob?chunkSize=100&processorRef=direct:process");
            }
        };
    }
}
