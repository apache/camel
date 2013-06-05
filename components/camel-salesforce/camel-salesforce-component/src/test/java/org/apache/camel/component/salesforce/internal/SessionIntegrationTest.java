package org.apache.camel.component.salesforce.internal;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.RedirectListener;
import org.apache.camel.component.salesforce.LoginConfigHelper;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author dbokde
 */
public class SessionIntegrationTest extends Assert implements SalesforceSession.SalesforceSessionListener {

    private static final Logger LOG = LoggerFactory.getLogger(SessionIntegrationTest.class);
    private static final int TIMEOUT = 60000;
    private boolean onLoginTriggered;
    private boolean onLogoutTriggered;

    @Test
    public void testLogin() throws Exception {

        final HttpClient httpClient = new HttpClient();
        httpClient.setConnectTimeout(TIMEOUT);
        httpClient.setTimeout(TIMEOUT);
        httpClient.registerListener(RedirectListener.class.getName());
        httpClient.start();

        final SalesforceSession session = new SalesforceSession(
            httpClient, LoginConfigHelper.getLoginConfig());
        session.addListener(this);

        try {
            String loginToken = session.login(session.getAccessToken());
            LOG.info("First token " + loginToken);

            assertTrue("SalesforceSessionListener onLogin NOT called", onLoginTriggered);
            onLoginTriggered = false;

            // refresh token, also causes logout
            loginToken = session.login(loginToken);
            LOG.info("Refreshed token " + loginToken);

            assertTrue("SalesforceSessionListener onLogout NOT called", onLogoutTriggered);
            assertTrue("SalesforceSessionListener onLogin NOT called", onLoginTriggered);

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
