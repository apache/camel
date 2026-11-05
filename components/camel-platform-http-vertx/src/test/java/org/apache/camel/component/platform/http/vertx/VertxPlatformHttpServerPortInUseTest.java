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
package org.apache.camel.component.platform.http.vertx;

import java.net.BindException;
import java.net.ServerSocket;

import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VertxPlatformHttpServerPortInUseTest {
    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Test
    void startFailsWhenPortIsInUse() throws Exception {
        try (ServerSocket portInUse = new ServerSocket(port.getPort());
             CamelContext context = new DefaultCamelContext()) {
            VertxPlatformHttpServerConfiguration configuration = new VertxPlatformHttpServerConfiguration();
            configuration.setBindPort(port.getPort());
            VertxPlatformHttpServer server = new VertxPlatformHttpServer(configuration);

            context.addService(server);
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("platform-http:/test")
                            .setBody().constant("Hello World");
                }
            });

            RuntimeCamelException e = assertThrows(RuntimeCamelException.class, context::start);
            assertInstanceOf(BindException.class, e.getCause());
            assertTrue(e.getMessage().contains(":" + port.getPort()), e.getMessage());
            assertFalse(context.isStarted());

            // the failed start stopped the server, so it holds neither the HTTP server nor its own Vert.x instance
            assertNull(server.getServer());
            assertNull(server.getVertx());
        }
    }
}
