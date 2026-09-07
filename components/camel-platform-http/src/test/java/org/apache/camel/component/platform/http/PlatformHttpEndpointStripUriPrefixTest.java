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
 * The stripUriPrefix consumer option must be provably inert for platform-http:proxy mode: proxy mode makes
 * {@link PlatformHttpEndpoint#getPath()} return "/", and {@link org.apache.camel.http.base.HttpHelper#stripUriPrefix}
 * treats a consumer path of "/" as "no prefix to strip", so enabling stripUriPrefix on a proxy endpoint must never
 * change its behavior.
 */
class PlatformHttpEndpointStripUriPrefixTest {

    @Test
    void stripUriPrefixDefaultsToFalse() throws Exception {
        assertFalse(stripUriPrefixOf("platform-http:/reverse-proxy"));
        assertFalse(stripUriPrefixOf("platform-http:/reverse-proxy?matchOnUriPrefix=true"));
    }

    @Test
    void stripUriPrefixCanBeEnabled() throws Exception {
        assertTrue(stripUriPrefixOf("platform-http:/reverse-proxy?stripUriPrefix=true"));
        assertTrue(stripUriPrefixOf("platform-http:/reverse-proxy?matchOnUriPrefix=true&stripUriPrefix=true"));
    }

    @Test
    void proxyModeIsUnaffectedByStripUriPrefix() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            PlatformHttpComponent component = new PlatformHttpComponent(context);
            PlatformHttpEndpoint endpoint
                    = (PlatformHttpEndpoint) component.createEndpoint("platform-http:proxy?stripUriPrefix=true");

            // the option is honored as a plain bean property...
            assertTrue(endpoint.isStripUriPrefix());
            // ...but proxy mode detection and the effective consumer path are unaffected by it
            assertTrue(endpoint.isHttpProxy());
            assertEquals("/", endpoint.getPath());
        }
    }

    private static boolean stripUriPrefixOf(String uri) throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            PlatformHttpComponent component = new PlatformHttpComponent(context);
            return ((PlatformHttpEndpoint) component.createEndpoint(uri)).isStripUriPrefix();
        }
    }
}
