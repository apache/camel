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
package org.apache.camel.component.salesforce.internal;

import org.apache.camel.component.salesforce.LoginConfigHelper;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
public class SessionManualIT implements SalesforceSession.SalesforceSessionListener {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManualIT.class);
    private static final int TIMEOUT = 60000;
    private boolean onLoginTriggered;
    private boolean onLogoutTriggered;

    @Test
    public void testLogin() throws Exception {

        final SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
        sslContextFactory.setSslContext(new SSLContextParameters().createSSLContext(new DefaultCamelContext()));
        final SalesforceHttpClient httpClient = new SalesforceHttpClient(sslContextFactory);
        httpClient.setConnectTimeout(TIMEOUT);

        final SalesforceSession session
                = new SalesforceSession(new DefaultCamelContext(), httpClient, TIMEOUT, LoginConfigHelper.getLoginConfig());
        session.addListener(this);
        httpClient.setSession(session);

        httpClient.start();
        try {
            String loginToken = session.login(session.getAccessToken());
            LOG.info("First token {}", loginToken);

            assertTrue(onLoginTriggered, "SalesforceSessionListener onLogin NOT called");
            onLoginTriggered = false;

            // refresh token, also causes logout
            loginToken = session.login(loginToken);
            LOG.info("Refreshed token {}", loginToken);

            assertTrue(onLogoutTriggered, "SalesforceSessionListener onLogout NOT called");
            assertTrue(onLoginTriggered, "SalesforceSessionListener onLogin NOT called");

        } finally {
            // logout finally
            session.logout();
        }
    }

    @Override
    public void onLogin(String accessToken, String instanceUrl) {
        onLoginTriggered = true;
    }

    @Override
    public void onLogout() {
        onLogoutTriggered = true;
    }
}
