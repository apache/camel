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
import org.apache.camel.spi.HeaderFilterStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exchange headers keep the casing of the inbound request, and HTTP/2 requires field names to be lower case. An
 * exact-case lookup against canonically capitalised names therefore never suppresses anything on an HTTP/2 request,
 * which is the traffic most likely to carry the credentials this is meant to keep out of the response.
 */
class PlatformHttpEndpointHeaderEchoTest {

    @Test
    void requestHeadersAreSuppressedWhateverTheirCasing() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();
            PlatformHttpComponent component = new PlatformHttpComponent(context);
            PlatformHttpEndpoint endpoint
                    = (PlatformHttpEndpoint) component.createEndpoint("platform-http:/test");

            HeaderFilterStrategy strategy = endpoint.getHeaderFilterStrategy();

            // canonical, as sent over HTTP/1.1
            assertTrue(strategy.applyFilterToCamelHeaders("Authorization", "Bearer x", null));
            assertTrue(strategy.applyFilterToCamelHeaders("Cookie", "a=b", null));
            // lower case, as required by HTTP/2
            assertTrue(strategy.applyFilterToCamelHeaders("authorization", "Bearer x", null));
            assertTrue(strategy.applyFilterToCamelHeaders("cookie", "a=b", null));
            assertTrue(strategy.applyFilterToCamelHeaders("proxy-authorization", "Basic x", null));
            // and any other casing a client might send
            assertTrue(strategy.applyFilterToCamelHeaders("AUTHORIZATION", "Bearer x", null));
        }
    }
}
