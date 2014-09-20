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
package org.apache.camel.component.box.internal;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.box.boxjavalibv2.BoxClient;
import com.box.boxjavalibv2.authorization.IAuthFlowListener;
import com.box.boxjavalibv2.authorization.IAuthFlowUI;
import com.box.boxjavalibv2.authorization.OAuthDataMessage;
import com.box.boxjavalibv2.authorization.OAuthWebViewData;
import com.box.boxjavalibv2.dao.BoxOAuthToken;
import com.box.boxjavalibv2.events.OAuthEvent;
import com.box.boxjavalibv2.resourcemanagers.IBoxOAuthManager;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.ProxyConfig;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import org.apache.camel.component.box.BoxConfiguration;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.conn.params.ConnRoutePNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
* HtmlUnit based OAuth2 implementation of {@link IAuthFlowUI}
*/
public final class LoginAuthFlowUI implements IAuthFlowUI {

    private static final Logger LOG = LoggerFactory.getLogger(LoginAuthFlowUI.class);
    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("&?([^=]+)=([^&]+)");

    private final BoxConfiguration configuration;
    private final BoxClient boxClient;

    private IAuthFlowListener listener;

    public LoginAuthFlowUI(BoxConfiguration configuration, BoxClient boxClient) {
        this.configuration = configuration;
        this.boxClient = boxClient;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void authenticate(IAuthFlowListener listener) {

        // TODO run this on an Executor to make it async

        // create HtmlUnit client
        final WebClient webClient = new WebClient(BrowserVersion.FIREFOX_24);
        final WebClientOptions options = webClient.getOptions();
        options.setRedirectEnabled(true);
        options.setJavaScriptEnabled(false);
        options.setThrowExceptionOnFailingStatusCode(true);
        options.setThrowExceptionOnScriptError(true);
        options.setPrintContentOnFailingStatusCode(LOG.isDebugEnabled());

        // add HTTP proxy if set
        final Map<String, Object> httpParams = configuration.getHttpParams();
        if (httpParams != null && httpParams.get(ConnRoutePNames.DEFAULT_PROXY) != null) {
            final HttpHost proxyHost = (HttpHost) httpParams.get(ConnRoutePNames.DEFAULT_PROXY);
            final Boolean socksProxy = (Boolean) httpParams.get("http.route.socks-proxy");
            final ProxyConfig proxyConfig = new ProxyConfig(proxyHost.getHostName(), proxyHost.getPort(),
                socksProxy != null ? socksProxy : false);
            options.setProxyConfig(proxyConfig);
        }

        // authorize application on user's behalf
        try {
            final String csrfId = String.valueOf(new SecureRandom().nextLong());

            OAuthWebViewData viewData = new OAuthWebViewData(boxClient.getOAuthDataController());
            viewData.setOptionalState(String.valueOf(csrfId));
            final HtmlPage authPage = webClient.getPage(viewData.buildUrl().toString());

            // submit login credentials
            final HtmlForm loginForm = authPage.getFormByName("login_form");
            final HtmlTextInput login = loginForm.getInputByName("login");
            login.setText(configuration.getUserName());
            final HtmlPasswordInput password = loginForm.getInputByName("password");
            password.setText(configuration.getUserPassword());
            final HtmlSubmitInput submitInput = loginForm.getInputByName("login_submit");

            // submit consent
            final HtmlPage consentPage = submitInput.click();
            final HtmlForm consentForm = consentPage.getFormByName("consent_form");
            final HtmlButton consentAccept = consentForm.getButtonByName("consent_accept");

            // disable redirect to avoid loading redirect URL
            webClient.getOptions().setRedirectEnabled(false);

            // validate CSRF and get authorization code
            String redirectQuery;
            try {
                final Page redirectPage = consentAccept.click();
                redirectQuery = redirectPage.getUrl().getQuery();
            } catch (FailingHttpStatusCodeException e) {
                // escalate non redirect errors
                if (e.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY) {
                    throw e;
                }
                final String location = e.getResponse().getResponseHeaderValue("Location");
                redirectQuery = location.substring(location.indexOf('?') + 1);
            }
            final Map<String, String> params = new HashMap<String, String>();
            final Matcher matcher = QUERY_PARAM_PATTERN.matcher(redirectQuery);
            while (matcher.find()) {
                params.put(matcher.group(1), matcher.group(2));
            }
            final String state = params.get("state");
            if (!csrfId.equals(state)) {
                final SecurityException e = new SecurityException("Invalid CSRF code!");
                listener.onAuthFlowException(e);
                this.listener.onAuthFlowException(e);
            } else {

                // get authorization code
                final String authorizationCode = params.get("code");

                // get OAuth token
                final IBoxOAuthManager oAuthManager = boxClient.getOAuthManager();
                final BoxOAuthToken oAuthToken = oAuthManager.createOAuth(authorizationCode,
                    configuration.getClientId(), configuration.getClientSecret(), null);

                // send initial token to BoxClient and this.listener
                final OAuthDataMessage authDataMessage = new OAuthDataMessage(oAuthToken,
                    boxClient.getJSONParser(), boxClient.getResourceHub());
                listener.onAuthFlowEvent(OAuthEvent.OAUTH_CREATED, authDataMessage);
                this.listener.onAuthFlowEvent(OAuthEvent.OAUTH_CREATED, authDataMessage);
            }

        } catch (Exception e) {
            // forward login exceptions to listener
            listener.onAuthFlowException(e);
            this.listener.onAuthFlowException(e);
        }
    }

    @Override
    public void addAuthFlowListener(IAuthFlowListener listener) {
        this.listener = listener;
    }

    @Override
    public void initializeAuthFlow(Object applicationContext, String clientId, String clientSecret) {
        // unknown usage
        throw new UnsupportedOperationException("initializeAuthFlow");
    }

    @Override
    public void initializeAuthFlow(Object applicationContext, String clientId, String clientSecret,
                                   String redirectUrl) {
        // unknown usage
        throw new UnsupportedOperationException("initializeAuthFlow");
    }

    @Override
    public void initializeAuthFlow(Object applicationContext, String clientId, String clientSecret,
                                   String redirectUrl, BoxClient boxClient) {
        // unknown usage
        throw new UnsupportedOperationException("initializeAuthFlow");
    }
}
