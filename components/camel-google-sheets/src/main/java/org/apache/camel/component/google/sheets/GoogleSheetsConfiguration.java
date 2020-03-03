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
package org.apache.camel.component.google.sheets;

import org.apache.camel.component.google.sheets.internal.GoogleSheetsApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for GoogleSheets component.
 */
@UriParams
public class GoogleSheetsConfiguration {

    @UriPath
    @Metadata(required = true)
    private GoogleSheetsApiName apiName;

    @UriPath(enums = "create,get,update,append,batchUpdate,clear")
    @Metadata(required = true)
    private String methodName;

    @UriParam
    private String clientId;

    @UriParam(label = "security", secret = true)
    private String clientSecret;

    @UriParam(label = "security", secret = true)
    private String accessToken;

    @UriParam(label = "security", secret = true)
    private String refreshToken;

    @UriParam
    private String applicationName;

    public GoogleSheetsApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     */
    public void setApiName(GoogleSheetsApiName apiName) {
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
     * Client ID of the sheets application
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Client secret of the sheets application
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * OAuth 2 access token. This typically expires after an hour so refreshToken is recommended for long term usage.
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * OAuth 2 refresh token. Using this, the Google Sheets component can obtain a new accessToken whenever the current one expires - a necessity if the application is long-lived.
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getApplicationName() {
        return applicationName;
    }

    /**
     * Google Sheets application name. Example would be "camel-google-sheets/1.0"
     */
    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
}
