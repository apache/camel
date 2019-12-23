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
import org.junit.Test;

/**
 * Unit test to verify that the Jetty HTTP connector is correctly disconnected
 * on shutdown
 */
public class JettyHttpEndpointDisconnectTest extends BaseJettyTest {

    private String serverUri = "http://localhost:" + getPort() + "/myservice";

    @Test
    public void testContextShutdownRemovesHttpConnector() throws Exception {
        context.stop();
        assertEquals("Connector should have been removed", 0, JettyHttpComponent.CONNECTORS.size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("jetty:" + serverUri).to("mock:result");
            }
        };
    }
}
