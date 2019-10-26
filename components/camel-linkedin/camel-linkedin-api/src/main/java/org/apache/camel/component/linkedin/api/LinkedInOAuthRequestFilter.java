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
package org.apache.camel.component.linkedin.api;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.SocketAddress;
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

import org.apache.http.HttpHost;
import org.apache.http.conn.params.ConnRoutePNames;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LinkedIn OAuth request filter to handle OAuth token.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public final class LinkedInOAuthRequestFilter implements ClientRequestFilter {

    public static final String BASE_ADDRESS = "https://api.linkedin.com/v1";

    private static final int SC_OK = 200;
    private static final int SC_MOVED_TEMPORARILY = 302;
    private static final int SC_SEE_OTHER = 303;
    private static final String HEADER_LOCATION = "location";

    private static final Logger LOG = LoggerFactory.getLogger(LinkedInOAuthRequestFilter.class);

    private static final String AUTHORIZATION_URL_PREFIX = "https://www.linkedin.com";

    private static final String AUTHORIZATION_URL = AUTHORIZATION_URL_PREFIX + "/uas/oauth2/authorization?"
            + "response_type=code&client_id=%s&state=%s&redirect_uri=%s";
    private static final String AUTHORIZATION_URL_WITH_SCOPE = AUTHORIZATION_URL_PREFIX + "/uas/oauth2/authorization?"
            + "response_type=code&client_id=%s&state=%s&scope=%s&redirect_uri=%s";

    private static final String ACCESS_TOKEN_URL = AUTHORIZATION_URL_PREFIX + "/uas/oauth2/accessToken?"
            + "grant_type=authorization_code&code=%s&redirect_uri=%s&client_id=%s&client_secret=%s";

    private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("&?([^=]+)=([^&]+)");


    private final OAuthParams oAuthParams;

    private OAuthToken oAuthToken;

    private Proxy proxy;

    public LinkedInOAuthRequestFilter(OAuthParams oAuthParams, Map<String, Object> httpParams,
                                      boolean lazyAuth, String[] enabledProtocols) {

        this.oAuthParams = oAuthParams;
        if (oAuthParams.getSecureStorage() != null) {
            this.oAuthToken = oAuthParams.getSecureStorage().getOAuthToken();
        } else {
            this.oAuthToken = null;
        }

        if (httpParams != null && httpParams.get(ConnRoutePNames.DEFAULT_PROXY) != null) {
            final HttpHost proxyHost = (HttpHost)httpParams.get(ConnRoutePNames.DEFAULT_PROXY);
            final Boolean socksProxy = (Boolean)httpParams.get("http.route.socks-proxy");
            SocketAddress proxyAddr = new InetSocketAddress(proxyHost.getHostName(), proxyHost.getPort());
            if (socksProxy != null && socksProxy) {
                proxy = new Proxy(Proxy.Type.SOCKS, proxyAddr);
            } else {
                proxy = new Proxy(Proxy.Type.HTTP, proxyAddr);
            }
        } else {
            proxy = null;
        }

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

        try {
            final String csrfId = String.valueOf(new SecureRandom().nextLong());

            final String encodedRedirectUri = URLEncoder.encode(oAuthParams.getRedirectUri(), "UTF-8");
            final OAuthScope[] scopes = oAuthParams.getScopes();
            final Map<String, String> cookies = new HashMap();
            final String authorizationUrl = authorizationUrl(csrfId, encodedRedirectUri, scopes);

            //get login page (redirection is disabled as there could be an error message in redirect url
            final Connection.Response loginPageResponse = addProxy(Jsoup.connect(authorizationUrl), proxy)
                    .followRedirects(false)
                    .method(Connection.Method.GET)
                    .execute();
            final Connection.Response loginPageRedirectedResponse = followRedirection(loginPageResponse, cookies);
            final Document loginPage = loginPageRedirectedResponse.parse();

            validatePage(loginPage);

            //fill login form
            final FormElement loginForm = (FormElement) loginPage.select("form").first();

            final Element loginField = loginForm.select("input[name=session_key]").first();
            loginField.val(oAuthParams.getUserName());

            final Element passwordField = loginForm.select("input[name=session_password]").first();
            passwordField.val(oAuthParams.getUserPassword());

            //submit loginPage
            final Connection.Response afterLoginResponse = addProxy(loginForm.submit(), proxy)
                    .followRedirects(false)
                    .cookies(cookies)
                    .execute();
            cookies.putAll(afterLoginResponse.cookies());
            //follow redirects
            final Connection.Response afterLoginRedirectedResponse = followRedirection(afterLoginResponse, cookies);
            final URL redirectionUrl = getRedirectLocationAndValidate(afterLoginRedirectedResponse);
            final String redirectQuery;
            //if redirect url != null, it means that it contains code= and there is no need to continue
            if (redirectionUrl != null) {
                redirectQuery = redirectionUrl.getQuery();
            } else if (afterLoginRedirectedResponse.statusCode() == SC_OK) {
                //allow permission page is in response (or still login page containing errors)
                final Document allowPage = afterLoginRedirectedResponse.parse();
                //detect possible login errors
                validatePage(allowPage);
                //if there is no error, allow permission page is it for sure
                final FormElement allowForm = (FormElement) allowPage.select("form").get(1);
                final Connection.Response allowRedirectResponse = addProxy(allowForm.submit(), proxy)
                        .followRedirects(false)
                        .cookies(cookies)
                        .execute();
                final URL allowUrl = getRedirectLocationAndValidate(allowRedirectResponse);

                redirectQuery = allowUrl.getQuery();
            } else {
                throw new IllegalArgumentException("Redirect response query is null, check username, password and permissions");
            }

            final Map<String, String> params = new HashMap<>();
            final Matcher matcher = QUERY_PARAM_PATTERN.matcher(redirectQuery);
            while (matcher.find()) {
                params.put(matcher.group(1), matcher.group(2));
            }
            // check if we got caught in a Captcha!
            if (params.get("challengeId") != null) {
                throw new SecurityException("Unable to login due to CAPTCHA, use with a valid accessToken instead!");
            }
            final String state = params.get("state");
            if (!csrfId.equals(state)) {
                throw new SecurityException("Invalid CSRF code!");
            } else {
                // return authorization code
                // TODO check results??
                return params.get("code");
            }

        } catch (Exception e) {
            throw new IllegalArgumentException("Error authorizing application: " + e.getMessage(), e);
        }
    }

    /**
     * Validate page html for errors.
     */
    private void validatePage(Document loginPage) {
        //this error could happen e.g. if there is a wrong redirect url
        Elements errorDivs = loginPage.select("body[class=error]");
        if (errorDivs.isEmpty()) {
            //this error could happen e.g. with wrong clientId, usernane or password
            errorDivs = loginPage.select("div[role=alert]:not([class*=hidden])");
        }

        if (!errorDivs.isEmpty()) {
            final String errorMessage = errorDivs.first().text();
            throw new IllegalArgumentException("Error authorizing application: " + errorMessage);
        }
    }

    /**
     * Constructs authorization URL from AuthParams.
     */
    private String authorizationUrl(String csrfId, String encodedRedirectUri, OAuthScope[] scopes) {
        final String url;
        if (scopes == null || scopes.length == 0) {
            url = String.format(AUTHORIZATION_URL, oAuthParams.getClientId(), csrfId, encodedRedirectUri);
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
            url = String.format(AUTHORIZATION_URL_WITH_SCOPE, oAuthParams.getClientId(), csrfId, builder.toString(), encodedRedirectUri);
        }
        return url;
    }

    /**
     * Follows redirection (states 302 and 303) - on each step validates whether there s abn error.
     */
    private Connection.Response followRedirection(Connection.Response response, Map<String, String> cookies) throws IOException {
        return followRedirection(response, cookies, 0);
    }

    /**
     * Follows redirections (recursively, with max 5 repetitions)
     */
    private Connection.Response followRedirection(Connection.Response response, Map<String, String> cookies, int deep) throws IOException {
        //if recursive calls are not ending (theoretically it could happen if there is some error), we will end after 5 redirections (in successfull scenario there is maximal redirection count 1)
        if (deep > 5) {
            throw new IllegalArgumentException("Error authorizing application. Redirection goes still on and on.");
        }
        //try to get redirection url
        URL url = getRedirectLocationAndValidate(response);
        //if contains code=, then it is final url (containing refresh code)
        if (url != null) {
            if (url.getQuery().contains("code=")) {
                return response;
            } else if (url.toString().contains("error=") || url.toString().contains("errorKey=")) {
                throw new IOException(URLDecoder.decode(url.toString()).replaceAll("&", ", "));
            } else {
                Connection.Response resp = addProxy(Jsoup.connect(url.toString()), proxy)
                                                .followRedirects(false)
                                                .method(Connection.Method.GET)
                                                .cookies(cookies)
                                                .execute();
                return followRedirection(resp, cookies, deep++);
            }
        }
        cookies.putAll(response.cookies());
        return response;
    }

    /**
     * Extract header Location from response, also validates for possible errors, which could be part of redirection url (e.g. errorKey=unexpected_error)
     */
    private URL getRedirectLocationAndValidate(Connection.Response response) throws IOException {
        if (response.statusCode() == SC_MOVED_TEMPORARILY || response.statusCode() == SC_SEE_OTHER) {
            URL location;
            try {
                location = new URL(response.header(HEADER_LOCATION));
            } catch (MalformedURLException e) {
                location = new URL(AUTHORIZATION_URL_PREFIX + response.header(HEADER_LOCATION));
            }

            final String locationQuery = location.getQuery();
            if (locationQuery != null && (locationQuery.contains("error=") || locationQuery.contains("errorKey="))) {
                throw new IOException(URLDecoder.decode(locationQuery).replaceAll("&", ", "));
            }
            return location;
        }
        return null;
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

    private OAuthToken getAccessToken(String refreshToken) throws IOException {
        final String tokenUrl = String.format(ACCESS_TOKEN_URL, refreshToken, oAuthParams.getRedirectUri(), oAuthParams.getClientId(), oAuthParams.getClientSecret());
        final Connection.Response response = addProxy(Jsoup.connect(tokenUrl), proxy)
                                                .ignoreContentType(true)
                                                .method(Connection.Method.POST)
                                                .execute();

        if (response.statusCode() != SC_OK) {
            throw new IOException(String.format("Error getting access token: [%s: %s]", response.statusCode(), response.statusMessage()));
        }
        final long currentTime = System.currentTimeMillis();
        final ObjectMapper mapper = new ObjectMapper();
        final Map map = mapper.readValue(response.body(), Map.class);
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
