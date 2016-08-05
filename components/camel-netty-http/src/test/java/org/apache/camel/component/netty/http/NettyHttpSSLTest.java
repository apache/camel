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
package org.apache.camel.component.netty.http;

import java.net.URL;
import java.util.Properties;
import javax.net.ssl.SSLSession;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.After;
import org.junit.Test;

public class NettyHttpSSLTest extends BaseNettyTest {

    private static final String NULL_VALUE_MARKER = CamelTestSupport.class.getCanonicalName();

    protected Properties originalValues = new Properties();

    @Override
    public void setUp() throws Exception {
        // ensure jsse clients can validate the self signed dummy localhost cert,
        // use the server keystore as the trust store for these tests
        URL trustStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.ks");
        setSystemProp("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());

        super.setUp();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        restoreSystemProperties();
        super.tearDown();
    }

    protected void setSystemProp(String key, String value) {
        String originalValue = System.setProperty(key, value);
        originalValues.put(key, originalValue != null ? originalValue : NULL_VALUE_MARKER);
    }

    protected void restoreSystemProperties() {
        for (Object key : originalValues.keySet()) {
            Object value = originalValues.get(key);
            if (NULL_VALUE_MARKER.equals(value)) {
                System.clearProperty((String) key);
            } else {
                System.setProperty((String) key, (String) value);
            }
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testSSLInOutWithNettyConsumer() throws Exception {
        // ibm jdks dont have sun security algorithms
        if (isJavaVendor("ibm")) {
            return;
        }

        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("netty-http:https://localhost:{{port}}?ssl=true&passphrase=changeit&keyStoreResource=jsse/localhost.ks&trustStoreResource=jsse/localhost.ks")
                        .to("mock:input")
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                SSLSession session = exchange.getIn().getHeader(NettyConstants.NETTY_SSL_SESSION, SSLSession.class);
                                if (session != null) {
                                    exchange.getOut().setBody("Bye World");
                                } else {
                                    exchange.getOut().setBody("Cannot start conversion without SSLSession");
                                }
                            }
                        });
            }
        });
        context.start();

        String out = template.requestBody("https://localhost:{{port}}", "Hello World", String.class);
        assertEquals("Bye World", out);

        assertMockEndpointsSatisfied();
    }

}

