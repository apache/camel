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
package org.apache.camel.component.dhis2;

import org.apache.camel.component.dhis2.internal.Dhis2ApiName;
import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.hisp.dhis.integration.sdk.api.Dhis2Client;

@UriParams
@Configurer
public class Dhis2Configuration {
    @UriParam(description = "Base API URL")
    private String baseApiUrl;

    @UriParam(description = "Username")
    private String username;

    @UriParam(description = "Password")
    private String password;

    @UriPath(description = "API name")
    @Metadata(required = true)
    private Dhis2ApiName apiName;

    @UriPath(description = "Method name")
    @Metadata(required = true)
    private String methodName;

    @UriParam(label = "advanced", description = "To use the custom client")
    private Dhis2Client client;

    public String getBaseApiUrl() {
        return baseApiUrl;
    }

    public void setBaseApiUrl(String baseApiUrl) {
        this.baseApiUrl = baseApiUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Dhis2ApiName getApiName() {
        return apiName;
    }

    public void setApiName(Dhis2ApiName apiName) {
        this.apiName = apiName;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public Dhis2Client getClient() {
        return client;
    }

    public void setClient(Dhis2Client dhis2Client) {
        this.client = dhis2Client;
    }
}
