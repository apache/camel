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
package org.apache.camel.component.apicurioregistry;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.netty.handler.codec.http.cookie.Cookie;
import io.vertx.core.Vertx;
import io.vertx.core.net.ProxyOptions;
import io.vertx.ext.auth.oauth2.OAuth2Auth;
import io.vertx.ext.auth.oauth2.OAuth2FlowType;
import io.vertx.ext.auth.oauth2.OAuth2Options;
import io.vertx.ext.auth.oauth2.Oauth2Credentials;
import io.vertx.ext.web.client.OAuth2WebClient;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.WebClientSession;
import io.vertx.ext.web.client.spi.CookieStore;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.vertx.common.VertxHelper;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Manage artifacts, versions, and groups in Apicurio Registry v3.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "apicurio-registry", title = "Apicurio Registry",
             syntax = "apicurio-registry:groupId/artifactId",
             category = { Category.CLOUD, Category.API }, headersClass = ApicurioRegistryConstants.class)
public class ApicurioRegistryEndpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    @UriPath(description = "The artifact group ID")
    private String groupId;

    @UriPath(description = "The artifact ID")
    private String artifactId;

    @UriParam
    private ApicurioRegistryConfiguration configuration;

    @UriParam(label = "advanced", description = "To use a pre-configured RegistryClient instance")
    private RegistryClient registryClient;

    private WebClient ownedWebClient;
    private OAuth2Auth oauth2;

    ApicurioRegistryEndpoint(String uri, ApicurioRegistryComponent component,
                             ApicurioRegistryConfiguration configuration,
                             String groupId, String artifactId) {
        super(uri, component);
        this.configuration = configuration;
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ApicurioRegistryProducer(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(groupId) || ObjectHelper.isEmpty(artifactId)) {
            throw new IllegalArgumentException(
                    "Both groupId and artifactId are required for the consumer, for example apicurio-registry:myGroup/myArtifact");
        }
        ApicurioRegistryConsumer consumer = new ApicurioRegistryConsumer(this, processor, configuration);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (registryClient == null) {
            Vertx vertx = ((ApicurioRegistryComponent) getComponent()).getOrCreateVertx();
            try {
                ownedWebClient = createWebClient(vertx);
                registryClient = RegistryClientFactory.create(
                        RegistryClientOptions.create(configuration.getRegistryUrl(), vertx).customWebClient(ownedWebClient));
            } catch (Exception e) {
                closeOwnedResources();
                throw e;
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (ownedWebClient != null) {
            registryClient = null;
            closeOwnedResources();
        }
    }

    private void closeOwnedResources() {
        // the SDK client has no close method, so the HTTP connection pool lives in the WebClient we created
        if (ownedWebClient != null) {
            ownedWebClient.close();
            ownedWebClient = null;
        }
        if (oauth2 != null) {
            oauth2.close();
            oauth2 = null;
        }
    }

    WebClientOptions createWebClientOptions() throws Exception {
        WebClientOptions options = new WebClientOptions();
        if (configuration.getSslContextParameters() != null) {
            VertxHelper.setupSSLOptions(getCamelContext(), configuration.getSslContextParameters(), options);
        }
        if (ObjectHelper.isNotEmpty(configuration.getProxyHost())) {
            ProxyOptions proxy = new ProxyOptions().setHost(configuration.getProxyHost());
            if (configuration.getProxyPort() != null) {
                proxy.setPort(configuration.getProxyPort());
            }
            if (ObjectHelper.isNotEmpty(configuration.getProxyUsername())) {
                proxy.setUsername(configuration.getProxyUsername());
                proxy.setPassword(configuration.getProxyPassword());
            }
            options.setProxyOptions(proxy);
        }
        return options;
    }

    private void validateAuthentication() {
        String authType = configuration.getAuthType();
        if (ObjectHelper.isEmpty(authType) || "none".equalsIgnoreCase(authType)) {
            return;
        }
        if ("basic".equalsIgnoreCase(authType)) {
            requireOption(configuration.getUsername(), "username", authType);
            requireOption(configuration.getPassword(), "password", authType);
        } else if ("oidc".equalsIgnoreCase(authType)) {
            requireOption(configuration.getTokenEndpoint(), "tokenEndpoint", authType);
            requireOption(configuration.getClientId(), "clientId", authType);
            requireOption(configuration.getClientSecret(), "clientSecret", authType);
        } else {
            throw new IllegalArgumentException(
                    "Unsupported authType '" + authType + "'. Supported values are none, basic and oidc");
        }
    }

    private static void requireOption(String value, String option, String authType) {
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException("The " + option + " option is required when authType=" + authType);
        }
    }

    private WebClient createWebClient(Vertx vertx) throws Exception {
        validateAuthentication();
        WebClientOptions options = createWebClientOptions();
        WebClient webClient = WebClient.create(vertx, options);
        try {
            String authType = configuration.getAuthType();
            if ("basic".equalsIgnoreCase(authType)) {
                String credentials = configuration.getUsername() + ":" + configuration.getPassword();
                // the session is only used to add the Authorization header: it gets a cookie store that keeps
                // nothing, so response cookies are not shared between the requests of this endpoint
                return WebClientSession.create(webClient, NoCookieStore.INSTANCE).addHeader("Authorization",
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
            } else if ("oidc".equalsIgnoreCase(authType)) {
                oauth2 = OAuth2Auth.create(vertx, new OAuth2Options()
                        .setHttpClientOptions(options)
                        .setClientId(configuration.getClientId())
                        .setClientSecret(configuration.getClientSecret())
                        .setTokenPath(configuration.getTokenEndpoint()));
                Oauth2Credentials credentials = new Oauth2Credentials().setFlow(OAuth2FlowType.CLIENT);
                if (configuration.getScope() != null) {
                    credentials.addScope(configuration.getScope());
                }
                return OAuth2WebClient.create(webClient, oauth2).withCredentials(credentials);
            }
            return webClient;
        } catch (Exception e) {
            webClient.close();
            throw e;
        }
    }

    /**
     * A cookie store that never keeps a cookie.
     */
    private static final class NoCookieStore implements CookieStore {

        static final NoCookieStore INSTANCE = new NoCookieStore();

        @Override
        public Iterable<Cookie> get(Boolean ssl, String domain, String path) {
            return Collections.emptyList();
        }

        @Override
        public CookieStore put(Cookie cookie) {
            return this;
        }

        @Override
        public CookieStore remove(Cookie cookie) {
            return this;
        }
    }

    public RegistryClient getRegistryClient() {
        return registryClient;
    }

    public void setRegistryClient(RegistryClient registryClient) {
        this.registryClient = registryClient;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public ApicurioRegistryConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ApicurioRegistryConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getRegistryUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "http";
    }
}
