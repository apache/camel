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
package org.apache.camel.component.salesforce;

import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SalesforceComponentIntegrationTest {

    private static final String SALESFORCE_TLS_TEST_URL = "https://tls1test.salesforce.com";

    @Test
    public void shouldNotUseSSLOrTTLSVersion10() throws Exception {
        final CamelContext context = new DefaultCamelContext();

        final SalesforceComponent component = new SalesforceComponent(context);

        final SalesforceLoginConfig loginConfig = new SalesforceLoginConfig();
        loginConfig.setClientId("not-used");
        loginConfig.setClientSecret("not-used");
        loginConfig.setUserName("not-used");
        loginConfig.setPassword("not-used");
        loginConfig.setLazyLogin(true);
        component.setLoginConfig(loginConfig);

        component.start();

        final SalesforceEndpoint endpoint = (SalesforceEndpoint) component.createEndpoint("salesforce:query");
        final SalesforceEndpointConfig config = endpoint.getConfiguration();

        final SalesforceHttpClient client = config.getHttpClient();
        final SslContextFactory sslContextFactory = client.getSslContextFactory();
        final SSLContext sslContext = sslContextFactory.getSslContext();
        final SSLEngine sslEngine = sslContext.createSSLEngine();
        final String[] protocols = sslEngine.getEnabledProtocols();
        for (final String protocol : protocols) {
            assertFalse("Protocol should not be SSL, was:" + protocol, protocol.startsWith("SSL"));
            assertTrue("Protocol should be TLSv1.1 or newer, was: " + protocol,
                    protocol.matches("TLSv(1.[1-9])|([2-9].*)"));
        }

        final ContentResponse response = client.GET(SALESFORCE_TLS_TEST_URL);

        assertNotNull("Response should be received", response);
    }
}
