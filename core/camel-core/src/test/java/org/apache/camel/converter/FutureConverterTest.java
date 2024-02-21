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
package org.apache.camel.converter;

import java.sql.Timestamp;
import java.util.concurrent.Future;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Isolated
public class FutureConverterTest extends ContextTestSupport {

    @Test
    public void testConvertFuture() {
        Future<?> future = template.asyncRequestBody("direct:foo", "Hello World");

        String out = context.getTypeConverter().convertTo(String.class, future);
        assertEquals("Bye World", out);
    }

    @Test
    public void testConvertMandatoryFuture() throws Exception {
        Future<?> future = template.asyncRequestBody("direct:foo", "Hello World");

        String out = context.getTypeConverter().mandatoryConvertTo(String.class, future);
        assertEquals("Bye World", out);
    }

    @Test
    public void testConvertMandatoryFutureWithExchange() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Future<?> future = template.asyncRequestBody("direct:foo", "Hello World");

        String out = context.getTypeConverter().mandatoryConvertTo(String.class, exchange, future);
        assertEquals("Bye World", out);
    }

    @Test
    public void testConvertMandatoryFutureWithExchangeFailed() {
        Exchange exchange = new DefaultExchange(context);
        Future<?> future = template.asyncRequestBody("direct:foo", "Hello World");

        assertThrows(NoTypeConversionAvailableException.class,
                () -> context.getTypeConverter().mandatoryConvertTo(Timestamp.class, exchange, future),
                "Should have thrown an exception");
    }

    @Test
    public void testConvertFutureWithExchangeFailed() throws Exception {
        Exchange exchange = new DefaultExchange(context);
        Future<?> future = template.asyncRequestBody("direct:foo", "Hello World");

        Timestamp out = context.getTypeConverter().convertTo(Timestamp.class, exchange, future);
        assertNull(out);
    }

    @Test
    public void testConvertFutureCancelled() {
        Future<?> future = template.asyncRequestBody("direct:foo", "Hello World");
        future.cancel(true);

        Object out = context.getTypeConverter().convertTo(String.class, future);
        // should be null since its cancelled
        assertNull(out);
    }

    @Test
    public void testConvertFutureCancelledThenOkay() {
        Future<?> future = template.asyncRequestBody("direct:foo", "Hello World");
        future.cancel(true);

        Object out = context.getTypeConverter().convertTo(String.class, future);
        // should be null since its cancelled
        assertNull(out);

        future = template.asyncRequestBody("direct:foo", "Hello World");

        out = context.getTypeConverter().convertTo(String.class, future);
        // not cancelled so we get the result this time
        assertEquals("Bye World", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:foo").delay(10).transform(constant("Bye World"));
            }
        };
    }
}
