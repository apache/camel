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

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.box.sdk.IAccessTokenCache;
import com.box.sdk.InMemoryLRUAccessTokenCache;
import com.box.sdk.JWTEncryptionPreferences;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.ProxyConfig;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.util.WebConnectionWrapper;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.box.BoxConfiguration;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BoxConnectionHelper
 * 
 * <p>
 * Utility class for creating Box API Connections
 */
public final class BoxConnectionHelper {

    private static final Logger LOG = LoggerFactory.getLogger(BoxConnectionHelper.class);

    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("&?([^=]+)=([^&]+)");
    
    private BoxConnectionHelper() {
        // hide utility class constructor
    }

    public static BoxAPIConnection createConnection(final BoxConfiguration configuration) {
        if (configuration.getAuthenticationType() == null) {
            throw new RuntimeCamelException(
                    "Box API connection failed: Authentication type not specified in configuration");
        }
        switch (configuration.getAuthenticationType()) {
        case BoxConfiguration.APP_ENTERPRISE_AUTHENTICATION:
            return createAppEnterpriseAuthenticatedConnection(configuration);
        case BoxConfiguration.APP_USER_AUTHENTICATION:
            return createAppUserAuthenticatedConnection(configuration);
        case BoxConfiguration.STANDARD_AUTHENTICATION:
            return createStandardAuthenticatedConnection(configuration);
        default:
            throw new RuntimeCamelException(String.format("Box API connection failed: Invalid authentication type '%s'",
                    configuration.getAuthenticationType()));
        }
    }

