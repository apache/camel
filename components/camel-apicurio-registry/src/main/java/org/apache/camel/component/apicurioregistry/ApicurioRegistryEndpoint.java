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

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.ScheduledPollEndpoint;

/**
 * Manage artifacts, versions, and groups in Apicurio Registry v3.
 */
@UriEndpoint(firstVersion = "4.22.0", scheme = "apicurio-registry", title = "Apicurio Registry",
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
        ApicurioRegistryConsumer consumer = new ApicurioRegistryConsumer(this, processor, configuration);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (registryClient == null) {
            registryClient = createRegistryClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        registryClient = null;
    }

    private RegistryClient createRegistryClient() {
        RegistryClientOptions options = RegistryClientOptions.create(configuration.getRegistryUrl());
        String authType = configuration.getAuthType();
        if ("basic".equalsIgnoreCase(authType)) {
            options.basicAuth(configuration.getUsername(), configuration.getPassword());
        } else if ("oidc".equalsIgnoreCase(authType)) {
            options.oauth2(configuration.getTokenEndpoint(), configuration.getClientId(),
                    configuration.getClientSecret(), configuration.getScope());
        }
        return RegistryClientFactory.create(options);
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
