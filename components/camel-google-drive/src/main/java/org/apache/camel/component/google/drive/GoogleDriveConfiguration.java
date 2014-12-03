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

import java.util.Arrays;
import java.util.List;

import com.google.api.services.drive.DriveScopes;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Component configuration for GoogleDrive component.
 */
@UriParams
public class GoogleDriveConfiguration {
    private static final List<String> DEFAULT_SCOPES = Arrays.asList(DriveScopes.DRIVE_FILE, DriveScopes.DRIVE_APPS_READONLY, DriveScopes.DRIVE_METADATA_READONLY,
            DriveScopes.DRIVE);

    @UriPath
    private GoogleDriveApiName apiName;

    @UriPath
    private String methodName;
    
    @UriParam
    private List<String> scopes = DEFAULT_SCOPES;
    
    @UriParam
    private String clientId;

    @UriParam
    private String clientSecret;

    @UriParam
    private String accessToken;

    @UriParam
    private String refreshToken;

    @UriParam
    private String applicationName;

    public GoogleDriveApiName getApiName() {
        return apiName;
    }

    public void setApiName(GoogleDriveApiName apiName) {
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

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }
    
    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }
}
