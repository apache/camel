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

package org.apache.camel.component.cyberark.vault;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.apache.camel.component.cyberark.vault.client.ConjurClientFactory;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Retrieve secrets from CyberArk Conjur Vault.
 */
@UriEndpoint(
        firstVersion = "4.17.0",
        scheme = "cyberark-vault",
        title = "CyberArk Vault",
        syntax = "cyberark-vault:label",
        producerOnly = true,
        category = {Category.CLOUD, Category.SECURITY},
        headersClass = CyberArkVaultConstants.class)
public class CyberArkVaultEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private CyberArkVaultConfiguration configuration;

    private ConjurClient conjurClient;

    public CyberArkVaultEndpoint(String uri, Component component, CyberArkVaultConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new CyberArkVaultProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Cannot consume from CyberArk Vault endpoint: " + getEndpointUri());
    }

    @Override
    public CyberArkVaultComponent getComponent() {
        return (CyberArkVaultComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        conjurClient = configuration.getConjurClient();
        if (conjurClient == null) {
            String url = configuration.getUrl();
            String account = configuration.getAccount();
            String authToken = configuration.getAuthToken();
            String apiKey = configuration.getApiKey();
            String username = configuration.getUsername();
            String password = configuration.getPassword();

            if (ObjectHelper.isNotEmpty(authToken)) {
                conjurClient = ConjurClientFactory.createWithToken(url, account, authToken);
            } else if (ObjectHelper.isNotEmpty(apiKey)) {
                conjurClient = ConjurClientFactory.createWithApiKey(url, account, username, apiKey);
            } else if (ObjectHelper.isNotEmpty(username) && ObjectHelper.isNotEmpty(password)) {
                conjurClient = ConjurClientFactory.createWithCredentials(url, account, username, password);
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (conjurClient != null && configuration.getConjurClient() == null) {
            // Only close if we created the client
            conjurClient.close();
        }
    }

    public CyberArkVaultConfiguration getConfiguration() {
        return configuration;
    }

    public ConjurClient getConjurClient() {
        return conjurClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getUrl())) {
            return configuration.getUrl();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "https";
    }
}
