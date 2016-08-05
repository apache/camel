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
package org.apache.camel.component.google.mail;

import java.util.List;

import com.google.api.services.gmail.Gmail;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.mail.internal.GoogleMailApiCollection;
import org.apache.camel.component.google.mail.internal.GoogleMailApiName;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link GoogleMailEndpoint}.
 */
public class GoogleMailComponent extends AbstractApiComponent<GoogleMailApiName, GoogleMailConfiguration, GoogleMailApiCollection> {

    private Gmail client;
    private GoogleMailClientFactory clientFactory;

    public GoogleMailComponent() {
        super(GoogleMailEndpoint.class, GoogleMailApiName.class, GoogleMailApiCollection.getCollection());
    }

    public GoogleMailComponent(CamelContext context) {
        super(context, GoogleMailEndpoint.class, GoogleMailApiName.class, GoogleMailApiCollection.getCollection());
    }

    @Override
    protected GoogleMailApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return GoogleMailApiName.fromValue(apiNameStr);
    }

    public Gmail getClient() {
        if (client == null) {
            client = getClientFactory().makeClient(configuration.getClientId(), configuration.getClientSecret(), configuration.getScopes(), configuration.getApplicationName(),
                    configuration.getRefreshToken(), configuration.getAccessToken());
        }
        return client;
    }

    public GoogleMailClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleMailClientFactory();
        }
        return clientFactory;
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    @Override
    public void setConfiguration(GoogleMailConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    public GoogleMailConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    /**
     * To use the GoogleCalendarClientFactory as factory for creating the client.
     * Will by default use {@link BatchGoogleMailClientFactory}
     */
    public void setClientFactory(GoogleMailClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, GoogleMailApiName apiName, GoogleMailConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new GoogleMailEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }


    private GoogleMailConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new GoogleMailConfiguration());
        }
        return this.getConfiguration();
    }

    public GoogleMailApiName getApiName() {
        return getConfigurationOrCreate().getApiName();
    }

    /**
     * What kind of operation to perform
     * @param apiName
     */
    public void setApiName(GoogleMailApiName apiName) {
        getConfigurationOrCreate().setApiName(apiName);
    }

    public String getMethodName() {
        return getConfigurationOrCreate().getMethodName();
    }

    /**
     * What sub operation to use for the selected operation
     * @param methodName
     */
    public void setMethodName(String methodName) {
        getConfigurationOrCreate().setMethodName(methodName);
    }

    public String getClientId() {
        return getConfigurationOrCreate().getClientId();
    }

    /**
     * Client ID of the mail application
     * @param clientId
     */
    public void setClientId(String clientId) {
        getConfigurationOrCreate().setClientId(clientId);
    }

    public String getClientSecret() {
        return getConfigurationOrCreate().getClientSecret();
    }

    /**
     * Client secret of the mail application
     * @param clientSecret
     */
    public void setClientSecret(String clientSecret) {
        getConfigurationOrCreate().setClientSecret(clientSecret);
    }

    public String getAccessToken() {
        return getConfigurationOrCreate().getAccessToken();
    }

    /**
     * OAuth 2 access token. This typically expires after an hour so refreshToken is recommended for long term usage.
     * @param accessToken
     */
    public void setAccessToken(String accessToken) {
        getConfigurationOrCreate().setAccessToken(accessToken);
    }

    public String getRefreshToken() {
        return getConfigurationOrCreate().getRefreshToken();
    }

    /**
     * OAuth 2 refresh token. Using this, the Google Calendar component can obtain a new accessToken whenever the current one expires - a necessity if the application is long-lived.
     * @param refreshToken
     */
    public void setRefreshToken(String refreshToken) {
        getConfigurationOrCreate().setRefreshToken(refreshToken);
    }

    public String getApplicationName() {
        return getConfigurationOrCreate().getApplicationName();
    }

    /**
     * Google mail application name. Example would be "camel-google-mail/1.0"
     * @param applicationName
     */
    public void setApplicationName(String applicationName) {
        getConfigurationOrCreate().setApplicationName(applicationName);
    }

    public List<String> getScopes() {
        return getConfigurationOrCreate().getScopes();
    }

    /**
     * Specifies the level of permissions you want a mail application to have to a user account. See https://developers.google.com/gmail/api/auth/scopes for more info.
     * @param scopes
     */
    public void setScopes(List<String> scopes) {
        getConfigurationOrCreate().setScopes(scopes);
    }
}
