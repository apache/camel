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
package org.apache.camel.component.box.internal;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.box.sdk.IAccessTokenCache;
import com.box.sdk.InMemoryLRUAccessTokenCache;
import com.box.sdk.JWTEncryptionPreferences;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.box.BoxConfiguration;
import org.apache.http.HttpHost;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;
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

        // authorize application on user's behalf
        try {
            //prepare proxy parameter
            final Proxy proxy;
            final Map<String, Object> httpParams = configuration.getHttpParams();
            if (httpParams != null && httpParams.get("http.route.default-proxy") != null) {
                final HttpHost proxyHost = (HttpHost) httpParams.get("http.route.default-proxy");
                final Boolean socksProxy = (Boolean) httpParams.get("http.route.socks-proxy");
                SocketAddress proxyAddr = new InetSocketAddress(proxyHost.getHostName(), proxyHost.getPort());
                if (socksProxy != null && socksProxy) {
                    proxy = new Proxy(Proxy.Type.SOCKS, proxyAddr);
                } else {
                    proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);
                }
            } else {
                proxy = null;
            }

            // generate anti-forgery token to prevent/detect CSRF attack
            final String csrfToken = String.valueOf(new SecureRandom().nextLong());

            final String authorizeUrl = authorizationUrl(configuration.getClientId(), csrfToken);

            //load loginPage
            final Connection.Response loginPageResponse = addProxy(Jsoup.connect(authorizeUrl), proxy).method(Connection.Method.GET).execute();
            final Document loginPage = loginPageResponse.parse();

            validatePage(loginPage);

            //fill login form
            final FormElement loginForm = (FormElement)loginPage.select("form[name=login_form]").first();

            final Element loginField = loginForm.select("input[name=login]").first();
            loginField.val(configuration.getUserName());

            final Element passwordField = loginForm.select("input[name=password]").first();
            passwordField.val(configuration.getUserPassword());

            //submit loginPage
            final Map<String, String> cookies = new HashMap<>();
            cookies.putAll(loginPageResponse.cookies());

            Connection.Response response = addProxy(loginForm.submit(), proxy)
                    .cookies(cookies)
                    .execute();
            cookies.putAll(response.cookies());

            final Document consentPage = response.parse();

            //possible invalid credentials error
            validatePage(consentPage);

            final FormElement consentForm = (FormElement)consentPage.select("form[name=consent_form]").first();

            //remove reject input
            consentForm.elements().removeIf(e -> e.attr("name").equals("consent_reject"));
            //parse request_token from javascript from head, it is the first script in the header
            final String requestTokenScript = consentPage.select("script").first().html();
            final Matcher m = Pattern.compile("var\\s+request_token\\s+=\\s+'([^'].+)'.*").matcher(requestTokenScript);
            if (m.find()) {
                final String requestToken = m.group(1);
                response = addProxy(consentForm.submit(), proxy)
                        .data("request_token", requestToken)
                        .followRedirects(false)
                        .cookies(cookies)
                        .execute();
            } else {
                throw new IllegalArgumentException("Error authorizing application: Can not parse request token.");
            }
            final String location = response.header("Location");

            final Map<String, String> params = new HashMap<>();
            final Matcher matcher = QUERY_PARAM_PATTERN.matcher(new URL(location).getQuery());
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
                    String.format("Box API connection failed: API returned the error code %d%n%n%s",
                            e.getResponseCode(), e.getResponse()),
                    e);
        } catch (Exception e) {
            throw new RuntimeCamelException(String.format("Box API connection failed: %s", e.getMessage()), e);
        }
    }

    /**
     * Validation of page:
     * - detects CAPTCHA test
     * - detects 2-step verification
     * - detects invalid credentials error
     * - detects wrong clientId error
     */
    private static void validatePage(Document page) {
        // CAPTCHA
        Elements captchaDivs = page.select("div[class*=g-recaptcha]");
        if (!captchaDivs.isEmpty()) {
            throw new IllegalArgumentException(
                    "Authentication requires CAPTCHA test. First you need to authenticate the account manually via web to unlock CAPTCHA.");
        }

        // 2-step verification
        Elements twoStepDivs = page.select("div[data-module=two-factor-enroll-form]");
        if (!twoStepDivs.isEmpty()) {
            throw new IllegalArgumentException(
                    "2-step verification is enabled on the Box account. Turn it off for camel-box to proceed the standard authentication.");
        }

        // login failures
        Elements errorDivs = page.select("div[class*=error_message]");
        String errorMessage = null;
        if (!errorDivs.isEmpty()) {
            errorMessage = errorDivs.first().text().replaceAll("\\s+", " ")
                    .replace(" Show Error Details", ":").trim();
        } else {
            errorDivs = page.select("div[class*=message]");
            if (!errorDivs.isEmpty()) {
                errorMessage = errorDivs.first().text();
            }
        }

        if (!errorDivs.isEmpty()) {
            throw new IllegalArgumentException("Error authorizing application: " + errorMessage);
        }
    }

    /**
     * Helper method to add proxy into JSoup connection
     */
    private static Connection addProxy(Connection connection, Proxy proxy) {
        if (proxy != null) {
            return connection.proxy(proxy);
        }
        return  connection;
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
                    String.format("Box API connection failed: API returned the error code %d%n%n%s",
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
                    String.format("Box API connection failed: API returned the error code %d%n%n%s",
                            e.getResponseCode(), e.getResponse()),
                    e);
        }

    }

    public static String authorizationUrl(String clientId, String stateToken) {
        return "https://account.box.com/api/oauth2/authorize?response_type=code&redirect_url=https%3A%2F%2Flocalhost%2F&client_id="
                + clientId + "&state=" + stateToken;
    }

}
