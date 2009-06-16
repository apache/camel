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

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.commons.httpclient.HttpClient;
import org.junit.Test;

/**
 * @version $Revision$
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
    public void testHttpProxyConfigured() throws Exception {
        HttpEndpoint http = context.getEndpoint("http://www.google.com", HttpEndpoint.class);

        System.setProperty("http.proxyHost", "myproxy");
        System.setProperty("http.proxyPort", "1234");

        try {
            HttpClient client = http.createHttpClient();
            assertEquals("myproxy", client.getHostConfiguration().getProxyHost());
            assertEquals(1234, client.getHostConfiguration().getProxyPort());
        } finally {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
        }
    }

    @Test
    public void testHttpProxyEndpointConfigured() throws Exception {
        HttpEndpoint http = context.getEndpoint("http://www.google.com?proxyHost=myotherproxy&proxyPort=2345", HttpEndpoint.class);

        System.setProperty("http.proxyHost", "myproxy");
        System.setProperty("http.proxyPort", "1234");

        try {
            HttpClient client = http.createHttpClient();
            assertEquals("myotherproxy", client.getHostConfiguration().getProxyHost());
            assertEquals(2345, client.getHostConfiguration().getProxyPort());
        } finally {
            System.clearProperty("http.proxyHost");
            System.clearProperty("http.proxyPort");
        }
    }

}
