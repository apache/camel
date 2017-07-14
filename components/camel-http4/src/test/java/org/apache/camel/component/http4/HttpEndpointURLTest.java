/**
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
package org.apache.camel.component.http4;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.URISupport;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.junit.Test;

public class HttpEndpointURLTest extends CamelTestSupport {
    
    @Test
    public void testHttpEndpointURLWithIPv6() {
        HttpEndpoint endpoint = (HttpEndpoint)context.getEndpoint("http4://[2a00:8a00:6000:40::1413]:30300/test?test=true");
        assertEquals("http://[2a00:8a00:6000:40::1413]:30300/test?test=true", endpoint.getHttpUri().toString());
    }
    
    @Test
    public void testHttpEndpointHttpUri() throws Exception {
        HttpEndpoint http1 = context.getEndpoint("http4://www.google.com", HttpEndpoint.class);
        HttpEndpoint http2 = context.getEndpoint("https4://www.google.com?test=parameter&proxyAuthHost=myotherproxy&proxyAuthPort=2345", HttpEndpoint.class);
        HttpEndpoint http3 = context.getEndpoint("https4://www.google.com?test=parameter", HttpEndpoint.class);
        
        assertEquals("Get a wrong HttpUri of http1", "http://www.google.com", http1.getHttpUri().toString());
        assertEquals("Get a wrong HttpUri of http2", "https://www.google.com?test=parameter", http2.getHttpUri().toString());
        assertEquals("Get a wrong HttpUri of http2 andhttp3", http2.getHttpUri(), http3.getHttpUri());
        
        try {
            // need to catch the exception here
            context.getEndpoint("https4://http://www.google.com", HttpEndpoint.class);
            fail("need to throw an exception here");
        } catch (ResolveEndpointFailedException ex) {
            assertTrue("Get a wrong exception message", ex.getMessage().indexOf("You have duplicated the http(s) protocol") > 0);
        }
    }
    
    @Test
    public void testConnectionManagerFromHttpUri() throws Exception {
        HttpEndpoint http1 = context.getEndpoint("http4://www.google.com?maxTotalConnections=40&connectionsPerRoute=5", HttpEndpoint.class);
        HttpClientConnectionManager connectionManager = http1.getClientConnectionManager();
        assertTrue("Get a wrong type of connection manager", connectionManager instanceof PoolingHttpClientConnectionManager);
        @SuppressWarnings("resource")
        PoolingHttpClientConnectionManager poolManager = (PoolingHttpClientConnectionManager)connectionManager;
        assertEquals("Get a wrong setting of maxTotalConnections", 40, poolManager.getMaxTotal());
        assertEquals("Get a wrong setting of connectionsPerRoute", 5, poolManager.getDefaultMaxPerRoute());
    }
    
    @Test
    // Just for CAMEL-8607
    public void testRawWithUnsafeCharacters() throws Exception {
        HttpEndpoint http1 = context.getEndpoint("http4://www.google.com?authenticationPreemptive=true&authPassword=RAW(foo%bar)&authUsername=RAW(username)", HttpEndpoint.class);
        assertTrue("The password is not loggged", URISupport.sanitizeUri(http1.getEndpointUri()).indexOf("authPassword=xxxxxx") > 0);
    }

}
