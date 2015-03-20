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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.apache.camel.Service;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.internal.dto.LoginError;
import org.apache.camel.component.salesforce.internal.dto.LoginToken;
import org.apache.camel.util.ObjectHelper;
import org.codehaus.jackson.map.ObjectMapper;
import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpMethods;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.UrlEncoded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SalesforceSession implements Service {

    private static final String OAUTH2_REVOKE_PATH = "/services/oauth2/revoke?token=";
    private static final String OAUTH2_TOKEN_PATH = "/services/oauth2/token";

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceSession.class);
    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded;charset=utf-8";

    private final HttpClient httpClient;

    private final SalesforceLoginConfig config;

    private final ObjectMapper objectMapper;
    private final Set<SalesforceSessionListener> listeners;

    private volatile String accessToken;
    private volatile String instanceUrl;

    public SalesforceSession(HttpClient httpClient, SalesforceLoginConfig config) {
        // validate parameters
        ObjectHelper.notNull(httpClient, "httpClient");
        ObjectHelper.notNull(config, "SalesforceLoginConfig");
        ObjectHelper.notNull(config.getLoginUrl(), "loginUrl");
        ObjectHelper.notNull(config.getClientId(), "clientId");
        ObjectHelper.notNull(config.getClientSecret(), "clientSecret");
        ObjectHelper.notNull(config.getUserName(), "userName");
        ObjectHelper.notNull(config.getPassword(), "password");

        this.httpClient = httpClient;
        this.config = config;

        // strip trailing '/'
        String loginUrl = config.getLoginUrl();
        config.setLoginUrl(loginUrl.endsWith("/") ? loginUrl.substring(0, loginUrl.length() - 1) : loginUrl);

        this.objectMapper = new ObjectMapper();
        this.listeners = new CopyOnWriteArraySet<SalesforceSessionListener>();
    }

    @SuppressWarnings("unchecked")
    public synchronized String login(String oldToken) throws SalesforceException {

        // check if we need a new session
        // this way there's always a single valid session
        if ((accessToken == null) || accessToken.equals(oldToken)) {

            // try revoking the old access token before creating a new one
            accessToken = oldToken;
            if (accessToken != null) {
                try {
                    logout();
                } catch (SalesforceException e) {
                    LOG.warn("Error revoking old access token: " + e.getMessage(), e);
                }
                accessToken = null;
            }

            // login to Salesforce and get session id
            final StatusExceptionExchange loginPost = new StatusExceptionExchange(true);
            String url = config.getLoginUrl() + OAUTH2_TOKEN_PATH;
            loginPost.setURL(url);
            loginPost.setMethod(HttpMethods.POST);
            loginPost.setRequestContentType(FORM_CONTENT_TYPE);

            final UrlEncoded nvps = new UrlEncoded();
            nvps.put("grant_type", "password");
            nvps.put("client_id", config.getClientId());
            nvps.put("client_secret", config.getClientSecret());
            nvps.put("username", config.getUserName());
            nvps.put("password", config.getPassword());
            nvps.put("format", "json");

            try {

                LOG.info("Login user {} at Salesforce url: {}", config.getUserName(), url);

                // set form content
                loginPost.setRequestContent(new ByteArrayBuffer(
                        nvps.encode(StringUtil.__UTF8, true).getBytes(StringUtil.__UTF8)));
                httpClient.send(loginPost);

                // wait for the login to finish
                final int exchangeState = loginPost.waitForDone();

                switch (exchangeState) {
                case HttpExchange.STATUS_COMPLETED:
                    final byte[] responseContent = loginPost.getResponseContentBytes();
                    final int responseStatus = loginPost.getResponseStatus();

                    switch (responseStatus) {
                    case HttpStatus.OK_200:
                        // parse the response to get token
                        LoginToken token = objectMapper.readValue(responseContent, LoginToken.class);

                        // don't log token or instance URL for security reasons
                        LOG.info("Login successful");
                        accessToken = token.getAccessToken();
                        instanceUrl = token.getInstanceUrl();

                        // notify all listeners
                        for (SalesforceSessionListener listener : listeners) {
                            try {
                                listener.onLogin(accessToken, instanceUrl);
                            } catch (Throwable t) {
                                LOG.warn("Unexpected error from listener {}: {}", listener, t.getMessage());
                            }
                        }

                        break;

                    case HttpStatus.BAD_REQUEST_400:
                        // parse the response to get error
                        final LoginError error = objectMapper.readValue(responseContent, LoginError.class);
                        final String msg = String.format("Login error code:[%s] description:[%s]",
                                error.getError(), error.getErrorDescription());
                        final List<RestError> errors = new ArrayList<RestError>();
                        errors.add(new RestError(msg, error.getErrorDescription()));
                        throw new SalesforceException(errors, HttpStatus.BAD_REQUEST_400);

                    default:
                        throw new SalesforceException(String.format("Login error status:[%s] reason:[%s]",
                            responseStatus, loginPost.getReason()), responseStatus);
                    }
                    break;

                case HttpExchange.STATUS_EXCEPTED:
                    final Throwable ex = loginPost.getException();
                    throw new SalesforceException(
                            String.format("Unexpected login exception: %s", ex.getMessage()), ex);

                case HttpExchange.STATUS_CANCELLED:
                    throw new SalesforceException("Login request CANCELLED!", null);

                case HttpExchange.STATUS_EXPIRED:
                    throw new SalesforceException("Login request TIMEOUT!", null);

                default:
                    throw new SalesforceException("Unknow status: " + exchangeState, null);
                }
            } catch (IOException e) {
                String msg = "Login error: unexpected exception " + e.getMessage();
                throw new SalesforceException(msg, e);
            } catch (InterruptedException e) {
                String msg = "Login error: unexpected exception " + e.getMessage();
                throw new SalesforceException(msg, e);
            }
        }

        return accessToken;
    }

    public synchronized void logout() throws SalesforceException {
        if (accessToken == null) {
            return;
        }

        StatusExceptionExchange logoutGet = new StatusExceptionExchange(true);
        logoutGet.setURL(config.getLoginUrl() + OAUTH2_REVOKE_PATH + accessToken);
        logoutGet.setMethod(HttpMethods.GET);

        try {
            httpClient.send(logoutGet);
            final int done = logoutGet.waitForDone();
            switch (done) {
            case HttpExchange.STATUS_COMPLETED:
                final int statusCode = logoutGet.getResponseStatus();
                final String reason = logoutGet.getReason();

                if (statusCode == HttpStatus.OK_200) {
                    LOG.info("Logout successful");
                } else {
                    throw new SalesforceException(
                            String.format("Logout error, code: [%s] reason: [%s]",
                                    statusCode, reason),
                            statusCode);
                }
                break;

            case HttpExchange.STATUS_EXCEPTED:
                final Throwable ex = logoutGet.getException();
                throw new SalesforceException("Unexpected logout exception: " + ex.getMessage(), ex);

            case HttpExchange.STATUS_CANCELLED:
                throw new SalesforceException("Logout request CANCELLED!", null);

            case HttpExchange.STATUS_EXPIRED:
                throw new SalesforceException("Logout request TIMEOUT!", null);

            default:
                throw new SalesforceException("Unknown status: " + done, null);
            }
        } catch (SalesforceException e) {
            throw e;
        } catch (Exception e) {
            String msg = "Logout error: " + e.getMessage();
            throw new SalesforceException(msg, e);
        } finally {
            // reset session
            accessToken = null;
            instanceUrl = null;
            // notify all session listeners about logout
            for (SalesforceSessionListener listener : listeners) {
                try {
                    listener.onLogout();
                } catch (Throwable t) {
                    LOG.warn("Unexpected error from listener {}: {}", listener, t.getMessage());
                }
            }
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getInstanceUrl() {
        return instanceUrl;
    }

    public boolean addListener(SalesforceSessionListener listener) {
        return listeners.add(listener);
    }

    public boolean removeListener(SalesforceSessionListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public void start() throws Exception {
        // auto-login at start if needed
        login(accessToken);
    }

    @Override
    public void stop() throws Exception {
        // logout
        logout();
    }

    /**
     * Records status line, and exception from exchange.
     */
    private static class StatusExceptionExchange extends ContentExchange {

        private String reason;
        private Throwable exception;

        public StatusExceptionExchange(boolean cacheFields) {
            super(cacheFields);
        }

        @Override
        protected synchronized void onResponseStatus(Buffer version, int status, Buffer reason) throws IOException {
            // remember reason
            this.reason = reason.toString(StringUtil.__ISO_8859_1);
            super.onResponseStatus(version, status, reason);
        }

        @Override
        protected void onConnectionFailed(Throwable x) {
            this.exception = x;
            super.onConnectionFailed(x);
        }

        @Override
        protected void onException(Throwable x) {
            this.exception = x;
            super.onException(x);
        }

        public String getReason() {
            return reason;
        }

        public Throwable getException() {
            return exception;
        }

    }

    public interface SalesforceSessionListener {
        void onLogin(String accessToken, String instanceUrl);

        void onLogout();
    }

}
