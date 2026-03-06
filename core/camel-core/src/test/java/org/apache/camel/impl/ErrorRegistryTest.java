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
package org.apache.camel.impl;

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.ErrorRegistry;
import org.apache.camel.spi.ErrorRegistryEntry;
import org.apache.camel.spi.ErrorRegistryView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ErrorRegistryTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getErrorRegistry().setEnabled(true);
        return context;
    }

    @Test
    public void testErrorRegistryCapturesHandledError() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        ErrorRegistry registry = context.getErrorRegistry();
        Collection<ErrorRegistryEntry> entries = registry.browse();
        assertEquals(1, entries.size());

        ErrorRegistryEntry entry = entries.iterator().next();
        assertNotNull(entry.exchangeId());
        assertEquals("foo", entry.routeId());
        assertNotNull(entry.timestamp());
        assertTrue(entry.handled());
        assertEquals("java.lang.IllegalArgumentException", entry.exceptionType());
        assertEquals("Forced error", entry.exceptionMessage());
        assertNull(entry.stackTrace());
    }

    @Test
    public void testErrorRegistryDisabled() throws Exception {
        context.getErrorRegistry().setEnabled(false);

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        assertEquals(0, context.getErrorRegistry().size());
    }

    @Test
    public void testErrorRegistryForRoute() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(2);

        template.sendBody("direct:start", "Hello World");
        template.sendBody("direct:start2", "Bye World");

        assertMockEndpointsSatisfied();

        ErrorRegistry registry = context.getErrorRegistry();
        assertEquals(2, registry.size());

        // test forRoute view
        ErrorRegistryView fooView = registry.forRoute("foo");
        assertEquals(1, fooView.size());
        ErrorRegistryEntry fooEntry = fooView.browse().iterator().next();
        assertEquals("foo", fooEntry.routeId());

        ErrorRegistryView barView = registry.forRoute("bar");
        assertEquals(1, barView.size());

        // clear only foo route
        fooView.clear();
        assertEquals(1, registry.size());
        assertEquals(0, fooView.size());
        assertEquals(1, barView.size());
    }

    @Test
    public void testErrorRegistryMaximumEntries() throws Exception {
        context.getErrorRegistry().setMaximumEntries(2);

        getMockEndpoint("mock:dead").expectedMessageCount(3);

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");

        assertMockEndpointsSatisfied();

        // only 2 most recent entries should be kept
        assertEquals(2, context.getErrorRegistry().size());
    }

    @Test
    public void testErrorRegistryWithStackTrace() throws Exception {
        context.getErrorRegistry().setStackTraceEnabled(true);

        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        ErrorRegistryEntry entry = context.getErrorRegistry().browse().iterator().next();
        assertNotNull(entry.stackTrace());
        assertTrue(entry.stackTrace().length > 0);
        assertTrue(entry.stackTrace()[0].contains("IllegalArgumentException"));
    }

    @Test
    public void testErrorRegistryBrowseLimit() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(3);

        template.sendBody("direct:start", "A");
        template.sendBody("direct:start", "B");
        template.sendBody("direct:start", "C");

        assertMockEndpointsSatisfied();

        assertEquals(3, context.getErrorRegistry().size());
        assertEquals(2, context.getErrorRegistry().browse(2).size());
    }

    @Test
    public void testErrorRegistryClear() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        assertEquals(1, context.getErrorRegistry().size());
        context.getErrorRegistry().clear();
        assertEquals(0, context.getErrorRegistry().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:dead"));

                from("direct:start").routeId("foo")
                        .throwException(new IllegalArgumentException("Forced error"));

                from("direct:start2").routeId("bar")
                        .throwException(new IllegalArgumentException("Forced error 2"));
            }
        };
    }
}
