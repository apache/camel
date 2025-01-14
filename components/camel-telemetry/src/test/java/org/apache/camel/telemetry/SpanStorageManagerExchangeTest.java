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
package org.apache.camel.telemetry;

import org.apache.camel.Exchange;
import org.apache.camel.telemetry.mock.MockSpanAdapter;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SpanStorageManagerExchangeTest extends ExchangeTestSupport {

    SpanStorageManager ssm = new SpanStorageManagerExchange();

    @BeforeEach
    public void initialize() {
        this.ssm = new SpanStorageManagerExchange();
    }

    @Test
    void testPushPeek() {
        Exchange exchange = createExchange();
        assertNull(ssm.peek(exchange));
        MockSpanAdapter span1 = MockSpanAdapter.buildSpan("span1");
        ssm.push(exchange, span1);
        assertEquals(span1, ssm.peek(exchange));
        MockSpanAdapter span2 = MockSpanAdapter.buildSpan("span2");
        ssm.push(exchange, span2);
        assertEquals(span2, ssm.peek(exchange));
    }

    @Test
    void testPushPull() {
        Exchange exchange = createExchange();
        assertNull(ssm.pull(exchange));

        MockSpanAdapter span1 = MockSpanAdapter.buildSpan("span1");
        ssm.push(exchange, span1);
        assertEquals(span1, ssm.pull(exchange));
        MockSpanAdapter span2 = MockSpanAdapter.buildSpan("span2");
        ssm.push(exchange, span2);
        assertEquals(span2, ssm.pull(exchange));

        span1 = MockSpanAdapter.buildSpan("span1");
        ssm.push(exchange, span1);
        span2 = MockSpanAdapter.buildSpan("span2");
        ssm.push(exchange, span2);
        assertEquals(span2, ssm.pull(exchange));
        assertEquals(span1, ssm.pull(exchange));
        assertNull(ssm.pull(exchange));
    }

}
