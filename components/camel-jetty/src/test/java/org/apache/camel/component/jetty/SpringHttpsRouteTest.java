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

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.annotation.Resource;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = { "/org/apache/camel/component/jetty/jetty-https.xml" })
@ResourceLock(BaseJettyTest.SSL_SYSPROPS)
@Isolated
public class SpringHttpsRouteTest {
    private static final String NULL_VALUE_MARKER = CamelTestSupport.class.getCanonicalName();
    protected String expectedBody = "<hello>world!</hello>";
    protected String pwd = "changeit";
    protected Properties originalValues = new Properties();
    protected transient Logger log = LoggerFactory.getLogger(SpringHttpsRouteTest.class);

    @EndpointInject("mock:a")
    MockEndpoint mockEndpoint;

    @Produce
    private ProducerTemplate template;

    private Integer port;

    @BeforeEach
    public void setUp() {
        // ensure jsse clients can validate the self-signed dummy localhost
        // cert,
        // use the server keystore as the trust store for these tests
        URL trustStoreUrl = Thread.currentThread().getContextClassLoader().getResource("jsse/localhost.p12");
        setSystemProp("javax.net.ssl.trustStore", trustStoreUrl.getPath());
    }

    @AfterEach
    public void tearDown() {
        restoreSystemProperties();
    }

    private void setSystemProp(String key, String value) {
        String originalValue = System.setProperty(key, value);
        originalValues.put(key, originalValue != null ? originalValue : NULL_VALUE_MARKER);
    }

    private void restoreSystemProperties() {
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
        mockEndpoint.reset();
        mockEndpoint.expectedBodiesReceived(expectedBody);

        template.sendBodyAndHeader("https://localhost:" + port + "/test", expectedBody, "Content-Type", "application/xml");

        mockEndpoint.assertIsSatisfied();
        List<Exchange> list = mockEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull(exchange, "exchange");

        Message in = exchange.getIn();
        assertNotNull(in, "in");

        Map<String, Object> headers = in.getHeaders();

        log.info("Headers: {}", headers);

        assertTrue(headers.size() > 0, "Should be more than one header but was: " + headers);
    }

    @Test
    public void testEndpointWithoutHttps() {
        mockEndpoint.reset();
        try {
            template.sendBodyAndHeader("http://localhost:" + port + "/test", expectedBody, "Content-Type", "application/xml");
            fail("expect exception on access to https endpoint via http");
        } catch (RuntimeCamelException expected) {
        }
        assertTrue(mockEndpoint.getExchanges().isEmpty(), "mock endpoint was not called");
    }

    @Resource(name = "dynaPort")
    public void setPort(AvailablePortFinder.Port port) {
        this.port = port.getPort();
    }
}
