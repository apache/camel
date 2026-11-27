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
package org.apache.camel.component.tahu;

import java.util.UUID;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Eclipse Tahu creates its MQTT clients without TLS settings, so sslContextParameters fails the endpoint at start.
 */
public class TahuSslContextParametersTest extends CamelTestSupport {

    @Test
    public void edgeEndpointWithSslContextParametersFailsToStart() {
        context.getRegistry().bind("ssl", new SSLContextParameters());

        assertRouteFailsToStart(TahuConstants.EDGE_NODE_SCHEME + "://Ssl/EdgeNode?sslContextParameters=#ssl");
    }

    @Test
    public void hostEndpointWithSslContextParametersFailsToStart() {
        context.getRegistry().bind("ssl", new SSLContextParameters());

        assertRouteFailsToStart(TahuConstants.HOST_APP_SCHEME + ":SslHost?sslContextParameters=#ssl");
    }

    @Test
    public void globalSslContextParametersFailsToStart() {
        context.setSSLContextParameters(new SSLContextParameters());
        context.getComponent(TahuConstants.EDGE_NODE_SCHEME, TahuDefaultComponent.class)
                .setUseGlobalSslContextParameters(true);
        context.getComponent(TahuConstants.HOST_APP_SCHEME, TahuDefaultComponent.class)
                .setUseGlobalSslContextParameters(true);

        assertRouteFailsToStart(TahuConstants.EDGE_NODE_SCHEME + "://GlobalSsl/EdgeNode");
        assertRouteFailsToStart(TahuConstants.HOST_APP_SCHEME + ":GlobalSslHost");
    }

    @Test
    public void globalSslContextParametersNotUsedByDefault() {
        context.setSSLContextParameters(new SSLContextParameters());

        assertStarts(context.getEndpoint(
                TahuConstants.EDGE_NODE_SCHEME + "://NoGlobalSsl/EdgeNode", TahuDefaultEndpoint.class));
        assertStarts(context.getEndpoint(
                TahuConstants.HOST_APP_SCHEME + ":NoGlobalSslHost", TahuDefaultEndpoint.class));
    }

    @Test
    public void endpointsWithoutSslContextParametersStart() {
        assertStarts(context.getEndpoint(
                TahuConstants.EDGE_NODE_SCHEME + "://NoSsl/EdgeNode", TahuDefaultEndpoint.class));
        assertStarts(context.getEndpoint(
                TahuConstants.HOST_APP_SCHEME + ":NoSslHost", TahuDefaultEndpoint.class));
    }

    private void assertRouteFailsToStart(String uri) {
        Exception e = assertThrows(Exception.class, () -> context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:" + UUID.randomUUID()).to(uri);
            }
        }));
        IllegalArgumentException cause = ObjectHelper.getException(IllegalArgumentException.class, e);
        assertNotNull(cause, "IllegalArgumentException expected as cause of " + e);
        assertTrue(cause.getMessage().contains("cannot be applied"), cause.getMessage());
        assertTrue(cause.getMessage().contains("javax.net.ssl"), cause.getMessage());
    }

    private static void assertStarts(TahuDefaultEndpoint endpoint) {
        assertTrue(endpoint.isStarted());
    }
}
