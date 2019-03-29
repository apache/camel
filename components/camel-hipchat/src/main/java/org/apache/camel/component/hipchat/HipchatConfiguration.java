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
package org.apache.camel.component.hipchat;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

@UriParams
public class HipchatConfiguration {
    @UriPath
    @Metadata(required = true)
    private String protocol;
    @UriPath
    @Metadata(required = true)
    private String host = HipchatConstants.DEFAULT_HOST;
    @UriPath(defaultValue = "" + HipchatConstants.DEFAULT_PORT)
    private Integer port = HipchatConstants.DEFAULT_PORT;
    @UriParam
    private String authToken;
    @UriParam
    private String consumeUsers;
    @UriParam(description = "The CloseableHttpClient reference from registry to be used during API HTTP requests.", defaultValue = "CloseableHttpClient default from HttpClient library")
    private CloseableHttpClient httpClient = HttpClients.createDefault();

    public String getHost() {
        return host;
    }

    /**
     * The host for the hipchat server, such as api.hipchat.com
     */
    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    /**
     * The port for the hipchat server. Is by default 80.
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    /**
     * The protocol for the hipchat server, such as http.
     */
    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getAuthToken() {
        return authToken;
    }

    /**
     * OAuth 2 auth token
     */
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getConsumeUsers() {
        return consumeUsers;
    }

    /**
     * Username(s) when consuming messages from the hiptchat server.
     * <p/>
     * Multiple user names can be separated by comma.
     */
    public void setConsumeUsers(String consumeUsers) {
        this.consumeUsers = consumeUsers;
    }

    public String hipChatUrl() {
        return getProtocol() + "://" + getHost() + ":" + getPort();
    }

    public String[] consumableUsers() {
        return consumeUsers != null ? consumeUsers.split(",") : new String[0];
    }

    public String withAuthToken(String urlPath) {
        return urlPath + HipchatApiConstants.AUTH_TOKEN_PREFIX + getAuthToken();
    }

    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }
}
