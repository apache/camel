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

import java.io.PrintWriter;
import java.io.StringWriter;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jetty10.JettyHttpComponent10;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test to verify that the Jetty HTTP connector is correctly disconnected on shutdown
 */
@Isolated
public class JettyHttpEndpointDisconnectTest extends BaseJettyTest {

    private String serverUri = "http://localhost:" + getPort() + "/myservice";

    @Test
    public void testContextShutdownRemovesHttpConnector() {
        context.stop();
        assertEquals(0, JettyHttpComponent.CONNECTORS.size(),
                () -> {
                    StringBuilder sb = new StringBuilder("Connector should have been removed\n");
                    for (String key : JettyHttpComponent.CONNECTORS.keySet()) {
                        Throwable t = JettyHttpComponent10.connectorCreation.get(key);
                        if (t == null) {
                            t = new Throwable("Unable to find connector creation");
                        }
                        StringWriter sw = new StringWriter();
                        try (PrintWriter pw = new PrintWriter(sw)) {
                            t.printStackTrace(pw);
                        }
                        sb.append(key).append(": ").append(sw.toString());
                    }
                    return sb.toString();
                });
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
