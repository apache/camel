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
package org.apache.camel.component.google.calendar;

import java.util.List;

import com.google.api.services.calendar.Calendar;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiCollection;
import org.apache.camel.component.google.calendar.internal.GoogleCalendarApiName;
import org.apache.camel.util.component.AbstractApiComponent;

/**
 * Represents the component that manages {@link GoogleCalendarEndpoint}.
 */
public class GoogleCalendarComponent extends AbstractApiComponent<GoogleCalendarApiName, GoogleCalendarConfiguration, GoogleCalendarApiCollection> {

    private Calendar client;
    private GoogleCalendarClientFactory clientFactory;

    public GoogleCalendarComponent() {
        super(GoogleCalendarEndpoint.class, GoogleCalendarApiName.class, GoogleCalendarApiCollection.getCollection());
    }

    public GoogleCalendarComponent(CamelContext context) {
        super(context, GoogleCalendarEndpoint.class, GoogleCalendarApiName.class, GoogleCalendarApiCollection.getCollection());
    }

    @Override
    protected GoogleCalendarApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return GoogleCalendarApiName.fromValue(apiNameStr);
    }

    public Calendar getClient() {
        if (client == null) {
            client = getClientFactory().makeClient(configuration.getClientId(),
                    configuration.getClientSecret(), configuration.getScopes(),
                    configuration.getApplicationName(), configuration.getRefreshToken(),
                    configuration.getAccessToken(), configuration.getEmailAddress(),
                    configuration.getP12FileName(), configuration.getUser());
        }
        return client;
    }

    public GoogleCalendarClientFactory getClientFactory() {
        if (clientFactory == null) {
            clientFactory = new BatchGoogleCalendarClientFactory();
        }
        return clientFactory;
    }

    @Override
    public GoogleCalendarConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    @Override
    public void setConfiguration(GoogleCalendarConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the GoogleCalendarClientFactory as factory for creating the client.
     * Will by default use {@link BatchGoogleCalendarClientFactory}
     */
    public void setClientFactory(GoogleCalendarClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, GoogleCalendarApiName apiName,
                                      GoogleCalendarConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new GoogleCalendarEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }


    private GoogleCalendarConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new GoogleCalendarConfiguration());
        }
        return this.getConfiguration();
    }

    public GoogleCalendarApiName getApiName() {
        return getConfigurationOrCreate().getApiName();
    }

    /**
     * What kind of operation to perform
     * @param apiName
     */
    public void setApiName(GoogleCalendarApiName apiName) {
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
     * Client ID of the calendar application
     * @param clientId
     */
    public void setClientId(String clientId) {
        getConfigurationOrCreate().setClientId(clientId);
    }

    public String getEmailAddress() {
        return getConfigurationOrCreate().getEmailAddress();
    }

    /**
     * The emailAddress of the Google Service Account.
     * @param emailAddress
     */
    public void setEmailAddress(String emailAddress) {
        getConfigurationOrCreate().setEmailAddress(emailAddress);
    }

    public String getClientSecret() {
        return getConfigurationOrCreate().getClientSecret();
    }

    /**
     * Client secret of the calendar application
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
     * Google calendar application name. Example would be "camel-google-calendar/1.0"
     * @param applicationName
     */
    public void setApplicationName(String applicationName) {
        getConfigurationOrCreate().setApplicationName(applicationName);
    }

    public List<String> getScopes() {
        return getConfigurationOrCreate().getScopes();
    }

    /**
     * Specifies the level of permissions you want a calendar application to have to a user account. See https://developers.google.com/google-apps/calendar/auth for more info.
     * @param scopes
     */
    public void setScopes(List<String> scopes) {
        getConfigurationOrCreate().setScopes(scopes);
    }

    public String getP12FileName() {
        return getConfigurationOrCreate().getP12FileName();
    }

    /**
     * The name of the p12 file which has the private key to use with the Google Service Account.
     * @param p12FileName
     */
    public void setP12FileName(String p12FileName) {
        getConfigurationOrCreate().setP12FileName(p12FileName);
    }

    public String getUser() {
        return getConfigurationOrCreate().getUser();
    }

    /**
     * The email address of the user the application is trying to impersonate in the service account flow
     * @param user
     */
    public void setUser(String user) {
        getConfigurationOrCreate().setUser(user);
    }
}
