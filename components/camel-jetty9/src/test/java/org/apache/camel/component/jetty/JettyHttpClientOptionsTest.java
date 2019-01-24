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
package org.apache.camel.component.jetty;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.http4.HttpProducer;
import org.apache.camel.http.common.HttpCommonEndpoint;
import org.apache.http.HttpConnection;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpCoreContext;
import org.eclipse.jetty.client.HttpClient;
import org.junit.Test;


/**
 * Unit test for http client options.
 */
public class JettyHttpClientOptionsTest extends BaseJettyTest {

    @Test
    public void testCustomHttpBinding() throws Exception {
        // assert jetty was configured with our timeout
        HttpCommonEndpoint jettyEndpoint = context.getEndpoint("http://localhost:{{port}}/myapp/myservice?socketTimeout=5555", HttpCommonEndpoint.class);
        assertNotNull("Jetty endpoint should not be null ", jettyEndpoint);
        AtomicInteger soTimeout = new AtomicInteger();
        HttpProducer producer = (HttpProducer)jettyEndpoint.createProducer();
        HttpClientContext ctx = new HttpClientContext() {
            @Override
            public void setAttribute(String id, Object obj) {
                if (obj instanceof HttpConnection) {
                    HttpConnection con = (HttpConnection) obj;
                    if (con.isOpen()) {
                        int so = con.getSocketTimeout();
                        if (so >= 0) {
                            soTimeout.set(so);
                        }
                    }
                }
                super.setAttribute(id, obj);
            }
        };
        HttpResponse response = producer.getHttpClient().execute(new HttpHost("localhost", getPort()), new HttpHead(), ctx);
        assertEquals("Get the wrong http client parameter", 5555, soTimeout.get());

        // send and receive
        Object out = template.requestBody("http://localhost:{{port}}/myapp/myservice", "Hello World");
        assertEquals("Bye World", context.getTypeConverter().convertTo(String.class, out));
    }
    
    @Test
    public void testProxySettingOfJettyHttpClient() throws Exception {
        // setup the Proxy setting through the URI
        HttpCommonEndpoint jettyEndpoint = context.getEndpoint("jetty://http://localhost:{{port}}/proxy/setting?proxyHost=192.168.0.1&proxyPort=9090", HttpCommonEndpoint.class);
        assertNotNull("Jetty endpoint should not be null ", jettyEndpoint);
        JettyHttpProducer producer = (JettyHttpProducer)jettyEndpoint.createProducer();
        assertProxyAddress(producer.getClient(), "192.168.0.1", 9090);

        // setup the context properties
        context.getGlobalOptions().put("http.proxyHost", "192.168.0.2");
        context.getGlobalOptions().put("http.proxyPort", "8080");
        jettyEndpoint = context.getEndpoint("jetty://http://localhost:{{port}}/proxy2/setting", HttpCommonEndpoint.class);
        producer = (JettyHttpProducer)jettyEndpoint.createProducer();
        assertProxyAddress(producer.getClient(), "192.168.0.2", 8080);
        context.getGlobalOptions().clear();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp/myservice?httpClient.soTimeout=5555").transform().constant("Bye World");
            }
        };
    }
    
    private void assertProxyAddress(HttpClient client, String expectedHost, int expectedPort) {
        CamelHttpClient camelHttpClient = (CamelHttpClient)client;
        assertEquals("Got the wrong http proxy host parameter", expectedHost, camelHttpClient.getProxyHost());
        assertEquals("Got the wrong http proxy port paramerter", expectedPort, camelHttpClient.getProxyPort());
    }

}
