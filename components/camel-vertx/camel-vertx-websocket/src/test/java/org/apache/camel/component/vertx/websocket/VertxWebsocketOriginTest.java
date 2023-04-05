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
package org.apache.camel.component.vertx.websocket;

import java.util.concurrent.ExecutionException;

import io.vertx.core.http.UpgradeRejectedException;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class VertxWebsocketOriginTest extends VertxWebSocketTestSupport {

    private static final int PORT2 = AvailablePortFinder.getNextAvailable();

    @Test
    public void testValidOrigin() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");
        template.sendBody("vertx-websocket:localhost:" + port + "/test", "world");
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testInvalidOrigin() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class, () -> {
            template.sendBody("vertx-websocket:localhost:" + PORT2 + "/test", "world");
        });

        UpgradeRejectedException upgradeRejectedException = unwrapException(e);
        assertNotNull(upgradeRejectedException);
        assertEquals(403, upgradeRejectedException.getStatus());
    }

    @Test
    public void testCustomOrigin() throws InterruptedException {
        String originUrl = "http://foohost:" + PORT2;
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");
        template.sendBody("vertx-websocket:localhost:" + PORT2 + "/test?originHeaderUrl=" + originUrl, "world");
        mockEndpoint.assertIsSatisfied();
    }

    @Test
    public void testOriginDisabled() throws InterruptedException {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedBodiesReceived("Hello world");
        template.sendBody("vertx-websocket:localhost:" + PORT2 + "/test?allowOriginHeader=false", "world");
        mockEndpoint.assertIsSatisfied();
    }

    private UpgradeRejectedException unwrapException(CamelExecutionException e) {
        Throwable cause = e.getCause();
        if (cause instanceof ExecutionException) {
            Throwable originalCause = cause.getCause();
            if (originalCause instanceof UpgradeRejectedException) {
                return (UpgradeRejectedException) originalCause;
            }
        }
        return null;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("vertx-websocket:localhost:%d/test?allowedOriginPattern=.*localhost.*", port)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");

                fromF("vertx-websocket:localhost:%d/test?allowedOriginPattern=.*foohost.*", PORT2)
                        .setBody(simple("Hello ${body}"))
                        .to("mock:result");
            }
        };
    }
}
