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
import java.util.Objects;

import com.box.boxjavalibv2.BoxConnectionManagerBuilder;
import com.box.boxjavalibv2.IBoxConfig;
import com.box.boxjavalibv2.authorization.IAuthSecureStorage;
import com.box.boxjavalibv2.authorization.OAuthRefreshListener;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Component configuration for Box component.
 */
@UriParams
public class BoxConfiguration {

    @UriPath @Metadata(required = "true")
    private BoxApiName apiName;
    @UriPath @Metadata(required = "true")
    private String methodName;
    @UriParam
    private String clientId;
    @UriParam
    private String clientSecret;
    @UriParam(label = "security")
    private IAuthSecureStorage authSecureStorage;
    @UriParam(label = "security")
    private String userName;
    @UriParam(label = "security")
    private String userPassword;
    @UriParam(label = "advanced")
    private OAuthRefreshListener refreshListener;
    @UriParam
    private boolean revokeOnShutdown;
    @UriParam
    private String sharedLink;
    @UriParam
    private String sharedPassword;
    @UriParam(label = "advanced")
    private IBoxConfig boxConfig;
    @UriParam(label = "advanced")
    private BoxConnectionManagerBuilder connectionManagerBuilder;
    @UriParam(label = "advanced")
    private Map<String, Object> httpParams;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;
    @UriParam(defaultValue = "30")
    private int loginTimeout = 30;

    public BoxApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(BoxApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * Box application client ID
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Box application client secret
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public IAuthSecureStorage getAuthSecureStorage() {
        return authSecureStorage;
    }

    /**
     * OAuth Secure Storage callback, can be used to provide and or save OAuth tokens.
     * The callback may return null on first call to allow the component to login and authorize application
     * and obtain an OAuth token, which can then be saved in the secure storage.
     * For the component to be able to create a token automatically a user password must be provided.
     */
    public void setAuthSecureStorage(IAuthSecureStorage authSecureStorage) {
        this.authSecureStorage = authSecureStorage;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Box user name, MUST be provided
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    /**
     * Box user password, MUST be provided if authSecureStorage is not set, or returns null on first call
     */
    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public OAuthRefreshListener getRefreshListener() {
        return refreshListener;
    }

    /**
     * OAuth listener for token updates, if the Camel application needs to use the access token outside the route
     */
    public void setRefreshListener(OAuthRefreshListener refreshListener) {
        this.refreshListener = refreshListener;
    }

    public boolean isRevokeOnShutdown() {
        return revokeOnShutdown;
    }

    /**
     * Flag to revoke OAuth refresh token on route shutdown, default false.
     * Will require a fresh refresh token on restart using either a custom IAuthSecureStorage
     * or automatic component login by providing a user password
     */
    public void setRevokeOnShutdown(boolean revokeOnShutdown) {
        this.revokeOnShutdown = revokeOnShutdown;
    }

    public String getSharedLink() {
        return sharedLink;
    }

    /**
     * Box shared link for shared endpoints, can be a link for a shared comment, file or folder
     */
    public void setSharedLink(String sharedLink) {
        this.sharedLink = sharedLink;
    }

    public String getSharedPassword() {
        return sharedPassword;
    }

    /**
     * Password associated with the shared link, MUST be provided with sharedLink
     */
    public void setSharedPassword(String sharedPassword) {
        this.sharedPassword = sharedPassword;
    }

    public IBoxConfig getBoxConfig() {
        return boxConfig;
    }

    /**
     * Custom Box SDK configuration, not required normally
     */
    public void setBoxConfig(IBoxConfig boxConfig) {
        this.boxConfig = boxConfig;
    }

    public BoxConnectionManagerBuilder getConnectionManagerBuilder() {
        return connectionManagerBuilder;
    }

    /**
     * Custom Box connection manager builder, used to override default settings like max connections for underlying HttpClient.
     */
    public void setConnectionManagerBuilder(BoxConnectionManagerBuilder connectionManagerBuilder) {
        this.connectionManagerBuilder = connectionManagerBuilder;
    }

    public Map<String, Object> getHttpParams() {
        return httpParams;
    }

    /**
     * Custom HTTP params for settings like proxy host
     */
    public void setHttpParams(Map<String, Object> httpParams) {
        this.httpParams = httpParams;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters.
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public int getLoginTimeout() {
        return loginTimeout;
    }

    /**
     * Amount of time the component will wait for a response from Box.com, default is 30 seconds
     */
    public void setLoginTimeout(int loginTimeout) {
        this.loginTimeout = loginTimeout;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BoxConfiguration) {
            final BoxConfiguration other = (BoxConfiguration) obj;
            // configurations are equal if BoxClient creation parameters are equal
            return boxConfig == other.boxConfig
                && connectionManagerBuilder == other.connectionManagerBuilder
                && httpParams == other.httpParams
                && Objects.equals(clientId, other.clientId)
                && Objects.equals(clientSecret, other.clientSecret)
                && authSecureStorage == other.authSecureStorage;
        }
        return false;
    }
}
