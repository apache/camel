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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExpressionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The split must complete, with the exception set, when the watermark cannot be evaluated or stored.
 */
class SplitterWatermarkStoreFailureTest extends ContextTestSupport {

    private final Map<String, String> store = new ConcurrentHashMap<>();
    private final FailingResumeStrategy strategy = new FailingResumeStrategy(store);

    @Test
    void testSplitCompletesWhenStoringTheWatermarkFails() throws Exception {
        strategy.failures.set(1);
        getMockEndpoint("mock:split").expectedMessageCount(3);

        Exchange out = template.asyncSend("direct:start", e -> e.getIn().setBody(Arrays.asList("a", "b", "c")))
                .get(5, TimeUnit.SECONDS);

        MockEndpoint.assertIsSatisfied(context);
        assertNotNull(out.getException());
        assertInstanceOf(IllegalStateException.class, out.getException().getCause());
        assertFalse(store.containsKey("failJob"));
    }

    @Test
    void testFailedWatermarkIsNotUsedByTheNextSplit() throws Exception {
        strategy.failures.set(1);
        Exchange out = template.asyncSend("direct:start", e -> e.getIn().setBody(Arrays.asList("a", "b", "c")))
                .get(5, TimeUnit.SECONDS);
        assertNotNull(out.getException());

        // the store works again: the items whose watermark was not stored are processed again
        MockEndpoint mock = getMockEndpoint("mock:split");
        mock.reset();
        mock.expectedBodiesReceived("a", "b", "c");

        out = template.asyncSend("direct:start", e -> e.getIn().setBody(Arrays.asList("a", "b", "c")))
                .get(5, TimeUnit.SECONDS);

        mock.assertIsSatisfied();
        assertNull(out.getException());
        assertEquals("2", store.get("failJob"));
    }

    @Test
    void testRedeliveryProcessesTheItemsAgainWhenStoringTheWatermarkFails() throws Exception {
        strategy.failures.set(1);
        getMockEndpoint("mock:redeliver").expectedBodiesReceived("a", "b", "c", "a", "b", "c");

        Exchange out = template.asyncSend("direct:redeliver", e -> e.getIn().setBody(Arrays.asList("a", "b", "c")))
                .get(5, TimeUnit.SECONDS);

        MockEndpoint.assertIsSatisfied(context);
        assertNull(out.getException());
        assertEquals("2", store.get("redeliverJob"));
    }

    @Test
    void testSplitCompletesWhenTheWatermarkExpressionFails() throws Exception {
        getMockEndpoint("mock:value").expectedMessageCount(2);

        Exchange out = template.asyncSend("direct:value", e -> e.getIn().setBody(Arrays.asList("a", "b")))
                .get(5, TimeUnit.SECONDS);

        MockEndpoint.assertIsSatisfied(context);
        assertInstanceOf(RuntimeExpressionException.class, out.getException());
        assertFalse(store.containsKey("valueJob"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body()).resumeStrategy(strategy, "failJob")
                        .to("mock:split");

                from("direct:redeliver")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0))
                        .split(body()).resumeStrategy(strategy, "redeliverJob")
                        .to("mock:redeliver");

                // the watermark expression fails on every item
                from("direct:value")
                        .split(body()).resumeStrategy(strategy, "valueJob").watermarkExpression("${body.noSuchMethod()}")
                        .to("mock:value");
            }
        };
    }

    private static class FailingResumeStrategy extends SplitterTestResumeStrategy {

        private final AtomicInteger failures = new AtomicInteger();

        FailingResumeStrategy(Map<String, String> store) {
            super(store);
        }

        @Override
        public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offsetValue) {
            if (failures.getAndDecrement() > 0) {
                throw new IllegalStateException("Cannot store the watermark");
            }
            super.updateLastOffset(offsetKey, offsetValue);
        }
    }
}
