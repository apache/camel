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
package org.apache.camel.component.salesforce.internal;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.util.jsse.KeyStoreParameters;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SalesforceSessionTest {

    private static final int TIMEOUT = 1;

    private final KeyStoreParameters parameters;

    public SalesforceSessionTest() {
        parameters = new KeyStoreParameters();
        parameters.setResource("test.p12");
        parameters.setType("PKCS12");
        parameters.setPassword("password");
    }

    @Test
    public void shouldGenerateJwtTokens() {
        final SalesforceLoginConfig config = new SalesforceLoginConfig("https://login.salesforce.com", "ABCD",
            "username", parameters, true);

        final SalesforceSession session = new SalesforceSession(new DefaultCamelContext(),
            mock(SalesforceHttpClient.class), TIMEOUT, config);

        final String jwtAssertion = session.generateJwtAssertion();

        Assert.assertNotNull(jwtAssertion);
    }

    @Test
    public void shouldUseTheOverridenInstanceUrl() throws Exception {
        final SalesforceLoginConfig config = new SalesforceLoginConfig("https://login.salesforce.com", "clientId",
            "clientSecret", "username", "password", true);
        config.setInstanceUrl("https://custom.salesforce.com:8443");

        final SalesforceSession session = login(config);

        assertEquals("https://custom.salesforce.com:8443", session.getInstanceUrl());
    }

    @Test
    public void shouldUseTheSalesforceSuppliedInstanceUrl() throws Exception {
        final SalesforceLoginConfig config = new SalesforceLoginConfig("https://login.salesforce.com", "clientId",
            "clientSecret", "username", "password", true);

        final SalesforceSession session = login(config);

        assertEquals("https://eu11.salesforce.com", session.getInstanceUrl());
    }

    static SalesforceSession login(final SalesforceLoginConfig config)
        throws InterruptedException, TimeoutException, ExecutionException, SalesforceException {
        final SalesforceHttpClient client = mock(SalesforceHttpClient.class);

        final SalesforceSession session = new SalesforceSession(new DefaultCamelContext(), client, TIMEOUT, config);

        final Request request = mock(Request.class);
        when(client.POST(eq("https://login.salesforce.com/services/oauth2/token"))).thenReturn(request);

        when(request.content(any())).thenReturn(request);
        when(request.timeout(anyLong(), any())).thenReturn(request);

        final ContentResponse response = mock(ContentResponse.class);
        when(request.send()).thenReturn(response);

        when(response.getStatus()).thenReturn(HttpStatus.OK_200);
        when(response.getContentAsString()).thenReturn("{\"instance_url\": \"https://eu11.salesforce.com\"}");

        session.login(null);
        return session;
    }
}
