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
package org.apache.camel.component.http;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.URISupport;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.io.HttpClientConnectionManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpEndpointURLTest extends CamelTestSupport {

    @Test
    public void testHttpEndpointURLWithIPv6() {
        HttpEndpoint endpoint = (HttpEndpoint) context.getEndpoint("http://[2a00:8a00:6000:40::1413]:30300/test?test=true");
        assertEquals("http://[2a00:8a00:6000:40::1413]:30300/test?test=true", endpoint.getHttpUri().toString());
    }

    @Test
    public void testHttpEndpointHttpUri() {
        HttpEndpoint http1 = context.getEndpoint("http://www.google.com", HttpEndpoint.class);
        HttpEndpoint http2 = context.getEndpoint(
                "https://www.google.com?test=parameter&proxyAuthHost=myotherproxy&proxyAuthPort=2345", HttpEndpoint.class);
        HttpEndpoint http3 = context.getEndpoint("https://www.google.com?test=parameter", HttpEndpoint.class);

        assertEquals("http://www.google.com", http1.getHttpUri().toString(), "Get a wrong HttpUri of http1");
        assertEquals("https://www.google.com?test=parameter", http2.getHttpUri().toString(), "Get a wrong HttpUri of http2");
        assertEquals(http2.getHttpUri(), http3.getHttpUri(), "Get a wrong HttpUri of http2 andhttp3");

        // secure because protocol in remainder is https
        HttpEndpoint http4 = context.getEndpoint("http://https://www.google.com", HttpEndpoint.class);
        assertEquals("https://www.google.com", http4.getHttpUri().toString(), "Get a wrong HttpUri of http1");

        // secure because protocol in remainder is https
        HttpEndpoint http5 = context.getEndpoint("https://https://www.google.com", HttpEndpoint.class);
        assertEquals("https://www.google.com", http5.getHttpUri().toString(), "Get a wrong HttpUri of http1");

        // not secure because protocol in remainder is plain http
        HttpEndpoint http6 = context.getEndpoint("https://http://www.google.com", HttpEndpoint.class);
        assertEquals("http://www.google.com", http6.getHttpUri().toString(), "Get a wrong HttpUri of http1");
    }

    @Test
    public void testConnectionManagerFromHttpUri() {
        HttpEndpoint http1
                = context.getEndpoint("http://www.google.com?maxTotalConnections=40&connectionsPerRoute=5", HttpEndpoint.class);
        HttpClientConnectionManager connectionManager = http1.getClientConnectionManager();
        assertTrue(connectionManager instanceof PoolingHttpClientConnectionManager, "Get a wrong type of connection manager");
        PoolingHttpClientConnectionManager poolManager = (PoolingHttpClientConnectionManager) connectionManager;
        assertEquals(40, poolManager.getMaxTotal(), "Get a wrong setting of maxTotalConnections");
        assertEquals(5, poolManager.getDefaultMaxPerRoute(), "Get a wrong setting of connectionsPerRoute");
    }

    @Test
    // Just for CAMEL-8607
    public void testRawWithUnsafeCharacters() {
        HttpEndpoint http1 = context.getEndpoint(
                "http://www.google.com?authenticationPreemptive=true&authPassword=RAW(foo%bar)&authUsername=RAW(username)",
                HttpEndpoint.class);
        assertTrue(URISupport.sanitizeUri(http1.getEndpointUri()).indexOf("authPassword=xxxxxx") > 0,
                "The password is not loggged");
    }

}