    public static BoxAPIConnection createStandardAuthenticatedConnection(BoxConfiguration configuration) {

        // Create web client for first leg of OAuth2
        //
        final WebClient webClient = new WebClient();
        final WebClientOptions options = webClient.getOptions();
        options.setRedirectEnabled(true);
        options.setJavaScriptEnabled(false);
        options.setThrowExceptionOnFailingStatusCode(true);
        options.setThrowExceptionOnScriptError(true);
        options.setPrintContentOnFailingStatusCode(LOG.isDebugEnabled());
        try {
            // use default SSP to create supported non-SSL protocols list
            final SSLContext sslContext = new SSLContextParameters().createSSLContext(null);
            options.setSSLClientProtocols(sslContext.createSSLEngine().getEnabledProtocols());
        } catch (GeneralSecurityException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } finally {
            if (webClient != null) {
                webClient.close();
            }
        }

        // disable default gzip compression, as htmlunit does not negotiate
        // pages sent with no compression
        new WebConnectionWrapper(webClient) {
            @Override
            public WebResponse getResponse(WebRequest request) throws IOException {
                request.setAdditionalHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
                return super.getResponse(request);
            }
        };

        // add HTTP proxy if set
        final Map<String, Object> httpParams = configuration.getHttpParams();
        if (httpParams != null && httpParams.get("http.route.default-proxy") != null) {
            final HttpHost proxyHost = (HttpHost) httpParams.get("http.route.default-proxy");
            final Boolean socksProxy = (Boolean) httpParams.get("http.route.socks-proxy");
            final ProxyConfig proxyConfig = new ProxyConfig(proxyHost.getHostName(), proxyHost.getPort(),
                    socksProxy != null ? socksProxy : false);
            options.setProxyConfig(proxyConfig);
        }

        // authorize application on user's behalf
        try {

            // generate anti-forgery token to prevent/detect CSRF attack
            final String csrfToken = String.valueOf(new SecureRandom().nextLong());

            final HtmlPage authPage = webClient.getPage(authorizationUrl(configuration.getClientId(), csrfToken));

            // look for <div role="error_message">
            final HtmlDivision div = authPage
                    .getFirstByXPath("//div[contains(concat(' ', @class, ' '), ' error_message ')]");
            if (div != null) {
                final String errorMessage = div.getTextContent().replaceAll("\\s+", " ")
                        .replaceAll(" Show Error Details", ":").trim();
                throw new IllegalArgumentException("Error authorizing application: " + errorMessage);
            }

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
                redirectQuery = new URL(location).getQuery();
            }
            final Map<String, String> params = new HashMap<String, String>();
            final Matcher matcher = QUERY_PARAM_PATTERN.matcher(redirectQuery);
            while (matcher.find()) {
                params.put(matcher.group(1), matcher.group(2));
            }
            final String state = params.get("state");
            if (!csrfToken.equals(state)) {
                throw new SecurityException("Invalid CSRF code!");
            } else {

                // get authorization code
                final String authorizationCode = params.get("code");

                return new BoxAPIConnection(configuration.getClientId(), configuration.getClientSecret(),
                        authorizationCode);
            }

        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API connection failed: API returned the error code %d\n\n%s",
                            e.getResponseCode(), e.getResponse()),
                    e);
        } catch (Exception e) {
            throw new RuntimeCamelException(String.format("Box API connection failed: %s", e.getMessage()), e);
        }
    }

    public static BoxAPIConnection createAppUserAuthenticatedConnection(BoxConfiguration configuration) {
        // Create Encryption Preferences
        JWTEncryptionPreferences encryptionPref = new JWTEncryptionPreferences();
        encryptionPref.setPublicKeyID(configuration.getPublicKeyId());

        try {
            encryptionPref.setPrivateKey(new String(Files.readAllBytes(Paths.get(configuration.getPrivateKeyFile()))));
        } catch (Exception e) {
            throw new RuntimeCamelException("Box API connection failed: could not read privateKeyFile", e);
        }

        encryptionPref.setPrivateKeyPassword(configuration.getPrivateKeyPassword());
        encryptionPref.setEncryptionAlgorithm(configuration.getEncryptionAlgorithm());

        IAccessTokenCache accessTokenCache = configuration.getAccessTokenCache();
        if (accessTokenCache == null) {
            accessTokenCache = new InMemoryLRUAccessTokenCache(configuration.getMaxCacheEntries());
        }

        try {
            return BoxDeveloperEditionAPIConnection.getAppUserConnection(configuration.getUserId(),
                    configuration.getClientId(), configuration.getClientSecret(), encryptionPref, accessTokenCache);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API connection failed: API returned the error code %d\n\n%s",
                            e.getResponseCode(), e.getResponse()),
                    e);
        }

    }

    public static BoxAPIConnection createAppEnterpriseAuthenticatedConnection(BoxConfiguration configuration) {
        // Create Encryption Preferences
        JWTEncryptionPreferences encryptionPref = new JWTEncryptionPreferences();
        encryptionPref.setPublicKeyID(configuration.getPublicKeyId());

        try {
            encryptionPref.setPrivateKey(new String(Files.readAllBytes(Paths.get(configuration.getPrivateKeyFile()))));
        } catch (Exception e) {
            throw new RuntimeCamelException("Box API connection failed: could not read privateKeyFile", e);
        }

        encryptionPref.setPrivateKeyPassword(configuration.getPrivateKeyPassword());
        encryptionPref.setEncryptionAlgorithm(configuration.getEncryptionAlgorithm());

        IAccessTokenCache accessTokenCache = configuration.getAccessTokenCache();
        if (accessTokenCache == null) {
            accessTokenCache = new InMemoryLRUAccessTokenCache(configuration.getMaxCacheEntries());
        }

        try {
            return BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(configuration.getEnterpriseId(),
                    configuration.getClientId(), configuration.getClientSecret(), encryptionPref, accessTokenCache);
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    String.format("Box API connection failed: API returned the error code %d\n\n%s",
                            e.getResponseCode(), e.getResponse()),
                    e);
        }

    }

    public static String authorizationUrl(String clientId, String stateToken) {
        return "https://account.box.com/api/oauth2/authorize?response_type=code&redirect_url=https%3A%2F%2Flocalhost%2F&client_id="
                + clientId + "&state=" + stateToken;
    }

}
