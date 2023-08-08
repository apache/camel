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
package org.apache.camel.component.jetty;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jetty.BaseJettyTest.SSL_SYSPROPS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ResourceLock(SSL_SYSPROPS)
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "these tests does not run well on Windows")
public class HttpsRouteTest extends BaseJettyTest {

    public static final String NULL_VALUE_MARKER = CamelTestSupport.class.getCanonicalName();

    private static final Logger LOG = LoggerFactory.getLogger(HttpsRouteTest.class);

    protected String expectedBody = "<hello>world!</hello>";
    protected String pwd = "changeit";
    protected Properties originalValues = new Properties();

    public String getHttpProducerScheme() {
        return "https://";
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        // ensure jsse clients can validate the self signed dummy localhost cert,
        // use the server keystore as the trust store for these tests
        URL trustStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.p12");
        setSystemProp("javax.net.ssl.trustStore", trustStoreUrl.toURI().getPath());
        setSystemProp("javax.net.ssl.trustStorePassword", "changeit");
        setSystemProp("javax.net.ssl.trustStoreType", "PKCS12");
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        restoreSystemProperties();
        super.tearDown();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setSecureSocketProtocol("TLSv1.3");
        context.setSSLContextParameters(sslContextParameters);
        ((SSLContextParametersAware) context.getComponent("https")).setUseGlobalSslContextParameters(true);
        return context;
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

    @Test
    public void testEndpoint() throws Exception {
        MockEndpoint mockEndpointA = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);
        mockEndpointA.expectedBodiesReceived(expectedBody);
        MockEndpoint mockEndpointB = resolveMandatoryEndpoint("mock:b", MockEndpoint.class);
        mockEndpointB.expectedBodiesReceived(expectedBody);

        invokeHttpEndpoint();

        mockEndpointA.assertIsSatisfied();
        mockEndpointB.assertIsSatisfied();
        List<Exchange> list = mockEndpointA.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull(exchange, "exchange");

        Message in = exchange.getIn();
        assertNotNull(in, "in");

        Map<String, Object> headers = in.getHeaders();

        LOG.info("Headers: {}", headers);

        assertTrue(headers.size() > 0, "Should be more than one header but was: " + headers);
    }

    @Test
    public void testEndpointWithoutHttps() {
        MockEndpoint mockEndpoint = resolveMandatoryEndpoint("mock:a", MockEndpoint.class);
        assertThrows(RuntimeCamelException.class,
                () -> template.sendBodyAndHeader("http://localhost:" + port1 + "/test", expectedBody, "Content-Type",
                        "application/xml"),
                "expect exception on access to https endpoint via http");
        assertTrue(mockEndpoint.getExchanges().isEmpty(), "mock endpoint was not called");
    }

    @Test
    public void testHelloEndpoint() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        URL url = new URL("https://localhost:" + port1 + "/hello");
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        SSLContext ssl = SSLContext.getInstance("TLSv1.3");
        ssl.init(null, null, null);
        connection.setSSLSocketFactory(ssl.getSocketFactory());
        InputStream is = connection.getInputStream();
        int c;
        while ((c = is.read()) >= 0) {
            os.write(c);
        }

        String data = new String(os.toByteArray());
        assertEquals("<b>Hello World</b>", data);
    }

    @Test
    public void testHelloEndpointWithoutHttps() throws Exception {
        assertThrows(SocketException.class,
                () -> new URL("http://localhost:" + port1 + "/hello").openStream(),
                "expected SocketException on use of http");
    }

    protected void invokeHttpEndpoint() {
        template.sendBodyAndHeader(getHttpProducerScheme() + "localhost:" + port1 + "/test", expectedBody, "Content-Type",
                "application/xml");
        template.sendBodyAndHeader(getHttpProducerScheme() + "localhost:" + port2 + "/test", expectedBody, "Content-Type",
                "application/xml");
    }

    protected void configureSslContextFactory(SslContextFactory sslContextFactory) {
        sslContextFactory.setKeyManagerPassword(pwd);
        sslContextFactory.setKeyStorePassword(pwd);
        URL keyStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.p12");
        try {
            sslContextFactory.setKeyStorePath("file://" + keyStoreUrl.toURI().getPath());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
        sslContextFactory.setTrustStoreType("PKCS12");
        sslContextFactory.setProtocol("TLSv1.3");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() throws URISyntaxException {
                JettyHttpComponent componentJetty = (JettyHttpComponent) context.getComponent("jetty");
                componentJetty.setSslPassword(pwd);
                componentJetty.setSslKeyPassword(pwd);
                URL keyStoreUrl = this.getClass().getClassLoader().getResource("jsse/localhost.p12");
                componentJetty.setKeystore("file://" + keyStoreUrl.toURI().getPath());

                from("jetty:https://localhost:" + port1 + "/test").to("mock:a");

                Processor proc = new Processor() {
                    public void process(Exchange exchange) {
                        exchange.getMessage().setBody("<b>Hello World</b>");
                    }
                };
                from("jetty:https://localhost:" + port1 + "/hello").process(proc);

                from("jetty:https://localhost:" + port2 + "/test").to("mock:b");
            }
        };
    }
}
