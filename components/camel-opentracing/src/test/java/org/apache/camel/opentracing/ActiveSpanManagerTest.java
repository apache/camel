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
package org.apache.camel.opentracing;

import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import org.apache.camel.Exchange;
import org.apache.camel.test.junit4.ExchangeTestSupport;
import org.junit.Test;

public class ActiveSpanManagerTest extends ExchangeTestSupport {

    private MockTracer tracer = new MockTracer();

    @Test
    public void testNoSpan() {
        Exchange exchange = createExchange();
        assertNull(ActiveSpanManager.getSpan(exchange));
    }

    @Test
    public void testCurrentSpan() {
        Exchange exchange = createExchange();
        Span span = tracer.buildSpan("test").start();
        ActiveSpanManager.activate(exchange, span);
        assertEquals(span, ActiveSpanManager.getSpan(exchange));
        
        ActiveSpanManager.deactivate(exchange);
        assertNull(ActiveSpanManager.getSpan(exchange));
    }

    @Test
    public void testCreateChild() {
        Exchange exchange = createExchange();
        Span parent = tracer.buildSpan("parent").start();
        ActiveSpanManager.activate(exchange, parent);
        Span child = tracer.buildSpan("child").start();
        ActiveSpanManager.activate(exchange, child);

        assertEquals(child, ActiveSpanManager.getSpan(exchange));
        
        ActiveSpanManager.deactivate(exchange);
        assertEquals(parent, ActiveSpanManager.getSpan(exchange));        
    }

    @Test
    public void testIsolatedConcurrentExchanges() {
        Exchange exchange = createExchange();
        Span parent = tracer.buildSpan("parent").start();
        ActiveSpanManager.activate(exchange, parent);

        Exchange path1 = exchange.copy();
        Exchange path2 = exchange.copy();

        // Check the parent span is available in the new exchanges
        assertEquals(parent, ActiveSpanManager.getSpan(path1));
        assertEquals(parent, ActiveSpanManager.getSpan(path2));

        Span child1 = tracer.buildSpan("child1").start();
        ActiveSpanManager.activate(path1, child1);

        Span child2 = tracer.buildSpan("child2").start();
        ActiveSpanManager.activate(path2, child2);

        ActiveSpanManager.deactivate(path2);

        // Check that the current span in path2 is back to parent
        // and hasn't been affected by path1 creating its own child
        ActiveSpanManager.activate(path2, parent);
    }
}
