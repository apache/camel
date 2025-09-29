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
package org.apache.camel.component.keycloak;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;

/**
 * Manage Keycloak instances via Admin API.
 */
@UriEndpoint(firstVersion = "4.15.0", scheme = "keycloak", title = "Keycloak",
             syntax = "keycloak:label", producerOnly = true, category = { Category.SECURITY, Category.MANAGEMENT },
             headersClass = KeycloakConstants.class)
public class KeycloakEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private Keycloak keycloakClient;

    @UriParam
    private KeycloakConfiguration configuration;

    public KeycloakEndpoint(String uri, Component component, KeycloakConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KeycloakProducer(this);
    }

    @Override
    public KeycloakComponent getComponent() {
        return (KeycloakComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (configuration.getKeycloakClient() != null) {
            keycloakClient = configuration.getKeycloakClient();
        } else {
            keycloakClient = createKeycloakClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getKeycloakClient()) && keycloakClient != null) {
            keycloakClient.close();
        }
        super.doStop();
    }

    public KeycloakConfiguration getConfiguration() {
        return configuration;
    }

    public Keycloak getKeycloakClient() {
        return keycloakClient;
    }

    private Keycloak createKeycloakClient() {
        KeycloakBuilder builder = KeycloakBuilder.builder()
                .serverUrl(configuration.getServerUrl())
                .realm(configuration.getRealm());

        if (configuration.getUsername() != null && configuration.getPassword() != null) {
            builder.username(configuration.getUsername())
                    .password(configuration.getPassword())
                    .clientId("admin-cli");
        } else if (configuration.getClientId() != null && configuration.getClientSecret() != null) {
            builder.clientId(configuration.getClientId())
                    .clientSecret(configuration.getClientSecret())
                    .grantType("client_credentials");
        } else {
            throw new IllegalArgumentException("Either username/password or clientId/clientSecret must be provided");
        }

        return builder.build();
    }

    @Override
    public String getServiceUrl() {
        return configuration.getServerUrl();
    }

    @Override
    public String getServiceProtocol() {
        return "keycloak";
    }
}
