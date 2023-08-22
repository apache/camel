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
package org.apache.camel.component.netty.http;

import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.netty.http.NettyHttpEndpoint.PROXY_NOT_SUPPORTED_MESSAGE;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NettyHttpProducerProxyModeTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static final int port = AvailablePortFinder.getNextAvailable();

    @Test
    public void testProxyNotSupported() throws Exception {

        context.addRoutes(new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:start")
                        .routeId("proxy-producer")
                        .to("netty-http:proxy://localhost:" + port + "/foo");
            }
        });

        FailedToStartRouteException thrown = assertThrows(FailedToStartRouteException.class, () -> {
            context.start();
        }, PROXY_NOT_SUPPORTED_MESSAGE);

        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().contains(PROXY_NOT_SUPPORTED_MESSAGE));
    }
}
