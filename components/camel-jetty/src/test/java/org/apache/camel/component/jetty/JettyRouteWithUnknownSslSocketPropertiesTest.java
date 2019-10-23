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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.eclipse.jetty.server.Server;
import org.junit.Test;

public class JettyRouteWithUnknownSslSocketPropertiesTest extends BaseJettyTest {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testUnknownProperty() throws Exception {
        if (!Server.getVersion().startsWith("8")) {
            // SocketConnector props do not work for jetty 9
            return;
        }
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // define socket connector properties
                Map<String, Object> properties = new HashMap<>();
                properties.put("acceptors", 4);
                properties.put("statsOn", "false");
                properties.put("soLingerTime", "5000");
                properties.put("doesNotExist", 2000);

                JettyHttpComponent jetty = getContext().getComponent("jetty", JettyHttpComponent.class);
                jetty.setSslSocketConnectorProperties(properties);

                from("jetty:https://localhost:{{port}}/myapp/myservice").to("log:foo");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            IllegalArgumentException iae = assertIsInstanceOf(IllegalArgumentException.class, e.getCause());
            assertTrue("Actual message: " + iae.getMessage(), iae.getMessage().endsWith("Unknown parameters=[{doesNotExist=2000}]"));
        }
    }

}
