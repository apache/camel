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

    @UriParam
    private IAuthSecureStorage authSecureStorage;

    @UriParam
    private String userName;

    @UriParam
    private String userPassword;

    @UriParam
    private OAuthRefreshListener refreshListener;

    @UriParam
    private boolean revokeOnShutdown;

    @UriParam
    private String sharedLink;

    @UriParam
    private String sharedPassword;

    @UriParam
    private IBoxConfig boxConfig;

    @UriParam
    private BoxConnectionManagerBuilder connectionManagerBuilder;

    @UriParam
    private Map<String, Object> httpParams;

    @UriParam
    private SSLContextParameters sslContextParameters;

    /**
     * Box.com login timeout in seconds, defaults to 30.
     */
    @UriParam(defaultValue = "30")
    private int loginTimeout = 30;

    public BoxApiName getApiName() {
        return apiName;
    }

    public void setApiName(BoxApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public IAuthSecureStorage getAuthSecureStorage() {
        return authSecureStorage;
    }

    public void setAuthSecureStorage(IAuthSecureStorage authSecureStorage) {
        this.authSecureStorage = authSecureStorage;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPassword() {
        return userPassword;
    }

    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    public OAuthRefreshListener getRefreshListener() {
        return refreshListener;
    }

    public void setRefreshListener(OAuthRefreshListener refreshListener) {
        this.refreshListener = refreshListener;
    }

    public boolean isRevokeOnShutdown() {
        return revokeOnShutdown;
    }

    public void setRevokeOnShutdown(boolean revokeOnShutdown) {
        this.revokeOnShutdown = revokeOnShutdown;
    }

    public String getSharedLink() {
        return sharedLink;
    }

    public void setSharedLink(String sharedLink) {
        this.sharedLink = sharedLink;
    }

    public String getSharedPassword() {
        return sharedPassword;
    }

    public void setSharedPassword(String sharedPassword) {
        this.sharedPassword = sharedPassword;
    }

    public IBoxConfig getBoxConfig() {
        return boxConfig;
    }

    public void setBoxConfig(IBoxConfig boxConfig) {
        this.boxConfig = boxConfig;
    }

    public BoxConnectionManagerBuilder getConnectionManagerBuilder() {
        return connectionManagerBuilder;
    }

    public void setConnectionManagerBuilder(BoxConnectionManagerBuilder connectionManagerBuilder) {
        this.connectionManagerBuilder = connectionManagerBuilder;
    }

    public Map<String, Object> getHttpParams() {
        return httpParams;
    }

    public void setHttpParams(Map<String, Object> httpParams) {
        this.httpParams = httpParams;
    }

    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    public int getLoginTimeout() {
        return loginTimeout;
    }

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
                && clientId == other.clientId
                && clientSecret == other.clientSecret
                && authSecureStorage == other.authSecureStorage;
        }
        return false;
    }
}
