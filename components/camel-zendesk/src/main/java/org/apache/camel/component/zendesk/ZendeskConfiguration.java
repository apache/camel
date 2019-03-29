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
package org.apache.camel.component.zendesk;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * Component configuration for Zendesk component.
 */
@UriParams
public class ZendeskConfiguration {

    @UriPath
    @Metadata(required = true)
    private String methodName;

    @UriParam
    private String serverUrl;

    @UriParam(label = "security", secret = true)
    private String username;

    @UriParam(label = "security", secret = true)
    private String oauthToken;

    @UriParam(label = "security", secret = true)
    private String token;

    @UriParam(label = "security", secret = true)
    private String password;

    /**
     * What operation to use
     * 
     * @return the methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * What operation to use
     * 
     * @param methodName
     *            the methodName to set
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * The server URL to connect.
     * 
     * @return server URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * The server URL to connect.
     * 
     * @param url server URL
     */
    public void setServerUrl(String url) {
        this.serverUrl = url;
    }

    /**
     * The user name.
     * 
     * @return user name
     */
    public String getUsername() {
        return username;
    }

    /**
     * The user name.
     * 
     * @param user user name
     */
    public void setUsername(String user) {
        this.username = user;
    }

    /**
     * The security token.
     * 
     * @return security token
     */
    public String getToken() {
        return token;
    }

    /**
     * The security token.
     * 
     * @param token security token
     */
    public void setToken(String token) {
        this.token = token;
    }

    /**
     * The OAuth token.
     * @return OAuth token
     */
    public String getOauthToken() {
        return oauthToken;
    }

    /**
     * The OAuth token.
     * 
     * @param token OAuth token
     */
    public void setOauthToken(String token) {
        this.oauthToken = token;
    }

    /**
     * The password.
     * 
     * @return password
     */
    public String getPassword() {
        return password;
    }

    /**
     * The password.
     * @param password password
     */
    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ZendeskConfiguration) {
            ZendeskConfiguration otherZendeskConf = (ZendeskConfiguration)other;
            return ObjectHelper.equal(methodName, otherZendeskConf.getMethodName())
                && ObjectHelper.equal(serverUrl, otherZendeskConf.getServerUrl())
                && ObjectHelper.equal(username, otherZendeskConf.getUsername())
                && ObjectHelper.equal(password, otherZendeskConf.getPassword())
                && ObjectHelper.equal(token, otherZendeskConf.getToken())
                && ObjectHelper.equal(oauthToken, otherZendeskConf.getOauthToken());
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("ZendeskConfiguration:[methodName=%s, serverUrl=%s, username=%s, password=%s, token=%s, oauthToken=%s]",
                             methodName, serverUrl, username, password, token, oauthToken);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }
}
