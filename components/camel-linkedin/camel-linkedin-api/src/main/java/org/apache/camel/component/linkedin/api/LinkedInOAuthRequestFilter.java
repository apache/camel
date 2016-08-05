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
package org.apache.camel.component.linkedin.api;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.ext.Provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.ProxyConfig;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.WebClientOptions;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlSubmitInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.util.WebConnectionWrapper;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.conn.params.ConnRoutePNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LinkedIn OAuth request filter to handle OAuth token.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public final class LinkedInOAuthRequestFilter implements ClientRequestFilter {

    public static final String BASE_ADDRESS = "https://api.linkedin.com/v1";

    private static final Logger LOG = LoggerFactory.getLogger(LinkedInOAuthRequestFilter.class);

    private static final String AUTHORIZATION_URL = "https://www.linkedin.com/uas/oauth2/authorization?"
        + "response_type=code&client_id=%s&state=%s&redirect_uri=%s";
    private static final String AUTHORIZATION_URL_WITH_SCOPE = "https://www.linkedin.com/uas/oauth2/authorization?"
        + "response_type=code&client_id=%s&state=%s&scope=%s&redirect_uri=%s";

    private static final String ACCESS_TOKEN_URL = "https://www.linkedin.com/uas/oauth2/accessToken?"
        + "grant_type=authorization_code&code=%s&redirect_uri=%s&client_id=%s&client_secret=%s";

    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("&?([^=]+)=([^&]+)");

    private final WebClient webClient;

    private final OAuthParams oAuthParams;

    private OAuthToken oAuthToken;

    @SuppressWarnings("deprecation")
    public LinkedInOAuthRequestFilter(OAuthParams oAuthParams, Map<String, Object> httpParams,
                                      boolean lazyAuth, String[] enabledProtocols) {

        this.oAuthParams = oAuthParams;
        this.oAuthToken = null;

        // create HtmlUnit client
        webClient = new WebClient(BrowserVersion.FIREFOX_38);
        final WebClientOptions options = webClient.getOptions();
        options.setRedirectEnabled(true);
        options.setJavaScriptEnabled(false);
        options.setThrowExceptionOnFailingStatusCode(true);
        options.setThrowExceptionOnScriptError(true);
        options.setPrintContentOnFailingStatusCode(LOG.isDebugEnabled());
        options.setSSLClientProtocols(enabledProtocols);

        // add HTTP proxy if set
        if (httpParams != null && httpParams.get(ConnRoutePNames.DEFAULT_PROXY) != null) {
            final HttpHost proxyHost = (HttpHost) httpParams.get(ConnRoutePNames.DEFAULT_PROXY);
            final Boolean socksProxy = (Boolean) httpParams.get("http.route.socks-proxy");
            final ProxyConfig proxyConfig = new ProxyConfig(proxyHost.getHostName(), proxyHost.getPort(),
                socksProxy != null ? socksProxy : false);
            options.setProxyConfig(proxyConfig);
        }

        // disable default gzip compression, as error pages are sent with no compression and htmlunit doesn't negotiate
        new WebConnectionWrapper(webClient) {
            @Override
            public WebResponse getResponse(WebRequest request) throws IOException {
                request.setAdditionalHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
                return super.getResponse(request);
            }
        };

        if (!lazyAuth) {
            try {
                updateOAuthToken();
            } catch (IOException e) {
                throw new IllegalArgumentException(
                    String.format("Error authorizing user %s: %s", oAuthParams.getUserName(), e.getMessage()), e);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private String getRefreshToken() {
        // disable redirect to avoid loading error redirect URL
        webClient.getOptions().setRedirectEnabled(false);

        try {
            final String csrfId = String.valueOf(new SecureRandom().nextLong());

            final String encodedRedirectUri = URLEncoder.encode(oAuthParams.getRedirectUri(), "UTF-8");
            final OAuthScope[] scopes = oAuthParams.getScopes();

            final String url;
            if (scopes == null || scopes.length == 0) {
                url = String.format(AUTHORIZATION_URL, oAuthParams.getClientId(),
                    csrfId, encodedRedirectUri);
            } else {
                final int nScopes = scopes.length;
                final StringBuilder builder = new StringBuilder();
                int i = 0;
                for (OAuthScope scope : scopes) {
                    builder.append(scope.getValue());
                    if (++i < nScopes) {
                        builder.append("%20");
                    }
                }
                url = String.format(AUTHORIZATION_URL_WITH_SCOPE, oAuthParams.getClientId(), csrfId,
                    builder.toString(), encodedRedirectUri);
            }
            HtmlPage authPage;
            try {
                authPage = webClient.getPage(url);
            } catch (FailingHttpStatusCodeException e) {
                // only handle errors returned with redirects
                if (e.getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY) {
                    final URL location = new URL(e.getResponse().getResponseHeaderValue(HttpHeaders.LOCATION));
                    final String locationQuery = location.getQuery();
                    if (locationQuery != null && locationQuery.contains("error=")) {
                        throw new IOException(URLDecoder.decode(locationQuery).replaceAll("&", ", "));
                    } else {
                        // follow the redirect to login form
                        authPage = webClient.getPage(location);
                    }
                } else {
                    throw e;
                }
            }

            // look for <div role="alert">
            final HtmlDivision div = authPage.getFirstByXPath("//div[@role='alert']");
            if (div != null) {
                throw new IllegalArgumentException("Error authorizing application: " + div.getTextContent());
            }

            // submit login credentials
            final HtmlForm loginForm = authPage.getFormByName("oauth2SAuthorizeForm");
            final HtmlTextInput login = loginForm.getInputByName("session_key");
            login.setText(oAuthParams.getUserName());
            final HtmlPasswordInput password = loginForm.getInputByName("session_password");
            password.setText(oAuthParams.getUserPassword());
            final HtmlSubmitInput submitInput = loginForm.getInputByName("authorize");

            // validate CSRF and get authorization code
            String redirectQuery;
            try {
                final Page redirectPage = submitInput.click();
                redirectQuery = redirectPage.getUrl().getQuery();
            } catch (FailingHttpStatusCodeException e) {
                // escalate non redirect errors
                if (e.getStatusCode() != HttpStatus.SC_MOVED_TEMPORARILY) {
                    throw e;
                }
                final String location = e.getResponse().getResponseHeaderValue("Location");
                redirectQuery = new URL(location).getQuery();
            }
            if (redirectQuery == null) {
                throw new IllegalArgumentException("Redirect response query is null, check username, password and permissions");
            }
            final Map<String, String> params = new HashMap<String, String>();
            final Matcher matcher = QUERY_PARAM_PATTERN.matcher(redirectQuery);
            while (matcher.find()) {
                params.put(matcher.group(1), matcher.group(2));
            }
            final String state = params.get("state");
            if (!csrfId.equals(state)) {
                throw new SecurityException("Invalid CSRF code!");
            } else {
                // return authorization code
                // TODO check results??
                return params.get("code");
            }

        } catch (IOException e) {
            throw new IllegalArgumentException("Error authorizing application: " + e.getMessage(), e);
        }
    }

    public void close() {
        webClient.close();
    }

    private OAuthToken getAccessToken(String refreshToken) throws IOException {
        final String tokenUrl = String.format(ACCESS_TOKEN_URL, refreshToken,
            oAuthParams.getRedirectUri(), oAuthParams.getClientId(), oAuthParams.getClientSecret());
        final WebRequest webRequest = new WebRequest(new URL(tokenUrl), HttpMethod.POST);

        final WebResponse webResponse = webClient.loadWebResponse(webRequest);
        if (webResponse.getStatusCode() != HttpStatus.SC_OK) {
            throw new IOException(String.format("Error getting access token: [%s: %s]",
                webResponse.getStatusCode(), webResponse.getStatusMessage()));
        }
        final long currentTime = System.currentTimeMillis();
        final ObjectMapper mapper = new ObjectMapper();
        final Map map = mapper.readValue(webResponse.getContentAsStream(), Map.class);
        final String accessToken = map.get("access_token").toString();
        final Integer expiresIn = Integer.valueOf(map.get("expires_in").toString());
        return new OAuthToken(refreshToken, accessToken,
            currentTime + TimeUnit.MILLISECONDS.convert(expiresIn, TimeUnit.SECONDS));
    }

    public synchronized OAuthToken getOAuthToken() {
        return oAuthToken;
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        updateOAuthToken();

        // add OAuth query param
        final String requestUri = requestContext.getUri().toString();
        final StringBuilder builder = new StringBuilder(requestUri);
        if (requestUri.contains("?")) {
            builder.append('&');
        } else {
            builder.append('?');
        }
        builder.append("oauth2_access_token=").append(oAuthToken.getAccessToken());
        requestContext.setUri(URI.create(builder.toString()));
    }

    private synchronized void updateOAuthToken() throws IOException {

        // check whether an update is needed
        final long currentTime = System.currentTimeMillis();
        if (oAuthToken == null || oAuthToken.getExpiryTime() < currentTime) {
            LOG.info("OAuth token doesn't exist or has expired");

            // check whether a secure store is provided
            final OAuthSecureStorage secureStorage = oAuthParams.getSecureStorage();
            if (secureStorage != null) {

                oAuthToken = secureStorage.getOAuthToken();
                // if it returned a valid token, we are done, otherwise fall through and generate a new token
                if (oAuthToken != null && oAuthToken.getExpiryTime() > currentTime) {
                    return;
                }
                LOG.info("OAuth secure storage returned a null or expired token, creating a new token...");

                // throw an exception if a user password is not set for authorization
                if (oAuthParams.getUserPassword() == null || oAuthParams.getUserPassword().isEmpty()) {
                    throw new IllegalArgumentException("Missing password for LinkedIn authorization");
                }
            }

            // need new OAuth token, authorize user, LinkedIn does not support OAuth2 grant_type=refresh_token
            final String refreshToken = getRefreshToken();
            this.oAuthToken = getAccessToken(refreshToken);
            LOG.info("OAuth token created!");

            // notify secure storage
            if (secureStorage != null) {
                secureStorage.saveOAuthToken(this.oAuthToken);
            }
        }
    }
}
