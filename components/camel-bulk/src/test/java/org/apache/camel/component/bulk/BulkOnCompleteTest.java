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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkOnCompleteTest extends CamelTestSupport {

    @Test
    void testOnCompleteFiresOnSuccess() throws Exception {
        MockEndpoint completeMock = getMockEndpoint("mock:complete");
        completeMock.expectedMessageCount(1);

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            items.add(i);
        }

        template.sendBody("direct:oncomplete", items);

        completeMock.assertIsSatisfied();

        // Verify the onComplete exchange has a BulkResult body
        Exchange completeExchange = completeMock.getExchanges().get(0);
        Object body = completeExchange.getIn().getBody();
        assertInstanceOf(BulkResult.class, body);

        BulkResult result = (BulkResult) body;
        assertEquals(20, result.getTotalItems());
        assertEquals(20, result.getSuccessCount());
        assertFalse(result.isAborted());
    }

    @Test
    void testOnCompleteFiresOnFailure() throws Exception {
        MockEndpoint completeMock = getMockEndpoint("mock:complete-on-failure");
        completeMock.expectedMessageCount(1);

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:oncomplete-with-failures", exchange -> {
            exchange.getIn().setBody(items);
        });

        completeMock.assertIsSatisfied();

        // onComplete should fire even when bulk aborts
        Exchange completeExchange = completeMock.getExchanges().get(0);
        BulkResult completeResult = completeExchange.getIn().getBody(BulkResult.class);
        assertNotNull(completeResult);
        assertTrue(completeResult.isAborted());
        assertTrue(completeResult.getFailureCount() > 0);

        // The main exchange should have the exception
        assertNotNull(result.getException());
        assertInstanceOf(BulkException.class, result.getException());
    }

    @Test
    void testJobInstanceIdIsUnique() throws Exception {
        List<Integer> items = List.of(1, 2, 3);

        Exchange result1 = template.send("direct:oncomplete", exchange -> {
            exchange.getIn().setBody(items);
        });
        Exchange result2 = template.send("direct:oncomplete", exchange -> {
            exchange.getIn().setBody(items);
        });

        String id1 = result1.getIn().getHeader(BulkConstants.BULK_JOB_INSTANCE_ID, String.class);
        String id2 = result2.getIn().getHeader(BulkConstants.BULK_JOB_INSTANCE_ID, String.class);

        assertNotNull(id1);
        assertNotNull(id2);
        assertFalse(id1.equals(id2), "Each bulk execution should have a unique instance ID");
    }

    @Test
    void testOnCompleteCallbackFailureDoesNotAffectResult() throws Exception {
        // onComplete callback throws, but the bulk result should still succeed
        List<Integer> items = List.of(1, 2, 3);

        Exchange result = template.send("direct:oncomplete-failing-callback", exchange -> {
            exchange.getIn().setBody(items);
        });

        // The main exchange should succeed despite the callback failure
        assertNull(result.getException());
        BulkResult bulkResult = result.getIn().getBody(BulkResult.class);
        assertNotNull(bulkResult);
        assertEquals(3, bulkResult.getTotalItems());
        assertEquals(3, bulkResult.getSuccessCount());
        assertFalse(bulkResult.isAborted());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:process-ok")
                        .log("Processing ${body}");

                from("direct:process-fail-some")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BulkConstants.BULK_INDEX, Integer.class);
                            if (index % 3 == 0) {
                                throw new RuntimeException("Fail item " + index);
                            }
                        });

                from("direct:oncomplete")
                        .to("bulk:completeJob?processorRef=direct:process-ok"
                            + "&onCompleteRef=direct:on-complete-handler");

                from("direct:on-complete-handler")
                        .to("mock:complete");

                from("direct:oncomplete-with-failures")
                        .to("bulk:failCompleteJob?processorRef=direct:process-fail-some"
                            + "&errorThreshold=0.1"
                            + "&onCompleteRef=direct:on-complete-failure-handler");

                from("direct:on-complete-failure-handler")
                        .to("mock:complete-on-failure");

                from("direct:oncomplete-failing-callback")
                        .to("bulk:failingCallbackJob?processorRef=direct:process-ok"
                            + "&onCompleteRef=direct:on-complete-throws");

                from("direct:on-complete-throws")
                        .process(exchange -> {
                            throw new RuntimeException("onComplete callback failed!");
                        });
            }
        };
    }
}
