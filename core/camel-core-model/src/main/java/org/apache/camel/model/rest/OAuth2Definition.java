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
package org.apache.camel.model.rest;

import java.util.ArrayList;
import java.util.List;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Rest security OAuth2 definition
 */
@Metadata(label = "rest,security,configuration")
@XmlRootElement(name = "oauth2")
@XmlAccessorType(XmlAccessType.FIELD)
public class OAuth2Definition extends RestSecurityDefinition {

    @XmlAttribute
    @Metadata(description = "The authorization URL to use for this flow. Required for implicit and access code flows.")
    private String authorizationUrl;
    @XmlAttribute
    @Metadata(description = "The token URL to use for this flow. Required for password, application, and access code flows.")
    private String tokenUrl;
    @XmlAttribute
    @Metadata(description = "The URL to use for obtaining refresh tokens.")
    private String refreshUrl;
    @XmlAttribute
    @Metadata(description = "The flow used by the OAuth2 security scheme. Valid values are implicit, password, application or accessCode.",
              enums = "implicit,password,application,clientCredentials,accessCode,authorizationCode")
    private String flow;
    @XmlElement(name = "scopes")
    @Metadata(description = "The available scopes for the OAuth2 security scheme.")
    private List<RestPropertyDefinition> scopes = new ArrayList<>();

    public OAuth2Definition() {
    }

    public OAuth2Definition(RestDefinition rest) {
        super(rest);
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    public void setAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getRefreshUrl() {
        return refreshUrl;
    }

    public void setRefreshUrl(String refreshUrl) {
        this.refreshUrl = refreshUrl;
    }

    public String getFlow() {
        return flow;
    }

    public void setFlow(String flow) {
        this.flow = flow;
    }

    public List<RestPropertyDefinition> getScopes() {
        return scopes;
    }

    public void setScopes(List<RestPropertyDefinition> scopes) {
        this.scopes = scopes;
    }

    public OAuth2Definition flow(String flow) {
        setFlow(flow);
        return this;
    }

    public OAuth2Definition authorizationUrl(String authorizationUrl) {
        setAuthorizationUrl(authorizationUrl);
        return this;
    }

    public OAuth2Definition tokenUrl(String tokenUrl) {
        setTokenUrl(tokenUrl);
        return this;
    }

    public OAuth2Definition refreshUrl(String refreshUrl) {
        setRefreshUrl(refreshUrl);
        return this;
    }

    public OAuth2Definition password(String tokenUrl) {
        setTokenUrl(tokenUrl);
        setFlow("password");
        return this;
    }

    public OAuth2Definition application(String tokenUrl) {
        return clientCredentials(tokenUrl);
    }

    public OAuth2Definition clientCredentials(String tokenUrl) {
        setTokenUrl(tokenUrl);
        setFlow("clientCredentials");
        return this;
    }

    public OAuth2Definition accessCode(String authorizationUrl, String tokenUrl) {
        return authorizationCode(authorizationUrl, tokenUrl);
    }

    public OAuth2Definition authorizationCode(String authorizationUrl, String tokenUrl) {
        setAuthorizationUrl(authorizationUrl);
        setTokenUrl(tokenUrl);
        setFlow("authorizationCode");
        return this;
    }

    public OAuth2Definition withScope(String key, String description) {
        scopes.add(new RestPropertyDefinition(key, description));
        return this;
    }

    public RestSecuritiesDefinition end() {
        return rest.getSecurityDefinitions();
    }

}
