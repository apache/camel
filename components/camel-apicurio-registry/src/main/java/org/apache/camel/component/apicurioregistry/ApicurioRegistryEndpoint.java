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

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
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

    private WebClient createWebClient(Vertx vertx) throws Exception {
        WebClientOptions options = createWebClientOptions();
        WebClient webClient = WebClient.create(vertx, options);
        String authType = configuration.getAuthType();
        if ("basic".equalsIgnoreCase(authType)) {
            String credentials = configuration.getUsername() + ":" + configuration.getPassword();
            return WebClientSession.create(webClient).addHeader("Authorization",
                    "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        } else if ("oidc".equalsIgnoreCase(authType)) {
            oauth2 = OAuth2Auth.create(vertx, new OAuth2Options()
                    .setFlow(OAuth2FlowType.CLIENT)
                    .setHttpClientOptions(options)
                    .setClientId(configuration.getClientId())
                    .setClientSecret(configuration.getClientSecret())
                    .setTokenPath(configuration.getTokenEndpoint()));
            Oauth2Credentials credentials = new Oauth2Credentials();
            if (configuration.getScope() != null) {
                credentials.addScope(configuration.getScope());
            }
            return OAuth2WebClient.create(webClient, oauth2).withCredentials(credentials);
        }
        return webClient;
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
