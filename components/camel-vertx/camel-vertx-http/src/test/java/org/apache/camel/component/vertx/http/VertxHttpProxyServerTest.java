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
package org.apache.camel.component.vertx.http;

import io.vertx.core.net.ProxyType;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxHttpProxyServerTest extends VertxHttpTestSupport {

    private final int port2 = AvailablePortFinder.getNextAvailable();

    @Test
    public void testProxyConfiguration() {
        String result = template.requestBody(getProducerUri() + "?proxyHost=localhost&proxyPort="
                                             + port2 + "&proxyUsername=foo"
                                             + "&proxyPassword=bar&proxyType=HTTP",
                null, String.class);
        assertEquals("Hello Proxied World", result);
    }

    @Test
    public void testProxyComponent() {
        VertxHttpComponent comp = context.getComponent("vertx-http", VertxHttpComponent.class);
        comp.setProxyHost("localhost");
        comp.setProxyPort(port2);
        comp.setProxyUsername("foo");
        comp.setProxyPassword("bar");
        comp.setProxyType(ProxyType.HTTP);

        String result = template.requestBody(getProducerUri(), null, String.class);
        assertEquals("Hello Proxied World", result);

        comp.setProxyHost(null);
        comp.setProxyPort(null);
        comp.setProxyUsername(null);
        comp.setProxyPassword(null);
        comp.setProxyType(null);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(getTestServerUri())
                        .setBody(constant("Hello Proxied World"));

                fromF("undertow:http://localhost:%d", port2)
                        .to(getTestServerUri());
            }
        };
    }
}
