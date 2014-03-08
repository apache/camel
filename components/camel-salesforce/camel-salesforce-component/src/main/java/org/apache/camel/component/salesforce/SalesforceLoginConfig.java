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
package org.apache.camel.component.salesforce;

/**
 * Configuration object for Salesforce login properties
 */
public class SalesforceLoginConfig {

    public static final String DEFAULT_LOGIN_URL = "https://login.salesforce.com";

    private String loginUrl;
    private String clientId;
    private String clientSecret;
    private String userName;
    private String password;
    // allow lazy login into Salesforce
    // note that login issues may not surface until a message needs to be processed
    private boolean lazyLogin;

    public SalesforceLoginConfig() {
        loginUrl = DEFAULT_LOGIN_URL;
        lazyLogin = false;
    }

    public SalesforceLoginConfig(String loginUrl,
                                 String clientId, String clientSecret,
                                 String userName, String password, boolean lazyLogin) {
        this.loginUrl = loginUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.userName = userName;
        this.password = password;
        this.lazyLogin = lazyLogin;
    }

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
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

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isLazyLogin() {
        return lazyLogin;
    }

    public void setLazyLogin(boolean lazyLogin) {
        this.lazyLogin = lazyLogin;
    }

}