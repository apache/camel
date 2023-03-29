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

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.component.salesforce.AuthenticationType;
import org.apache.camel.component.salesforce.SalesforceHttpClient;
import org.apache.camel.component.salesforce.SalesforceLoginConfig;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.RestError;
import org.apache.camel.component.salesforce.api.utils.JsonUtils;
import org.apache.camel.component.salesforce.internal.dto.LoginError;
import org.apache.camel.component.salesforce.internal.dto.LoginToken;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jetty.client.HttpConversation;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.FormContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.Fields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SalesforceSession extends ServiceSupport {

    private static final String JWT_SIGNATURE_ALGORITHM = "SHA256withRSA";

    private static final int JWT_CLAIM_WINDOW = 270; // 4.5 min

    private static final String JWT_HEADER
            = Base64.getUrlEncoder().encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));

    private static final String OAUTH2_REVOKE_PATH = "/services/oauth2/revoke?token=";
    private static final String OAUTH2_TOKEN_PATH = "/services/oauth2/token";

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceSession.class);

    private final SalesforceHttpClient httpClient;
    private final long timeout;

    private final SalesforceLoginConfig config;

    private final ObjectMapper objectMapper;
    private final Set<SalesforceSessionListener> listeners;

    private volatile String accessToken;
    private volatile String instanceUrl;
    private volatile String id;
    private volatile String orgId;

    private final CamelContext camelContext;
    private final AtomicBoolean loggingIn = new AtomicBoolean();
    private CountDownLatch latch = new CountDownLatch(1);

    public SalesforceSession(CamelContext camelContext, SalesforceHttpClient httpClient, long timeout,
                             SalesforceLoginConfig config) {
        this.camelContext = camelContext;
        // validate parameters
        ObjectHelper.notNull(httpClient, "httpClient");
        ObjectHelper.notNull(config, "SalesforceLoginConfig");
        config.validate();

        this.httpClient = httpClient;
        this.timeout = timeout;
        this.config = config;

        this.objectMapper = JsonUtils.createObjectMapper();
        this.listeners = new CopyOnWriteArraySet<>();
    }

    public void attemptLoginUntilSuccessful(long backoffIncrement, long maxBackoff) {
        // if another thread is logging in, we will just wait until it's successful
        if (!loggingIn.compareAndSet(false, true)) {
            LOG.debug("waiting on login from another thread");
            // TODO: This is janky
            try {
                while (latch == null) {
                    Thread.sleep(100);
                }
                latch.await();
            } catch (InterruptedException ex) {
                throw new RuntimeException("Failed to login.", ex);
            }
            LOG.debug("done waiting");
            return;
        }
        LOG.debug("Attempting to login, no other threads logging in");
        latch = new CountDownLatch(1);

        long backoff = 0;

        try {
            for (;;) {
                try {
                    if (isStoppingOrStopped()) {
                        return;
                    }
                    login(getAccessToken());
                    break;
                } catch (SalesforceException e) {
                    backoff = backoff + backoffIncrement;
                    if (backoff > maxBackoff) {
                        backoff = maxBackoff;
                    }
                    LOG.warn(String.format("Salesforce login failed. Pausing for %d milliseconds", backoff), e);
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ex) {
                        throw new RuntimeException("Failed to login.", ex);
                    }
                }
            }
        } finally {
            loggingIn.set(false);
            latch.countDown();
        }
    }

    public synchronized String login(String oldToken) throws SalesforceException {

        // check if we need a new session
        // this way there's always a single valid session
        if (accessToken == null || accessToken.equals(oldToken)) {

            // try revoking the old access token before creating a new one
            accessToken = oldToken;
            if (accessToken != null) {
                try {
                    logout();
                } catch (SalesforceException e) {
                    LOG.warn("Error revoking old access token: {}", e.getMessage(), e);
                }
                accessToken = null;
            }

            // login to Salesforce and get session id
            final Request loginPost = getLoginRequest(null);
            try {

                final ContentResponse loginResponse = loginPost.send();
                parseLoginResponse(loginResponse, loginResponse.getContentAsString());

            } catch (InterruptedException e) {
                throw new SalesforceException("Login error: " + e.getMessage(), e);
            } catch (TimeoutException e) {
                throw new SalesforceException("Login request timeout: " + e.getMessage(), e);
            } catch (ExecutionException e) {
                throw new SalesforceException("Unexpected login error: " + e.getCause().getMessage(), e.getCause());
            }
        }
        return accessToken;
    }

    /**
     * Creates login request, allows SalesforceSecurityHandler to create a login request for a failed authentication
     * conversation
     *
     * @return login POST request.
     */
    public Request getLoginRequest(HttpConversation conversation) {
        final String loginUrl = (instanceUrl == null ? config.getLoginUrl() : instanceUrl) + OAUTH2_TOKEN_PATH;
        LOG.info("Login at Salesforce loginUrl: {}", loginUrl);
        final Fields fields = new Fields(true);

        fields.put("client_id", config.getClientId());
        fields.put("format", "json");

        final AuthenticationType type = config.getType();
        switch (type) {
            case USERNAME_PASSWORD:
                fields.put("client_secret", config.getClientSecret());
                fields.put("grant_type", "password");
                fields.put("username", config.getUserName());
                fields.put("password", config.getPassword());
                break;
            case REFRESH_TOKEN:
                fields.put("client_secret", config.getClientSecret());
                fields.put("grant_type", "refresh_token");
                fields.put("refresh_token", config.getRefreshToken());
                break;
            case JWT:
                fields.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
                fields.put("assertion", generateJwtAssertion());
                break;
            case CLIENT_CREDENTIALS:
                fields.put("grant_type", "client_credentials");
                fields.put("client_secret", config.getClientSecret());
                break;
            default:
                throw new IllegalArgumentException("Unsupported login configuration type: " + type);
        }

        final Request post;
        if (conversation == null) {
            post = httpClient.POST(loginUrl);
        } else {
            post = httpClient.newHttpRequest(conversation, URI.create(loginUrl)).method(HttpMethod.POST);
        }

        return post.content(new FormContentProvider(fields)).timeout(timeout, TimeUnit.MILLISECONDS);
    }

    String generateJwtAssertion() {
        final long utcPlusWindow = Clock.systemUTC().millis() / 1000 + JWT_CLAIM_WINDOW;
        final String audience = config.getJwtAudience() != null ? config.getJwtAudience() : config.getLoginUrl();

        final StringBuilder claim = new StringBuilder().append("{\"iss\":\"").append(config.getClientId())
                .append("\",\"sub\":\"").append(config.getUserName())
                .append("\",\"aud\":\"").append(audience).append("\",\"exp\":\"").append(utcPlusWindow)
                .append("\"}");

        final StringBuilder token = new StringBuilder(JWT_HEADER).append('.')
                .append(Base64.getUrlEncoder().encodeToString(claim.toString().getBytes(StandardCharsets.UTF_8)));

        final KeyStoreParameters keyStoreParameters = config.getKeystore();
        keyStoreParameters.setCamelContext(camelContext);

        try {
            final KeyStore keystore = keyStoreParameters.createKeyStore();

            final Enumeration<String> aliases = keystore.aliases();
            String alias = null;
            while (aliases.hasMoreElements()) {
                String tmp = aliases.nextElement();
                if (keystore.isKeyEntry(tmp)) {
                    if (alias == null) {
                        alias = tmp;
                    } else {
                        throw new IllegalArgumentException(
                                "The given keystore `" + keyStoreParameters.getResource()
                                                           + "` contains more than one key entry, expecting only one");
                    }
                }
            }

            PrivateKey key = (PrivateKey) keystore.getKey(alias, keyStoreParameters.getPassword().toCharArray());

            Signature signature = Signature.getInstance(JWT_SIGNATURE_ALGORITHM);
            signature.initSign(key);
            signature.update(token.toString().getBytes(StandardCharsets.UTF_8));
            byte[] signed = signature.sign();

            token.append('.').append(Base64.getUrlEncoder().encodeToString(signed));

            // Clean the private key from memory
            try {
                key.destroy();
            } catch (javax.security.auth.DestroyFailedException ex) {
                LOG.debug("Error destroying private key: {}", ex.getMessage());
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }

        return token.toString();
    }

    /**
     * Parses login response, allows SalesforceSecurityHandler to parse a login request for a failed authentication
     * conversation.
     */
    public synchronized void parseLoginResponse(ContentResponse loginResponse, String responseContent)
            throws SalesforceException {
        final int responseStatus = loginResponse.getStatus();

        try {
            switch (responseStatus) {
                case HttpStatus.OK_200:
                    // parse the response to get token
                    LoginToken token = objectMapper.readValue(responseContent, LoginToken.class);

                    // don't log token or instance URL for security reasons
                    LOG.info("Login successful");
                    accessToken = token.getAccessToken();
                    instanceUrl = Optional.ofNullable(config.getInstanceUrl()).orElse(token.getInstanceUrl());
                    id = token.getId();
                    orgId = id.substring(id.indexOf("id/") + 3, id.indexOf("id/") + 21);
                    // strip trailing '/'
                    int lastChar = instanceUrl.length() - 1;
                    if (instanceUrl.charAt(lastChar) == '/') {
                        instanceUrl = instanceUrl.substring(0, lastChar);
                    }

                    // notify all session listeners
                    for (SalesforceSessionListener listener : listeners) {
                        try {
                            listener.onLogin(accessToken, instanceUrl);
                        } catch (Exception t) {
                            LOG.warn("Unexpected error from listener {}: {}", listener, t.getMessage());
                        }
                    }

                    break;

                case HttpStatus.BAD_REQUEST_400:
                    // parse the response to get error
                    final LoginError error = objectMapper.readValue(responseContent, LoginError.class);
                    final String errorCode = error.getError();
                    final String msg = String.format("Login error code:[%s] description:[%s]", error.getError(),
                            error.getErrorDescription());
                    final List<RestError> errors = new ArrayList<>();
                    errors.add(new RestError(errorCode, msg));
                    throw new SalesforceException(errors, HttpStatus.BAD_REQUEST_400);

                default:
                    throw new SalesforceException(
                            String.format("Login error status:[%s] reason:[%s]", responseStatus, loginResponse.getReason()),
                            responseStatus);
            }
        } catch (IOException e) {
            String msg = "Login error: response parse exception " + e.getMessage();
            throw new SalesforceException(msg, e);
        }
    }

    public synchronized void logout() throws SalesforceException {
        if (accessToken == null) {
            return;
        }

        try {
            String logoutUrl = (instanceUrl == null ? config.getLoginUrl() : instanceUrl) + OAUTH2_REVOKE_PATH + accessToken;
            final Request logoutGet = httpClient.newRequest(logoutUrl).timeout(timeout, TimeUnit.MILLISECONDS);
            final ContentResponse logoutResponse = logoutGet.send();

            final int statusCode = logoutResponse.getStatus();
            final String reason = logoutResponse.getReason();

            if (statusCode == HttpStatus.OK_200) {
                LOG.debug("Logout successful");
            } else {
                LOG.debug("Failed to revoke OAuth token. This is expected if the token is invalid or already expired");
            }

        } catch (InterruptedException e) {
            String msg = "Logout error: " + e.getMessage();
            throw new SalesforceException(msg, e);
        } catch (ExecutionException e) {
            final Throwable ex = e.getCause();
            throw new SalesforceException("Unexpected logout exception: " + ex.getMessage(), ex);
        } catch (TimeoutException e) {
            throw new SalesforceException("Logout request TIMEOUT!", e);
        } finally {
            // reset session
            accessToken = null;
            instanceUrl = null;
            // notify all session listeners about logout
            for (SalesforceSessionListener listener : listeners) {
                try {
                    listener.onLogout();
                } catch (Exception t) {
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

    public String getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public boolean addListener(SalesforceSessionListener listener) {
        return listeners.add(listener);
    }

    public boolean removeListener(SalesforceSessionListener listener) {
        return listeners.remove(listener);
    }

    @Override
    public void doStart() throws Exception {
        // auto-login at start if needed
        login(accessToken);
    }

    @Override
    public void doStop() throws Exception {
        // logout
        logout();
    }

    public long getTimeout() {
        return timeout;
    }

    public interface SalesforceSessionListener {
        void onLogin(String accessToken, String instanceUrl);

        void onLogout();
    }
}
