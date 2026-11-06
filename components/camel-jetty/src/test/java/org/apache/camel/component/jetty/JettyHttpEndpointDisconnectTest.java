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
package org.apache.camel.component.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty12.JettyHttpComponent12;
import org.apache.camel.support.ExceptionHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit test to verify that the Jetty HTTP connector is correctly disconnected on shutdown
 */
@Isolated
public class JettyHttpEndpointDisconnectTest extends BaseJettyTest {

    private static final Logger LOG = LoggerFactory.getLogger(JettyHttpEndpointDisconnectTest.class);

    private final String serverUri = "http://localhost:" + getPort() + "/myservice";

    @Test
    public void testContextShutdownRemovesHttpConnector() {
        context.stop();
        // only check the connector of this test, as the connectors are static and a connector
        // left behind by another test in the same JVM must not make this test fail
        String connectorKey = "http:localhost:" + getPort();
        logOtherConnectors(connectorKey);
        assertFalse(JettyHttpComponent.CONNECTORS.containsKey(connectorKey),
                () -> {
                    Throwable t = JettyHttpComponent12.connectorCreation.get(connectorKey);
                    if (t == null) {
                        t = new Throwable("Unable to find connector creation");
                    }
                    return "Connector should have been removed\n" + connectorKey + ": "
                           + ExceptionHelper.stackTraceToString(t);
                });
    }

    /**
     * Logs the connectors still registered that do not belong to this test, with where they were created, to help
     * finding which test leaks them.
     */
    private void logOtherConnectors(String connectorKey) {
        if (!LOG.isDebugEnabled()) {
            return;
        }
        for (String key : JettyHttpComponent.CONNECTORS.keySet()) {
            if (!key.equals(connectorKey)) {
                Throwable t = JettyHttpComponent12.connectorCreation.get(key);
                LOG.debug("Connector left behind by another test: {}, created at: {}", key,
                        t != null ? ExceptionHelper.stackTraceToString(t) : "unknown");
            }
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jetty:" + serverUri).to("mock:result");
            }
        };
    }
}
