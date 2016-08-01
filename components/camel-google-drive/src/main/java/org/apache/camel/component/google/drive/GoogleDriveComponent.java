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
package org.apache.camel.component.google.drive;

import java.util.List;

import com.google.api.services.drive.Drive;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link GoogleDriveEndpoint}.
 */
public class GoogleDriveComponent extends AbstractApiComponent<GoogleDriveApiName, GoogleDriveConfiguration, GoogleDriveApiCollection> {

    private Drive client;
    private GoogleDriveClientFactory clientFactory;
    
    public GoogleDriveComponent() {
        super(GoogleDriveEndpoint.class, GoogleDriveApiName.class, GoogleDriveApiCollection.getCollection());
    }

    public GoogleDriveComponent(CamelContext context) {
        super(context, GoogleDriveEndpoint.class, GoogleDriveApiName.class, GoogleDriveApiCollection.getCollection());
    }

    @Override
    protected GoogleDriveApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return GoogleDriveApiName.fromValue(apiNameStr);
    }

    public Drive getClient() {
        if (client == null) {
            client = getClientFactory().makeClient(configuration.getClientId(), configuration.getClientSecret(), configuration.getScopes(), 
                configuration.getApplicationName(), configuration.getRefreshToken(), configuration.getAccessToken());
        }
        return client;
    }
    
    public GoogleDriveClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleDriveClientFactory();
        }
        return clientFactory;
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    @Override
    public void setConfiguration(GoogleDriveConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    public GoogleDriveConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    /**
     * To use the GoogleCalendarClientFactory as factory for creating the client.
     * Will by default use {@link BatchGoogleDriveClientFactory}
     */
    public void setClientFactory(GoogleDriveClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }
    
    @Override
    protected Endpoint createEndpoint(String uri, String methodName, GoogleDriveApiName apiName,
                                      GoogleDriveConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        GoogleDriveEndpoint endpoint = new GoogleDriveEndpoint(uri, this, apiName, methodName, endpointConfiguration);
        endpoint.setClientFactory(clientFactory);
        return endpoint;
    }


    private GoogleDriveConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new GoogleDriveConfiguration());
        }
        return this.getConfiguration();
    }

    public GoogleDriveApiName getApiName() {
        return getConfigurationOrCreate().getApiName();
    }

    /**
     * What kind of operation to perform
     * @param apiName
     */
    public void setApiName(GoogleDriveApiName apiName) {
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
     * Client ID of the drive application
     * @param clientId
     */
    public void setClientId(String clientId) {
        getConfigurationOrCreate().setClientId(clientId);
    }

    public String getClientSecret() {
        return getConfigurationOrCreate().getClientSecret();
    }

    /**
     * Client secret of the drive application
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
     * Google drive application name. Example would be "camel-google-drive/1.0"
     * @param applicationName
     */
    public void setApplicationName(String applicationName) {
        getConfigurationOrCreate().setApplicationName(applicationName);
    }

    public List<String> getScopes() {
        return getConfigurationOrCreate().getScopes();
    }

    /**
     * Specifies the level of permissions you want a drive application to have to a user account. See https://developers.google.com/drive/web/scopes for more info.
     * @param scopes
     */
    public void setScopes(List<String> scopes) {
        getConfigurationOrCreate().setScopes(scopes);
    }
}
