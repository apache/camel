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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BatchMultiStepTest extends CamelTestSupport {

    @Test
    void testMultiStepProcessing() throws Exception {
        MockEndpoint step1Mock = getMockEndpoint("mock:step1-done");
        MockEndpoint step2Mock = getMockEndpoint("mock:step2-done");

        step1Mock.expectedMessageCount(10);
        step2Mock.expectedMessageCount(10);

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:multistep", exchange -> {
            exchange.getIn().setBody(items);
        });

        step1Mock.assertIsSatisfied();
        step2Mock.assertIsSatisfied();

        assertNull(result.getException());

        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(10, batchResult.getTotalItems());
        assertEquals(10, batchResult.getSuccessCount());
        assertEquals(0, batchResult.getFailureCount());

        // Verify items went through both steps (step1 doubles, step2 adds 1)
        // So item 3 -> 6 -> 7
        for (Exchange ex : step2Mock.getExchanges()) {
            int body = ex.getIn().getBody(Integer.class);
            int index = ex.getIn().getHeader(BatchConstants.BATCH_INDEX, Integer.class);
            assertEquals(index * 2 + 1, body, "Item " + index + " should be doubled then +1");
        }
    }

    @Test
    void testMultiStepWithNoFailuresPolicy() throws Exception {
        MockEndpoint step1Mock = getMockEndpoint("mock:validate-done");
        MockEndpoint step2Mock = getMockEndpoint("mock:transform-done");

        // Step 1 will fail on even numbers (5 out of 10 fail)
        // Only 5 items succeed in step 1 and reach its mock
        // Step 2 with NO_FAILURES should only process the 5 that succeeded
        step1Mock.expectedMessageCount(5);
        step2Mock.expectedMessageCount(5);

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:multistep-nofailures", exchange -> {
            exchange.getIn().setBody(items);
        });

        step1Mock.assertIsSatisfied();
        step2Mock.assertIsSatisfied();

        assertNull(result.getException());

        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(10, batchResult.getTotalItems());
        // 5 items failed in step 1 and were skipped in step 2 — still failed
        assertEquals(5, batchResult.getFailureCount());
        assertEquals(5, batchResult.getSuccessCount());

        // Verify only odd-numbered items reached step 2
        for (Exchange ex : step2Mock.getExchanges()) {
            int index = ex.getIn().getHeader(BatchConstants.BATCH_INDEX, Integer.class);
            assertEquals(1, index % 2, "Only odd indices should reach step 2, got index " + index);
        }
    }

    @Test
    void testMultiStepWithFailuresOnlyRecovery() throws Exception {
        MockEndpoint step1Mock = getMockEndpoint("mock:process-done");
        MockEndpoint recoveryMock = getMockEndpoint("mock:recovery-done");

        // Step 1 fails on items 0, 3, 6, 9 (multiples of 3) — 6 succeed and reach mock
        // Step 2 (FAILURES_ONLY) should only process those 4 items and recover them
        step1Mock.expectedMessageCount(6);
        recoveryMock.expectedMessageCount(4);

        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            items.add(i);
        }

        Exchange result = template.send("direct:multistep-recovery", exchange -> {
            exchange.getIn().setBody(items);
        });

        step1Mock.assertIsSatisfied();
        recoveryMock.assertIsSatisfied();

        assertNull(result.getException());

        BatchResult batchResult = result.getIn().getBody(BatchResult.class);
        assertNotNull(batchResult);
        assertEquals(10, batchResult.getTotalItems());
        // All items should end up successful (recovery step fixes the failures)
        assertEquals(0, batchResult.getFailureCount());
        assertEquals(10, batchResult.getSuccessCount());
    }

    @Test
    void testStepIndexHeader() throws Exception {
        MockEndpoint step1Mock = getMockEndpoint("mock:step1-done");
        MockEndpoint step2Mock = getMockEndpoint("mock:step2-done");

        step1Mock.expectedMessageCount(3);
        step2Mock.expectedMessageCount(3);

        template.sendBody("direct:multistep", List.of(1, 2, 3));

        step1Mock.assertIsSatisfied();
        step2Mock.assertIsSatisfied();

        // Step 1 should have stepIndex=0
        for (Exchange ex : step1Mock.getExchanges()) {
            assertEquals(0, ex.getIn().getHeader(BatchConstants.BATCH_STEP_INDEX, Integer.class));
        }
        // Step 2 should have stepIndex=1
        for (Exchange ex : step2Mock.getExchanges()) {
            assertEquals(1, ex.getIn().getHeader(BatchConstants.BATCH_STEP_INDEX, Integer.class));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Step 1: double the value
                from("direct:step1")
                        .transform(simple("${body}"))
                        .process(exchange -> {
                            int val = exchange.getIn().getBody(Integer.class);
                            exchange.getIn().setBody(val * 2);
                        })
                        .to("mock:step1-done");

                // Step 2: add 1
                from("direct:step2")
                        .process(exchange -> {
                            int val = exchange.getIn().getBody(Integer.class);
                            exchange.getIn().setBody(val + 1);
                        })
                        .to("mock:step2-done");

                // Multi-step route
                from("direct:multistep")
                        .to("batch:multiStepJob?steps=direct:step1,direct:step2&chunkSize=50");

                // Step 1: validate — fail even numbers
                from("direct:validate")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BatchConstants.BATCH_INDEX, Integer.class);
                            if (index % 2 == 0) {
                                throw new RuntimeException("Validation failed for even index " + index);
                            }
                        })
                        .to("mock:validate-done");

                // Step 2: transform
                from("direct:transform")
                        .to("mock:transform-done");

                // Multi-step with NO_FAILURES policy
                from("direct:multistep-nofailures")
                        .to("batch:validateJob?steps=direct:validate,direct:transform"
                            + "&acceptPolicy=NO_FAILURES&chunkSize=50");

                // Step 1: process — fail multiples of 3
                from("direct:process-step")
                        .process(exchange -> {
                            int index = exchange.getIn().getHeader(BatchConstants.BATCH_INDEX, Integer.class);
                            if (index % 3 == 0) {
                                throw new RuntimeException("Process failed for index " + index);
                            }
                        })
                        .to("mock:process-done");

                // Recovery step: always succeeds
                from("direct:recovery")
                        .process(exchange -> {
                            // Recovery logic — just set a marker
                            exchange.getIn().setHeader("recovered", true);
                        })
                        .to("mock:recovery-done");

                // Multi-step with FAILURES_ONLY recovery
                from("direct:multistep-recovery")
                        .to("batch:recoveryJob?steps=direct:process-step,direct:recovery"
                            + "&acceptPolicy=FAILURES_ONLY&chunkSize=50");
            }
        };
    }
}
