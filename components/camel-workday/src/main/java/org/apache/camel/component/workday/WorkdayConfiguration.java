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

package org.apache.camel.component.workday;

import org.apache.camel.spi.Metadata;
import org.apache.commons.httpclient.HttpConnectionManager;

import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriParam;

@UriParams
public class WorkdayConfiguration {

    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String clientId;

    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String clientSecret;

    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String tokenRefresh;

    @UriParam(label = "host")
    @Metadata(required = true)
    private String host;

    @UriParam(label = "tenant")
    @Metadata(required = true)
    private String tenant;

    @UriParam(label = "format")
    private String format = "json";

    @UriParam(label = "advanced")
    private HttpConnectionManager httpConnectionManager;

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

    public String getTokenRefresh() {
        return tokenRefresh;
    }

    public void setTokenRefresh(String tokenRefresh) {
        this.tokenRefresh = tokenRefresh;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public HttpConnectionManager getHttpConnectionManager() {
        return httpConnectionManager;
    }

    public void setHttpConnectionManager(HttpConnectionManager httpConnectionManager) {
        this.httpConnectionManager = httpConnectionManager;
    }

}