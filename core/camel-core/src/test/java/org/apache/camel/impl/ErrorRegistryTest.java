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
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.BacklogErrorEventMessage;
import org.apache.camel.spi.ErrorRegistry;
import org.apache.camel.spi.ErrorRegistryView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ErrorRegistryTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getErrorRegistry().setEnabled(true);
        context.setMessageHistory(true);
        return context;
    }

    @Test
    public void testErrorRegistryCapturesHandledError() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();

        ErrorRegistry registry = context.getErrorRegistry();
        Collection<BacklogErrorEventMessage> entries = registry.browse();
        assertEquals(1, entries.size());

        BacklogErrorEventMessage entry = entries.iterator().next();
        assertNotNull(entry.getExchangeId());
        assertEquals("foo", entry.getRouteId());
        assertTrue(entry.getTimestamp() > 0);
        assertTrue(entry.isHandled());
        assertEquals("java.lang.IllegalArgumentException", entry.getExceptionType());
        assertEquals("Forced error", entry.getExceptionMessage());
        assertNotNull(entry.getException());
        assertTrue(entry.getException() instanceof IllegalArgumentException);
        assertTrue(entry.getUid() > 0);
        assertNotNull(entry.getProcessingThreadName());
        assertEquals("direct://start", entry.getFromEndpointUri());
        assertTrue(entry.getRouteUptime() >= 0, "Route uptime should be non-negative");
        assertTrue(entry.getElapsed() >= 0, "Elapsed time should be non-negative");
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

        ErrorRegistryView fooView = registry.forRoute("foo");
        assertEquals(1, fooView.size());
        BacklogErrorEventMessage fooEntry = fooView.browse().iterator().next();
        assertEquals("foo", fooEntry.getRouteId());

        ErrorRegistryView barView = registry.forRoute("bar");
        assertEquals(1, barView.size());

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

        assertEquals(2, context.getErrorRegistry().size());
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

    @Test
    public void testErrorRegistryCapturesUnhandledError() throws Exception {
        try {
            template.sendBody("direct:unhandled", "Hello World");
        } catch (Exception e) {
            // expected
        }

        ErrorRegistry registry = context.getErrorRegistry();
        Collection<BacklogErrorEventMessage> entries = registry.browse();
        assertEquals(1, entries.size());

        BacklogErrorEventMessage entry = entries.iterator().next();
        assertNotNull(entry.getExchangeId());
        assertEquals("unhandled", entry.getRouteId());
        assertFalse(entry.isHandled());
        assertEquals("java.lang.IllegalArgumentException", entry.getExceptionType());
        assertEquals("Unhandled error", entry.getExceptionMessage());
    }

    @Test
    public void testErrorRegistryCapturesEndpointUri() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:withEndpoint", "Hello World");
        assertMockEndpointsSatisfied();

        BacklogErrorEventMessage entry = context.getErrorRegistry().browse().iterator().next();
        assertNotNull(entry.getEndpointUri());
        assertTrue(entry.getEndpointUri().contains("direct://fail"),
                "Expected endpoint URI to contain direct://fail but was: " + entry.getEndpointUri());
    }

    @Test
    public void testErrorRegistryCapturesMessageHistory() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        BacklogErrorEventMessage entry = context.getErrorRegistry().browse().iterator().next();
        assertNotNull(entry.getMessageHistory(), "Message history should be captured when enabled");
        assertTrue(entry.getMessageHistory().length > 0, "Message history should have at least one entry");
        assertTrue(entry.getMessageHistory()[0].contains("foo"), "Message history should contain route id");
    }

    @Test
    public void testErrorRegistryCapturesExchangeData() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBodyAndHeader("direct:start", "Hello World", "MyHeader", "MyValue");
        assertMockEndpointsSatisfied();

        BacklogErrorEventMessage entry = context.getErrorRegistry().browse().iterator().next();
        assertNotNull(entry.getMessageAsJSon());
        assertTrue(entry.getMessageAsJSon().contains("MyHeader"),
                "Message JSON should contain header name");
        assertTrue(entry.getMessageAsJSon().contains("MyValue"),
                "Message JSON should contain header value");
        assertTrue(entry.getMessageAsJSon().contains("Hello World"),
                "Message JSON should contain body");
    }

    @Test
    public void testErrorRegistryToJson() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        BacklogErrorEventMessage entry = context.getErrorRegistry().browse().iterator().next();
        String json = entry.toJSon(2);
        assertNotNull(json);
        assertTrue(json.contains("\"exchangeId\""));
        assertTrue(json.contains("\"routeId\""));
        assertTrue(json.contains("\"handled\""));
        assertTrue(json.contains("\"exception\""));
        assertTrue(json.contains("\"message\""));
    }

    @Test
    public void testErrorRegistryAsJson() throws Exception {
        getMockEndpoint("mock:dead").expectedMessageCount(1);
        template.sendBody("direct:start", "Hello World");
        assertMockEndpointsSatisfied();

        BacklogErrorEventMessage entry = context.getErrorRegistry().browse().iterator().next();
        Map<String, Object> json = entry.asJSon();
        assertNotNull(json);
        assertEquals("foo", json.get("routeId"));
        assertEquals(true, json.get("handled"));
        assertNotNull(json.get("exchangeId"));
        assertNotNull(json.get("exception"));
        assertNotNull(json.get("message"));
        assertEquals("direct://start", json.get("fromEndpointUri"));
        assertTrue((long) json.get("routeUptime") >= 0);
        assertTrue((long) json.get("elapsed") >= 0);
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

                from("direct:unhandled").routeId("unhandled")
                        .errorHandler(noErrorHandler())
                        .throwException(new IllegalArgumentException("Unhandled error"));

                from("direct:withEndpoint").routeId("withEndpoint")
                        .to("direct:fail");

                from("direct:fail").routeId("failRoute")
                        .throwException(new IllegalArgumentException("Endpoint error"));
            }
        };
    }
}
