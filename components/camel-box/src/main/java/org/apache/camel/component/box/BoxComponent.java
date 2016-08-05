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
package org.apache.camel.component.box;

import java.util.Map;

import com.box.boxjavalibv2.BoxConnectionManagerBuilder;
import com.box.boxjavalibv2.IBoxConfig;
import com.box.boxjavalibv2.authorization.IAuthSecureStorage;
import com.box.boxjavalibv2.authorization.OAuthRefreshListener;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.component.box.internal.BoxClientHelper;
import org.apache.camel.component.box.internal.CachedBoxClient;
import org.apache.camel.util.component.AbstractApiComponent;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Represents the component that manages {@link BoxEndpoint}.
 */
public class BoxComponent extends AbstractApiComponent<BoxApiName, BoxConfiguration, BoxApiCollection> {

    private CachedBoxClient cachedBoxClient;

    public BoxComponent() {
        super(BoxEndpoint.class, BoxApiName.class, BoxApiCollection.getCollection());
    }

    public BoxComponent(CamelContext context) {
        super(context, BoxEndpoint.class, BoxApiName.class, BoxApiCollection.getCollection());
    }

    @Override
    protected BoxApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return BoxApiName.fromValue(apiNameStr);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, BoxApiName apiName,
                                      BoxConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new BoxEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    public CachedBoxClient getBoxClient() {
        return cachedBoxClient;
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    @Override
    public void setConfiguration(BoxConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public BoxConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (cachedBoxClient == null) {
            if (configuration != null) {
                cachedBoxClient = BoxClientHelper.createBoxClient(configuration);
            } else {
                throw new IllegalArgumentException("Unable to connect, Box component configuration is missing");
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (cachedBoxClient != null) {
            // close shared client connections
            BoxClientHelper.closeIdleConnections(cachedBoxClient);
        }
    }

    @Override
    public void doShutdown() throws Exception {
        try {
            if (cachedBoxClient != null) {
                // shutdown singleton client
                BoxClientHelper.shutdownBoxClient(configuration, cachedBoxClient);
            }
        } finally {
            cachedBoxClient = null;
            super.doShutdown();
        }
    }

    private BoxConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new BoxConfiguration());
        }
        return this.getConfiguration();
    }

    public BoxApiName getApiName() {
        return getConfigurationOrCreate().getApiName();
    }

    /**
     * What kind of operation to perform
     * @param apiName
     */
    public void setApiName(BoxApiName apiName) {
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
     * Box application client ID
     * @param clientId
     */
    public void setClientId(String clientId) {
        getConfigurationOrCreate().setClientId(clientId);
    }

    public String getClientSecret() {
        return getConfigurationOrCreate().getClientSecret();
    }

    /**
     * Box application client secret
     * @param clientSecret
     */
    public void setClientSecret(String clientSecret) {
        getConfigurationOrCreate().setClientSecret(clientSecret);
    }

    public IAuthSecureStorage getAuthSecureStorage() {
        return getConfigurationOrCreate().getAuthSecureStorage();
    }

    /**
     * OAuth Secure Storage callback, can be used to provide and or save OAuth tokens.
     * The callback may return null on first call to allow the component to login and authorize application
     * and obtain an OAuth token, which can then be saved in the secure storage.
     * For the component to be able to create a token automatically a user password must be provided.
     * @param authSecureStorage
     */
    public void setAuthSecureStorage(IAuthSecureStorage authSecureStorage) {
        getConfigurationOrCreate().setAuthSecureStorage(authSecureStorage);
    }

    public String getUserName() {
        return getConfigurationOrCreate().getUserName();
    }

    /**
     * Box user name, MUST be provided
     * @param userName
     */
    public void setUserName(String userName) {
        getConfigurationOrCreate().setUserName(userName);
    }

    public String getUserPassword() {
        return getConfigurationOrCreate().getUserPassword();
    }

    /**
     * Box user password, MUST be provided if authSecureStorage is not set, or returns null on first call
     * @param userPassword
     */
    public void setUserPassword(String userPassword) {
        getConfigurationOrCreate().setUserPassword(userPassword);
    }

    public OAuthRefreshListener getRefreshListener() {
        return getConfigurationOrCreate().getRefreshListener();
    }

    /**
     * OAuth listener for token updates, if the Camel application needs to use the access token outside the route
     * @param refreshListener
     */
    public void setRefreshListener(OAuthRefreshListener refreshListener) {
        getConfigurationOrCreate().setRefreshListener(refreshListener);
    }

    public boolean isRevokeOnShutdown() {
        return getConfigurationOrCreate().isRevokeOnShutdown();
    }

    /**
     * Flag to revoke OAuth refresh token on route shutdown, default false.
     * Will require a fresh refresh token on restart using either a custom IAuthSecureStorage
     * or automatic component login by providing a user password
     * @param revokeOnShutdown
     */
    public void setRevokeOnShutdown(boolean revokeOnShutdown) {
        getConfigurationOrCreate().setRevokeOnShutdown(revokeOnShutdown);
    }

    public String getSharedLink() {
        return getConfigurationOrCreate().getSharedLink();
    }

    /**
     * Box shared link for shared endpoints, can be a link for a shared comment, file or folder
     * @param sharedLink
     */
    public void setSharedLink(String sharedLink) {
        getConfigurationOrCreate().setSharedLink(sharedLink);
    }

    public String getSharedPassword() {
        return getConfigurationOrCreate().getSharedPassword();
    }

    /**
     * Password associated with the shared link, MUST be provided with sharedLink
     * @param sharedPassword
     */
    public void setSharedPassword(String sharedPassword) {
        getConfigurationOrCreate().setSharedPassword(sharedPassword);
    }

    public IBoxConfig getBoxConfig() {
        return getConfigurationOrCreate().getBoxConfig();
    }

    /**
     * Custom Box SDK configuration, not required normally
     * @param boxConfig
     */
    public void setBoxConfig(IBoxConfig boxConfig) {
        getConfigurationOrCreate().setBoxConfig(boxConfig);
    }

    public BoxConnectionManagerBuilder getConnectionManagerBuilder() {
        return getConfigurationOrCreate().getConnectionManagerBuilder();
    }

    /**
     * Custom Box connection manager builder, used to override default settings like max connections for underlying HttpClient.
     * @param connectionManagerBuilder
     */
    public void setConnectionManagerBuilder(BoxConnectionManagerBuilder connectionManagerBuilder) {
        getConfigurationOrCreate().setConnectionManagerBuilder(connectionManagerBuilder);
    }

    public Map<String, Object> getHttpParams() {
        return getConfigurationOrCreate().getHttpParams();
    }

    /**
     * Custom HTTP params for settings like proxy host
     * @param httpParams
     */
    public void setHttpParams(Map<String, Object> httpParams) {
        getConfigurationOrCreate().setHttpParams(httpParams);
    }

    public SSLContextParameters getSslContextParameters() {
        return getConfigurationOrCreate().getSslContextParameters();
    }

    /**
     * To configure security using SSLContextParameters.
     * @param sslContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        getConfigurationOrCreate().setSslContextParameters(sslContextParameters);
    }

    public int getLoginTimeout() {
        return getConfigurationOrCreate().getLoginTimeout();
    }

    /**
     * Amount of time the component will wait for a response from Box.com, default is 30 seconds
     * @param loginTimeout
     */
    public void setLoginTimeout(int loginTimeout) {
        getConfigurationOrCreate().setLoginTimeout(loginTimeout);
    }
}
