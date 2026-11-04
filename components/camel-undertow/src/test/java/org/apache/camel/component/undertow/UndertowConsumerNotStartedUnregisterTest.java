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
package org.apache.camel.component.undertow;

import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.common.http.WebsocketTestClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An undertow consumer (or WebSocket producer) that never started, or whose start failed, must not unregister the
 * handler of a running consumer of the same path when it is stopped or its route is removed.
 */
class UndertowConsumerNotStartedUnregisterTest extends BaseUndertowTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/dup").routeId("running").transform().constant("running");
            }
        };
    }

    @Test
    void controlRestartingTheRunningRoute() throws Exception {
        // control: a consumer that stops unregisters its path, and registers it again when it starts again
        addRoute("undertow:http://localhost:{{port}}/other", "other");
        assertEquals("running", get("/dup"));

        context.getRouteController().stopRoute("running");
        assertEquals("No matching path found", get("/dup"));
        context.getRouteController().startRoute("running");

        assertEquals("running", get("/dup"));
        context.getRouteController().stopRoute("running");
        context.getRouteController().startRoute("running");
        assertEquals("running", get("/dup"));
    }

    @Test
    void removingANeverStartedRouteKeepsThePathOfTheRunningRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/other").routeId("other").transform().constant("other");
                from("undertow:http://localhost:{{port}}/dup").routeId("idle").autoStartup(false)
                        .transform().constant("idle");
            }
        });
        assertEquals("running", get("/dup"));

        context.removeRoute("idle");

        assertEquals("running", get("/dup"));
        assertEquals("other", get("/other"));
    }

    @Test
    void removingANeverStartedRouteKeepsAStaticPathNextToATemplatePath() throws Exception {
        // the second case of the JIRA, with CAMEL-26460: a template path /orders/{id} and a static /orders are running,
        // and a third /orders route that never started is removed: both paths still answer
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/orders/{id}").routeId("template")
                        .transform().constant("template");
                from("undertow:http://localhost:{{port}}/orders").routeId("static").transform().constant("static");
                from("undertow:http://localhost:{{port}}/orders").routeId("idle").autoStartup(false)
                        .transform().constant("idle");
            }
        });
        assertEquals("static", get("/orders"));
        assertEquals("template", get("/orders/1"));

        context.removeRoute("idle");

        assertEquals("static", get("/orders"));
        assertEquals("template", get("/orders/1"));
    }

    @Test
    void removingANeverStartedRouteKeepsTheServerOfTheRunningRoute() throws Exception {
        // the path of the running route is the only path of the server: the server must not stop
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:http://localhost:{{port}}/dup").routeId("idle").autoStartup(false)
                        .transform().constant("idle");
            }
        });
        assertEquals("running", get("/dup"));

        context.removeRoute("idle");

        assertEquals("running", get("/dup"));
    }

    @Test
    void removingARouteWhoseStartFailedKeepsThePathOfTheRunningRoute() throws Exception {
        // a second consumer of the same path and method (another endpoint, as one endpoint cannot have two consumers)
        // fails to start, as the path already has a handler
        assertStartFails("undertow:http://localhost:{{port}}/dup?matchOnUriPrefix=false", "failed",
                "Duplicate handler for ");
        assertEquals("running", get("/dup"));

        context.getRouteController().stopRoute("failed");
        context.removeRoute("failed");

        assertEquals("running", get("/dup"));
    }

    @Test
    void routeWhoseStartFailedStartsOnceThePathIsFree() throws Exception {
        assertStartFails("undertow:http://localhost:{{port}}/dup?matchOnUriPrefix=false", "failed",
                "Duplicate handler for ");
        // a second start while the path is still taken fails too, and keeps the path of the running route
        assertThrows(FailedToStartRouteException.class, () -> context.getRouteController().startRoute("failed"));
        assertEquals("running", get("/dup"));

        context.getRouteController().stopRoute("running");
        context.getRouteController().startRoute("failed");

        assertEquals("failed", get("/dup"));
    }

    @Test
    void removingARouteWhoseStartFailedWithSeveralMethodsKeepsNoMethod() throws Exception {
        addRoute("undertow:http://localhost:{{port}}/mm?httpMethodRestrict=POST,OPTIONS", "post");
        // GET is free, but POST already has a handler, so the start fails: GET must not stay registered
        assertStartFails("undertow:http://localhost:{{port}}/mm?httpMethodRestrict=GET,POST", "failed",
                "Duplicate handler for POST method");
        context.getRouteController().stopRoute("failed");
        context.removeRoute("failed");

        // nor in the methods that OPTIONS lists
        assertEquals("POST,OPTIONS", call("/mm", "OPTIONS").getMessage().getHeader("Allow"));
        // a route for GET alone can start, and the route for POST still answers
        addRoute("undertow:http://localhost:{{port}}/mm?httpMethodRestrict=GET", "get");
        assertEquals("get", get("/mm"));
        assertEquals("post", call("/mm", "POST").getMessage().getBody(String.class));
    }

    @Test
    void removingAWebSocketRouteWhoseStartFailedKeepsTheRunningConsumer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:ws://localhost:{{port}}/ws").routeId("ws").to("mock:ws");
            }
        });
        // a second consumer of the same WebSocket path fails to start, as the WebSocket handler of the path already has
        // a consumer (sendTimeout only makes it another endpoint, as one endpoint cannot have two consumers)
        assertStartFails("undertow:ws://localhost:{{port}}/ws?sendTimeout=30001", "ws-failed", "setConsumer");
        context.getRouteController().stopRoute("ws-failed");
        context.removeRoute("ws-failed");

        assertWebSocketRouteReceives("Hello");

        // the failed consumer had registered with the shared WebSocket handler of the path, and unregistered when it
        // was removed: once the running route is removed too, the path is gone
        context.getRouteController().stopRoute("ws");
        context.removeRoute("ws");
        assertThrows(CompletionException.class,
                () -> new WebsocketTestClient("ws://localhost:" + getPort() + "/ws").connect());
    }

    @Test
    void stoppingAWebSocketProducerThatNeverStartedKeepsTheRunningConsumer() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("undertow:ws://localhost:{{port}}/ws").routeId("ws").to("mock:ws");
            }
        });
        Producer producer = context.getEndpoint("undertow:ws://localhost:{{port}}/ws").createProducer();
        producer.stop();

        assertWebSocketRouteReceives("Hello");
    }

    private void addRoute(String uri, String routeId) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId(routeId).transform().constant(routeId);
            }
        });
    }

    private void assertStartFails(String uri, String routeId, String message) {
        FailedToStartRouteException e = assertThrows(FailedToStartRouteException.class, () -> addRoute(uri, routeId));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    private String get(String path) {
        return template.requestBody("undertow:http://localhost:{{port}}" + path + "?throwExceptionOnFailure=false",
                null, String.class);
    }

    private Exchange call(String path, String method) {
        return template.request("undertow:http://localhost:{{port}}" + path + "?throwExceptionOnFailure=false",
                e -> e.getIn().setHeader(Exchange.HTTP_METHOD, method));
    }

    private void assertWebSocketRouteReceives(String message) throws Exception {
        MockEndpoint ws = getMockEndpoint("mock:ws");
        ws.expectedBodiesReceived(message);
        WebsocketTestClient client = new WebsocketTestClient("ws://localhost:" + getPort() + "/ws");
        client.connect();
        try {
            client.sendTextMessage(message);
            MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        } finally {
            client.close();
        }
    }
}
