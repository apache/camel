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
package org.apache.camel.oauth;

import java.util.List;

public class OAuthCodeFlowParams {

    public enum AuthRequestDisplayOpts {
        PAGE,
        POPUP,
        TOUCH,
        WAP;
    }

    public enum AuthRequestPromptOpts {
        NONE,
        LOGIN,
        CONSENT,
        SELECT_ACCOUNT;
    }

    public enum AuthRequestResponseType {
        CODE,
        ID_TOKEN,
    }

    private String clientId;
    private String redirectUri;
    private AuthRequestResponseType responseType;
    private List<String> scopes;
    private String state;
    private AuthRequestDisplayOpts display;
    private List<AuthRequestPromptOpts> prompt;
    private Integer maxAge;
    private String uiLocales;
    private String idTokenHint;
    private String loginHint;
    private String acrValues;

    public String getClientId() {
        return clientId;
    }

    public OAuthCodeFlowParams setClientId(String clientId) {
        this.clientId = clientId;
        return this;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public OAuthCodeFlowParams setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
        return this;
    }

    public AuthRequestResponseType getResponseType() {
        return responseType;
    }

    public OAuthCodeFlowParams setResponseType(AuthRequestResponseType responseType) {
        this.responseType = responseType;
        return this;
    }

    public List<String> getScopes() {
        return scopes;
    }

    public OAuthCodeFlowParams setScope(String scope) {
        this.scopes = List.of(scope);
        return this;
    }

    public OAuthCodeFlowParams setScopes(List<String> scopes) {
        this.scopes = scopes;
        return this;
    }

    public String getState() {
        return state;
    }

    public OAuthCodeFlowParams setState(String state) {
        this.state = state;
        return this;
    }

    public AuthRequestDisplayOpts getDisplay() {
        return display;
    }

    public OAuthCodeFlowParams setDisplay(AuthRequestDisplayOpts display) {
        this.display = display;
        return this;
    }

    public List<AuthRequestPromptOpts> getPrompt() {
        return prompt;
    }

    public OAuthCodeFlowParams setPrompt(List<AuthRequestPromptOpts> prompt) {
        this.prompt = prompt;
        return this;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    public OAuthCodeFlowParams setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
        return this;
    }

    public String getUiLocales() {
        return uiLocales;
    }

    public OAuthCodeFlowParams setUiLocales(String uiLocales) {
        this.uiLocales = uiLocales;
        return this;
    }

    public String getIdTokenHint() {
        return idTokenHint;
    }

    public OAuthCodeFlowParams setIdTokenHint(String idTokenHint) {
        this.idTokenHint = idTokenHint;
        return this;
    }

    public String getLoginHint() {
        return loginHint;
    }

    public OAuthCodeFlowParams setLoginHint(String loginHint) {
        this.loginHint = loginHint;
        return this;
    }

    public String getAcrValues() {
        return acrValues;
    }

    public OAuthCodeFlowParams setAcrValues(String acrValues) {
        this.acrValues = acrValues;
        return this;
    }
}
