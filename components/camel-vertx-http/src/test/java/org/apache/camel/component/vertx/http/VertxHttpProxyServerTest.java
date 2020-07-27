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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VertxHttpProxyServerTest extends VertxHttpTestSupport {

    private int port2 = AvailablePortFinder.getNextAvailable();

    @Test
    public void testProxyConfiguration() {
        String result = template.requestBody(getProducerUri() + "?proxyHost=localhost&proxyPort="
                                             + port2 + "&proxyUsername=foo"
                                             + "&proxyPassword=bar&proxyType=HTTP",
                null, String.class);
        assertEquals("Hello Proxied World", result);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(getTestServerUri())
                        .setBody(constant("Hello Proxied World"));

                fromF("undertow:http://localhost:%d", port2)
                        .to(getTestServerUri());
            }
        };
    }
}
