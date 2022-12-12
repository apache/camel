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
package org.apache.camel.tracing;

import org.apache.camel.Exchange;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ActiveSpanManagerTest extends ExchangeTestSupport {

    @Test
    public void testNoSpan() {
        Exchange exchange = createExchange();
        assertNull(ActiveSpanManager.getSpan(exchange));

        // don't throw
        ActiveSpanManager.endScope(exchange);
    }

    @Test
    public void testCurrentSpan() {
        Exchange exchange = createExchange();
        MockSpanAdapter span = MockSpanAdapter.buildSpan("test");
        ActiveSpanManager.activate(exchange, span);
        assertTrue(span.isCurrent());
        assertEquals(span, ActiveSpanManager.getSpan(exchange));

        ActiveSpanManager.deactivate(exchange);
        assertFalse(span.isCurrent());
        assertNull(ActiveSpanManager.getSpan(exchange));
    }

    @Test
    public void testSEndScope() {
        Exchange exchange = createExchange();
        MockSpanAdapter span = MockSpanAdapter.buildSpan("test");
        ActiveSpanManager.activate(exchange, span);
        assertTrue(span.isCurrent());

        ActiveSpanManager.endScope(exchange);
        assertFalse(span.isCurrent());

        // scope has ended, but span is still on the exchange and can become parent to other spans.
        assertEquals(span, ActiveSpanManager.getSpan(exchange));
    }

    @Test
    public void testCreateChild() {
        Exchange exchange = createExchange();
        SpanAdapter parent = MockSpanAdapter.buildSpan("parent");
        ActiveSpanManager.activate(exchange, parent);
        SpanAdapter child = MockSpanAdapter.buildSpan("child");
        ActiveSpanManager.activate(exchange, child);
        assertEquals(child, ActiveSpanManager.getSpan(exchange));

        ActiveSpanManager.deactivate(exchange);
        assertEquals(parent, ActiveSpanManager.getSpan(exchange));
    }

    @Test
    public void testIsolatedConcurrentExchanges() {
        Exchange exchange = createExchange();
        SpanAdapter parent = MockSpanAdapter.buildSpan("parent");
        ActiveSpanManager.activate(exchange, parent);

        Exchange path1 = exchange.copy();
        Exchange path2 = exchange.copy();

        // Check the parent span is available in the new exchanges
        assertEquals(parent, ActiveSpanManager.getSpan(path1));
        assertEquals(parent, ActiveSpanManager.getSpan(path2));

        SpanAdapter child1 = MockSpanAdapter.buildSpan("child1");
        ActiveSpanManager.activate(path1, child1);

        SpanAdapter child2 = MockSpanAdapter.buildSpan("child2");
        ActiveSpanManager.activate(path2, child2);

        ActiveSpanManager.deactivate(path2);

        // Check that the current span in path2 is back to parent
        // and hasn't been affected by path1 creating its own child
        ActiveSpanManager.activate(path2, parent);
    }

    @Test
    public void testMDCSupport() {
        Exchange exchange = createExchange();
        exchange.getContext().setUseMDCLogging(true);

        SpanAdapter parent = MockSpanAdapter.buildSpan("parent");
        ((MockSpanAdapter) parent).setTraceId("0");
        ((MockSpanAdapter) parent).setSpanId("1");

        // no MDC entries before
        // 0 and 1 after activating parent
        assertNull(MDC.get(ActiveSpanManager.MDC_TRACE_ID));
        assertNull(MDC.get(ActiveSpanManager.MDC_SPAN_ID));
        ActiveSpanManager.activate(exchange, parent);
        assertEquals("0", MDC.get(ActiveSpanManager.MDC_TRACE_ID));
        assertEquals("1", MDC.get(ActiveSpanManager.MDC_SPAN_ID));

        // 1 and 2 after activating child1
        SpanAdapter child1 = MockSpanAdapter.buildSpan("child1");
        ((MockSpanAdapter) child1).setTraceId("1");
        ((MockSpanAdapter) child1).setSpanId("2");
        ActiveSpanManager.activate(exchange, child1);
        assertEquals("1", MDC.get(ActiveSpanManager.MDC_TRACE_ID));
        assertEquals("2", MDC.get(ActiveSpanManager.MDC_SPAN_ID));

        // back to 0 and 1 after deactivating child1
        ActiveSpanManager.deactivate(exchange);
        assertEquals("0", MDC.get(ActiveSpanManager.MDC_TRACE_ID));
        assertEquals("1", MDC.get(ActiveSpanManager.MDC_SPAN_ID));

        // back to no MDC entries after deactivating parent
        ActiveSpanManager.deactivate(exchange);
        assertNull(MDC.get(ActiveSpanManager.MDC_TRACE_ID));
        assertNull(MDC.get(ActiveSpanManager.MDC_SPAN_ID));
    }
}
