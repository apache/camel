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
package org.apache.camel.dsl.jbang.core.commands.tui;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusParserTest {

    // ---- parseIntegration tests ----

    @Test
    void parseIntegrationMinimal() {
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "myIntegration");
        context.put("version", "4.21.0");
        context.put("phase", 5);
        root.put("context", context);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertEquals("myIntegration", info.name);
        assertEquals("4.21.0", info.camelVersion);
        assertEquals(5, info.state);
    }

    @Test
    void parseIntegrationReturnsNullWhenNoContext() {
        JsonObject root = new JsonObject();
        ProcessHandle ph = ProcessHandle.current();

        IntegrationInfo info = StatusParser.parseIntegration(ph, root);
        assertNull(info);
    }

    @Test
    void parseIntegrationWithRoutes() {
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "routeTest");
        root.put("context", context);

        JsonArray routes = new JsonArray();
        JsonObject route = new JsonObject();
        route.put("routeId", "route1");
        route.put("from", "timer:tick");
        route.put("state", "Started");
        routes.add(route);
        root.put("routes", routes);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertEquals(1, info.routes.size());
        assertEquals("route1", info.routes.get(0).routeId);
        assertEquals("timer:tick", info.routes.get(0).from);
        assertEquals("Started", info.routes.get(0).state);
        assertEquals(1, info.routeTotal);
        assertEquals(1, info.routeStarted);
    }

    @Test
    void parseIntegrationWithHealth() {
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "healthTest");
        root.put("context", context);

        JsonObject healthChecks = new JsonObject();
        healthChecks.put("ready", true);
        JsonArray checks = new JsonArray();
        JsonObject check = new JsonObject();
        check.put("group", "readiness");
        check.put("id", "context");
        check.put("state", "UP");
        check.put("readiness", true);
        checks.add(check);
        healthChecks.put("checks", checks);
        root.put("healthChecks", healthChecks);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertEquals("1/1", info.ready);
        assertEquals(1, info.healthChecks.size());
        assertEquals("context", info.healthChecks.get(0).name);
        assertEquals("UP", info.healthChecks.get(0).state);
    }

    @Test
    void parseIntegrationWithEndpoints() {
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "endpointTest");
        root.put("context", context);

        JsonObject endpointsObj = new JsonObject();
        JsonArray endpointList = new JsonArray();
        JsonObject ep = new JsonObject();
        ep.put("uri", "timer:tick");
        ep.put("direction", "in");
        ep.put("routeId", "route1");
        ep.put("hits", 42L);
        endpointList.add(ep);
        endpointsObj.put("endpoints", endpointList);
        root.put("endpoints", endpointsObj);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertEquals(1, info.endpoints.size());
        assertEquals("timer:tick", info.endpoints.get(0).uri);
        assertEquals("timer", info.endpoints.get(0).component);
        assertEquals(42L, info.endpoints.get(0).hits);
    }

    @Test
    void parseIntegrationWithCircuitBreakers() {
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "cbTest");
        root.put("context", context);

        JsonObject r4j = new JsonObject();
        JsonArray breakers = new JsonArray();
        JsonObject cb = new JsonObject();
        cb.put("routeId", "route1");
        cb.put("id", "cb1");
        cb.put("state", "CLOSED");
        cb.put("failureRate", 0.5);
        breakers.add(cb);
        r4j.put("circuitBreakers", breakers);
        root.put("resilience4j", r4j);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertEquals(1, info.circuitBreakers.size());
        assertEquals("resilience4j", info.circuitBreakers.get(0).component);
        assertEquals("CLOSED", info.circuitBreakers.get(0).state);
        assertEquals(0.5, info.circuitBreakers.get(0).failureRate);
    }

    @Test
    void parseIntegrationWithErrors() {
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "errorTest");
        root.put("context", context);

        JsonObject errorsObj = new JsonObject();
        errorsObj.put("size", 3);
        root.put("errors", errorsObj);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertEquals(3, info.errorCount);
    }

    @Test
    void parseIntegrationWithInflight() {
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "inflightTest");
        root.put("context", context);

        JsonObject inflightObj = new JsonObject();
        inflightObj.put("inflightBrowseEnabled", true);
        inflightObj.put("inflight", 1);
        JsonArray exchanges = new JsonArray();
        JsonObject ex = new JsonObject();
        ex.put("exchangeId", "ID-1");
        ex.put("fromRouteId", "route1");
        ex.put("atRouteId", "route1");
        ex.put("nodeId", "node1");
        ex.put("elapsed", 100L);
        ex.put("duration", 200L);
        exchanges.add(ex);
        inflightObj.put("exchanges", exchanges);
        root.put("inflight", inflightObj);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertTrue(info.inflightBrowseEnabled);
        assertEquals(1, info.inflightExchanges.size());
        assertEquals("ID-1", info.inflightExchanges.get(0).exchangeId);
        assertFalse(info.inflightExchanges.get(0).blocked);
    }

    @Test
    void parseIntegrationWithHttpEndpoints() {
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "httpTest");
        root.put("context", context);

        JsonObject restsObj = new JsonObject();
        JsonArray restList = new JsonArray();
        JsonObject rest = new JsonObject();
        rest.put("url", "http://localhost:8080/api/hello");
        rest.put("method", "get");
        rest.put("routeId", "route1");
        rest.put("hits", 10L);
        restList.add(rest);
        restsObj.put("rests", restList);
        root.put("rests", restsObj);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertEquals(1, info.httpEndpoints.size());
        assertEquals("GET", info.httpEndpoints.get(0).method);
        assertTrue(info.httpEndpoints.get(0).fromRest);
    }

    @Test
    void parseIntegrationMissingSectionsDoNotNpe() {
        // A minimal JSON with only context — all other sections missing should not NPE
        JsonObject root = new JsonObject();
        JsonObject context = new JsonObject();
        context.put("name", "minimal");
        root.put("context", context);

        ProcessHandle ph = ProcessHandle.current();
        IntegrationInfo info = StatusParser.parseIntegration(ph, root);

        assertNotNull(info);
        assertEquals("minimal", info.name);
        assertTrue(info.routes.isEmpty());
        assertTrue(info.healthChecks.isEmpty());
        assertTrue(info.endpoints.isEmpty());
        assertTrue(info.circuitBreakers.isEmpty());
        assertTrue(info.inflightExchanges.isEmpty());
    }

    // ---- parseTraceEntry tests ----

    @Test
    void parseTraceEntryFirstDirection() {
        JsonObject json = new JsonObject();
        json.put("uid", "uid1");
        json.put("exchangeId", "EX-1");
        json.put("routeId", "route1");
        json.put("nodeId", "node1");
        json.put("first", true);
        json.put("last", false);
        json.put("done", false);
        json.put("failed", false);
        json.put("endpointUri", "timer:tick");
        json.put("nodeLevel", 1);
        json.put("elapsed", 42L);
        json.put("timestamp", 1700000000000L);

        TraceEntry entry = StatusParser.parseTraceEntry(json, "12345");
        assertEquals("12345", entry.pid);
        assertEquals("*-> ", entry.direction);
        assertEquals("Processing", entry.status);
        assertTrue(entry.processor.contains("from[timer:tick]"));
        assertEquals(42L, entry.elapsed);
    }

    @Test
    void parseTraceEntryLastDirection() {
        JsonObject json = new JsonObject();
        json.put("uid", "uid2");
        json.put("exchangeId", "EX-2");
        json.put("last", true);
        json.put("first", false);
        json.put("done", true);
        json.put("failed", false);
        json.put("nodeLabel", "log:result");
        json.put("nodeLevel", 1);
        json.put("timestamp", 1700000000000L);

        TraceEntry entry = StatusParser.parseTraceEntry(json, "12345");
        assertEquals("<-* ", entry.direction);
        assertEquals("Done", entry.status);
    }

    @Test
    void parseTraceEntryRemoteDirection() {
        JsonObject json = new JsonObject();
        json.put("uid", "uid3");
        json.put("first", true);
        json.put("last", false);
        json.put("done", false);
        json.put("failed", false);
        json.put("remoteEndpoint", true);
        json.put("endpointUri", "http://example.com");
        json.put("nodeLevel", 1);
        json.put("timestamp", 1700000000000L);

        TraceEntry entry = StatusParser.parseTraceEntry(json, "12345");
        assertEquals("*-->", entry.direction);
    }

    @Test
    void parseTraceEntryFailedStatus() {
        JsonObject json = new JsonObject();
        json.put("uid", "uid4");
        json.put("first", false);
        json.put("last", false);
        json.put("done", true);
        json.put("failed", true);
        json.put("nodeLabel", "process");
        json.put("nodeLevel", 0);

        TraceEntry entry = StatusParser.parseTraceEntry(json, "12345");
        assertEquals("Failed", entry.status);
        assertTrue(entry.failed);
    }

    @Test
    void parseTraceEntryWithException() {
        JsonObject json = new JsonObject();
        json.put("uid", "uid5");
        json.put("first", false);
        json.put("last", false);
        json.put("done", true);
        json.put("failed", true);
        json.put("nodeLabel", "process");
        json.put("nodeLevel", 0);

        JsonObject exc = new JsonObject();
        exc.put("message", "NullPointerException");
        exc.put("stackTrace", "at Foo.bar(Foo.java:42)");
        json.put("exception", exc);

        TraceEntry entry = StatusParser.parseTraceEntry(json, "12345");
        assertNotNull(entry.exception);
        assertTrue(entry.exception.contains("NullPointerException"));
        assertTrue(entry.exception.contains("at Foo.bar"));
    }

    @Test
    void parseTraceEntryStubDirection() {
        JsonObject json = new JsonObject();
        json.put("uid", "uid6");
        json.put("first", true);
        json.put("last", false);
        json.put("done", false);
        json.put("failed", false);
        json.put("stubEndpoint", true);
        json.put("endpointUri", "stub:test");
        json.put("nodeLevel", 1);

        TraceEntry entry = StatusParser.parseTraceEntry(json, "12345");
        assertEquals("~-->", entry.direction);
    }

    // ---- parseMessage tests ----

    @Test
    void parseMessageWithHeadersAsListOfKv() {
        JsonObject message = new JsonObject();
        JsonArray headers = new JsonArray();
        JsonObject h1 = new JsonObject();
        h1.put("key", "Content-Type");
        h1.put("value", "text/plain");
        h1.put("type", "java.lang.String");
        headers.add(h1);
        message.put("headers", headers);

        StatusParser.MessageData md = StatusParser.parseMessage(message);
        assertNotNull(md.headers());
        assertEquals("text/plain", md.headers().get("Content-Type"));
        assertEquals("String", md.headerTypes().get("Content-Type"));
    }

    @Test
    void parseMessageWithHeadersAsMap() {
        JsonObject message = new JsonObject();
        Map<String, Object> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        message.put("headers", headers);

        StatusParser.MessageData md = StatusParser.parseMessage(message);
        assertNotNull(md.headers());
        assertEquals("application/json", md.headers().get("Accept"));
    }

    @Test
    void parseMessageWithBodyAsJsonObject() {
        JsonObject message = new JsonObject();
        JsonObject body = new JsonObject();
        body.put("value", "Hello World");
        body.put("type", "java.lang.String");
        message.put("body", body);

        StatusParser.MessageData md = StatusParser.parseMessage(message);
        assertEquals("Hello World", md.body());
        assertEquals("String", md.bodyType());
    }

    @Test
    void parseMessageWithBodyAsRawString() {
        JsonObject message = new JsonObject();
        message.put("body", "plain text body");

        StatusParser.MessageData md = StatusParser.parseMessage(message);
        assertEquals("plain text body", md.body());
    }

    @Test
    void parseMessageWithExchangeProperties() {
        JsonObject message = new JsonObject();
        JsonArray props = new JsonArray();
        JsonObject p1 = new JsonObject();
        p1.put("key", "CamelToEndpoint");
        p1.put("value", "direct:end");
        p1.put("type", "java.lang.String");
        props.add(p1);
        message.put("exchangeProperties", props);

        StatusParser.MessageData md = StatusParser.parseMessage(message);
        assertNotNull(md.exchangeProperties());
        assertEquals("direct:end", md.exchangeProperties().get("CamelToEndpoint"));
    }

    @Test
    void parseMessageWithExchangeVariables() {
        JsonObject message = new JsonObject();
        JsonArray vars = new JsonArray();
        JsonObject v1 = new JsonObject();
        v1.put("key", "myVar");
        v1.put("value", "varValue");
        v1.put("type", "java.lang.String");
        vars.add(v1);
        message.put("exchangeVariables", vars);

        StatusParser.MessageData md = StatusParser.parseMessage(message);
        assertNotNull(md.exchangeVariables());
        assertEquals("varValue", md.exchangeVariables().get("myVar"));
    }

    @Test
    void parseMessageEmptyReturnsNulls() {
        JsonObject message = new JsonObject();

        StatusParser.MessageData md = StatusParser.parseMessage(message);
        assertNull(md.headers());
        assertNull(md.body());
        assertNull(md.exchangeProperties());
        assertNull(md.exchangeVariables());
    }
}
