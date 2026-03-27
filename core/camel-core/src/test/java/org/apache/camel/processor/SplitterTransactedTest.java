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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.SplitResult;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Splitter EIP enhancements (group, error threshold, watermark) using the transacted code path
 * ({@link MulticastProcessor.MulticastTransactedTask}).
 */
class SplitterTransactedTest extends ContextTestSupport {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Test
    void testGroupTransacted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:group");
        mock.expectedMessageCount(3);

        Exchange exchange = createExchangeWithBody(Arrays.asList("a", "b", "c", "d", "e", "f", "g"));
        exchange.getExchangeExtension().setTransacted(true);

        template.send("direct:group", exchange);

        mock.assertIsSatisfied();

        // verify chunks
        assertEquals(List.of("a", "b", "c"), mock.getReceivedExchanges().get(0).getIn().getBody());
        assertEquals(List.of("d", "e", "f"), mock.getReceivedExchanges().get(1).getIn().getBody());
        assertEquals(List.of("g"), mock.getReceivedExchanges().get(2).getIn().getBody());
    }

    @Test
    void testMaxFailedRecordsTransacted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:maxfail");
        mock.expectedMinimumMessageCount(0);

        Exchange exchange = createExchangeWithBody(Arrays.asList("ok", "FAIL", "ok2", "FAIL2", "ok3"));
        exchange.getExchangeExtension().setTransacted(true);

        Exchange result = template.send("direct:maxfail", exchange);

        mock.assertIsSatisfied();

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);
        assertEquals(2, splitResult.getFailureCount());
        assertTrue(splitResult.isAborted());
    }

    @Test
    void testErrorThresholdTransacted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:threshold");
        mock.expectedMinimumMessageCount(0);

        // 2 out of 4 fail = 50% error rate, threshold is 0.4 (40%)
        Exchange exchange = createExchangeWithBody(Arrays.asList("ok", "FAIL", "FAIL2", "ok2"));
        exchange.getExchangeExtension().setTransacted(true);

        Exchange result = template.send("direct:threshold", exchange);

        mock.assertIsSatisfied();

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);
        assertTrue(splitResult.isAborted());
    }

    @Test
    void testSplitResultTransacted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(3);

        Exchange exchange = createExchangeWithBody(Arrays.asList("ok", "FAIL", "ok2", "ok3"));
        exchange.getExchangeExtension().setTransacted(true);

        Exchange result = template.send("direct:result", exchange);

        mock.assertIsSatisfied();

        SplitResult splitResult = result.getProperty(Exchange.SPLIT_RESULT, SplitResult.class);
        assertNotNull(splitResult);
        assertEquals(4, splitResult.getTotalItems());
        assertEquals(1, splitResult.getFailureCount());
        assertEquals(3, splitResult.getSuccessCount());
        assertFalse(splitResult.isAborted());
    }

    @Test
    void testIndexWatermarkTransacted() throws Exception {
        store.put("txJob", "1");

        MockEndpoint mock = getMockEndpoint("mock:watermark");
        mock.expectedMessageCount(3);

        Exchange exchange = createExchangeWithBody(Arrays.asList("a", "b", "c", "d", "e"));
        exchange.getExchangeExtension().setTransacted(true);

        Exchange result = template.send("direct:watermark", exchange);

        mock.assertIsSatisfied();

        // should skip items 0 and 1, process c, d, e
        assertEquals("c", mock.getReceivedExchanges().get(0).getIn().getBody(String.class));
        assertEquals("d", mock.getReceivedExchanges().get(1).getIn().getBody(String.class));
        assertEquals("e", mock.getReceivedExchanges().get(2).getIn().getBody(String.class));

        // watermark updated to 4
        assertEquals("4", store.get("txJob"));

        // previous watermark exposed
        assertEquals("1", result.getProperty(Exchange.SPLIT_WATERMARK));
    }

    @Test
    void testValueWatermarkTransacted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:valuewm");
        mock.expectedMessageCount(3);

        Exchange exchange = createExchangeWithBody(Arrays.asList("2024-01-01", "2024-01-02", "2024-01-03"));
        exchange.getExchangeExtension().setTransacted(true);

        template.send("direct:valuewm", exchange);

        mock.assertIsSatisfied();

        // watermark should be the last processed item
        assertEquals("2024-01-03", store.get("txDateJob"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:group")
                        .split(body()).group(3)
                        .to("mock:group");

                from("direct:maxfail")
                        .split(body()).maxFailedRecords(2)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if (body.startsWith("FAIL")) {
                                throw new IllegalArgumentException("Simulated failure: " + body);
                            }
                        })
                        .to("mock:maxfail");

                from("direct:threshold")
                        .split(body()).errorThreshold(0.4)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if (body.startsWith("FAIL")) {
                                throw new IllegalArgumentException("Simulated failure: " + body);
                            }
                        })
                        .to("mock:threshold");

                from("direct:result")
                        .split(body()).maxFailedRecords(10)
                        .process(exchange -> {
                            String body = exchange.getIn().getBody(String.class);
                            if (body.startsWith("FAIL")) {
                                throw new IllegalArgumentException("Simulated failure: " + body);
                            }
                        })
                        .to("mock:result");

                from("direct:watermark")
                        .split(body()).watermarkStore(store, "txJob")
                        .to("mock:watermark");

                from("direct:valuewm")
                        .split(body())
                        .watermarkStore(store, "txDateJob")
                        .watermarkExpression("${body}")
                        .to("mock:valuewm");
            }
        };
    }
}
