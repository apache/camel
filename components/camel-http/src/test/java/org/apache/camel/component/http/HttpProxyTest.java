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
package org.apache.camel.component.http;

import org.apache.camel.http.common.HttpConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.URISupport;
import org.apache.commons.httpclient.HttpClient;
import org.junit.Test;

/**
 */
public class HttpProxyTest extends CamelTestSupport {

    @Test
    public void testNoHttpProxyConfigured() throws Exception {
        HttpEndpoint http = context.getEndpoint("http://www.google.com", HttpEndpoint.class);

        HttpClient client = http.createHttpClient();
        assertNull("No proxy configured yet", client.getHostConfiguration().getProxyHost());
        assertEquals("No proxy configured yet", -1, client.getHostConfiguration().getProxyPort());
    }
    
    @Test
    public void testDifferentHttpProxyConfigured() throws Exception {
        HttpEndpoint http1 = context.getEndpoint("http://www.google.com?proxyHost=myproxy&proxyPort=1234", HttpEndpoint.class);
        HttpEndpoint http2 = context.getEndpoint("http://www.google.com?test=parameter&proxyHost=myotherproxy&proxyPort=2345", HttpEndpoint.class);

        
        HttpClient client1 = http1.createHttpClient();
        assertEquals("myproxy", client1.getHostConfiguration().getProxyHost());
        assertEquals(1234, client1.getHostConfiguration().getProxyPort());
        
        HttpClient client2 = http2.createHttpClient();
        assertEquals("myotherproxy", client2.getHostConfiguration().getProxyHost());
        assertEquals(2345, client2.getHostConfiguration().getProxyPort());

        //As the endpointUri is recreated, so the parameter could be in different place, so we use the URISupport.normalizeUri
        assertEquals("Get a wrong endpoint uri of http1", "http://www.google.com?proxyHost=myproxy&proxyPort=1234", URISupport.normalizeUri(http1.getEndpointUri()));
        assertEquals("Get a wrong endpoint uri of http2", "http://www.google.com?proxyHost=myotherproxy&proxyPort=2345&test=parameter", URISupport.normalizeUri(http2.getEndpointUri()));
       
        assertEquals("Should get the same EndpointKey", http1.getEndpointKey(), http2.getEndpointKey());
    }

    @Test
    public void testHttpProxyConfigured() throws Exception {
        HttpEndpoint http = context.getEndpoint("http://www.google.com", HttpEndpoint.class);

        context.getProperties().put("http.proxyHost", "myproxy");
        context.getProperties().put("http.proxyPort", "1234");

        try {
            HttpClient client = http.createHttpClient();
            assertEquals("myproxy", client.getHostConfiguration().getProxyHost());
            assertEquals(1234, client.getHostConfiguration().getProxyPort());
        } finally {
            context.getProperties().remove("http.proxyHost");
            context.getProperties().remove("http.proxyPort");
        }
    }

    @Test
    public void testHttpProxyEndpointConfigured() throws Exception {
        HttpEndpoint http = context.getEndpoint("http://www.google.com?proxyHost=myotherproxy&proxyPort=2345", HttpEndpoint.class);

        context.getProperties().put("http.proxyHost", "myproxy");
        context.getProperties().put("http.proxyPort", "1234");

        try {
            HttpClient client = http.createHttpClient();
            assertEquals("myotherproxy", client.getHostConfiguration().getProxyHost());
            assertEquals(2345, client.getHostConfiguration().getProxyPort());
        } finally {
            context.getProperties().remove("http.proxyHost");
            context.getProperties().remove("http.proxyPort");
        }
    }

    @Test
    public void testHttpProxyComponentConfigured() throws Exception {
        HttpConfiguration config = new HttpConfiguration();
        config.setProxyHost("myotherproxy");
        config.setProxyPort(2345);

        HttpComponent comp = context.getComponent("http", HttpComponent.class);
        comp.setHttpConfiguration(config);

        HttpEndpoint http = context.getEndpoint("http://www.google.com", HttpEndpoint.class);

        context.getProperties().put("http.proxyHost", "myproxy");
        context.getProperties().put("http.proxyPort", "1234");

        try {
            HttpClient client = http.createHttpClient();
            assertEquals("myotherproxy", client.getHostConfiguration().getProxyHost());
            assertEquals(2345, client.getHostConfiguration().getProxyPort());
        } finally {
            context.getProperties().remove("http.proxyHost");
            context.getProperties().remove("http.proxyPort");
        }
    }

}
