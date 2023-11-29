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

import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.SSLSession;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.netty.NettyConstants;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.isJavaVendor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class NettyHttpSSLTest extends BaseNettyTest {

    private static final String NULL_VALUE_MARKER = CamelTestSupport.class.getCanonicalName();

    protected final Properties originalValues = new Properties();

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        // ensure jsse clients can validate the self signed dummy localhost cert,
        // use the server keystore as the trust store for these tests
        URL trustStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.p12");
        setSystemProp("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());
        setSystemProp("javax.net.ssl.trustStorePassword", "changeit");

        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        restoreSystemProperties();
        super.tearDown();
    }

    protected void setSystemProp(String key, String value) {
        String originalValue = System.setProperty(key, value);
        originalValues.put(key, originalValue != null ? originalValue : NULL_VALUE_MARKER);
    }

    protected void restoreSystemProperties() {
        for (Map.Entry<Object, Object> entry : originalValues.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
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
        assumeFalse(isJavaVendor("ibm"));

        getMockEndpoint("mock:input").expectedBodiesReceived("Hello World");

        context.addRoutes(new RouteBuilder() {
            public void configure() {
                from("netty-http:https://localhost:{{port}}?ssl=true&passphrase=changeit&keyStoreResource=jsse/localhost.p12&trustStoreResource=jsse/localhost.p12")
                        .to("mock:input")
                        .process(exchange -> {
                            SSLSession session = exchange.getIn().getHeader(NettyConstants.NETTY_SSL_SESSION, SSLSession.class);
                            if (session != null) {
                                exchange.getMessage().setBody("Bye World");
                            } else {
                                exchange.getMessage().setBody("Cannot start conversion without SSLSession");
                            }
                        });
            }
        });
        context.start();
        String out = template.requestBody("https://localhost:{{port}}", "Hello World", String.class);
        assertEquals("Bye World", out);

        MockEndpoint.assertIsSatisfied(context);
    }

}
