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
package org.apache.camel.component.platform.http;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proxy mode makes the endpoint a catch-all whose forward target comes from the request's own Host header. Selecting it
 * by prefix meant any path merely beginning with "proxy" became a forwarding proxy its author never asked for.
 */
class PlatformHttpEndpointProxyPathTest {

    @Test
    void onlyTheProxyPathSelectsProxyMode() throws Exception {
        assertTrue(isProxy("platform-http:proxy"));

        // a leading slash did not select proxy mode before the check was tightened, and still does not:
        // narrowing the check must never turn an endpoint into a proxy that was not already one
        assertFalse(isProxy("platform-http:/proxy"));

        assertFalse(isProxy("platform-http:proxyStats"));
        assertFalse(isProxy("platform-http:proxy-health"));
        assertFalse(isProxy("platform-http:proxying"));
        assertFalse(isProxy("platform-http:/orders"));
    }

    @Test
    void aNonProxyPathIsNotTurnedIntoACatchAll() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            PlatformHttpComponent component = new PlatformHttpComponent(context);
            PlatformHttpEndpoint endpoint
                    = (PlatformHttpEndpoint) component.createEndpoint("platform-http:proxyStats");

            assertEquals("proxyStats", endpoint.getPath());
        }
    }

    private static boolean isProxy(String uri) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            PlatformHttpComponent component = new PlatformHttpComponent(context);
            return ((PlatformHttpEndpoint) component.createEndpoint(uri)).isHttpProxy();
        }
    }
}
