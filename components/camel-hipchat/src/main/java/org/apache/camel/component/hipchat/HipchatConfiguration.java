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
package org.apache.camel.component.hipchat;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class HipchatConfiguration {
    @UriPath
    private String host = HipchatConstants.DEFAULT_HOST;
    @UriPath
    private Integer port = HipchatConstants.DEFAULT_PORT;
    @UriParam
    private String protocol = HipchatConstants.DEFAULT_PROTOCOL;
    @UriParam
    private String authToken;
    @UriParam
    private String consumeUsers;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getConsumeUsers() {
        return consumeUsers;
    }

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
}
