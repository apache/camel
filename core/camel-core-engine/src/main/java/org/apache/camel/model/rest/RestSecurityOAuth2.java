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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.Metadata;

/**
 * Rest security OAuth2 definition
 */
@Metadata(label = "rest,security")
@XmlRootElement(name = "oauth2")
@XmlAccessorType(XmlAccessType.FIELD)
public class RestSecurityOAuth2 extends RestSecurityDefinition {

    @XmlAttribute
    private String authorizationUrl;

    @XmlAttribute
    private String tokenUrl;

    @XmlAttribute
    @Metadata(enums = "implicit,password,application,accessCode")
    private String flow;

    @XmlElement(name = "scopes")
    private List<RestPropertyDefinition> scopes = new ArrayList<>();

    public RestSecurityOAuth2() {
    }

    public RestSecurityOAuth2(RestDefinition rest) {
        super(rest);
    }

    public String getAuthorizationUrl() {
        return authorizationUrl;
    }

    /**
     * The authorization URL to be used for this flow. This SHOULD be in the
     * form of a URL. Required for implicit and access code flows
     */
    public void setAuthorizationUrl(String authorizationUrl) {
        this.authorizationUrl = authorizationUrl;
    }

    public String getTokenUrl() {
        return tokenUrl;
    }

    /**
     * The token URL to be used for this flow. This SHOULD be in the form of a
     * URL. Required for password, application, and access code flows.
     */
    public void setTokenUrl(String tokenUrl) {
        this.tokenUrl = tokenUrl;
    }

    public String getFlow() {
        return flow;
    }

    /**
     * The flow used by the OAuth2 security scheme. Valid values are "implicit",
     * "password", "application" or "accessCode".
     */
    public void setFlow(String flow) {
        this.flow = flow;
    }

    public List<RestPropertyDefinition> getScopes() {
        return scopes;
    }

    /**
     * The available scopes for an OAuth2 security scheme
     */
    public void setScopes(List<RestPropertyDefinition> scopes) {
        this.scopes = scopes;
    }

    public RestSecurityOAuth2 authorizationUrl(String authorizationUrl) {
        setAuthorizationUrl(authorizationUrl);
        setFlow("implicit");
        return this;
    }

    public RestSecurityOAuth2 password(String tokenUrl) {
        setTokenUrl(tokenUrl);
        setFlow("password");
        return this;
    }

    public RestSecurityOAuth2 application(String tokenUrl) {
        setTokenUrl(tokenUrl);
        setFlow("application");
        return this;
    }

    public RestSecurityOAuth2 accessCode(String authorizationUrl, String tokenUrl) {
        setAuthorizationUrl(authorizationUrl);
        setTokenUrl(tokenUrl);
        setFlow("accessCode");
        return this;
    }

    public RestSecurityOAuth2 withScope(String key, String description) {
        scopes.add(new RestPropertyDefinition(key, description));
        return this;
    }

    public RestSecuritiesDefinition end() {
        return rest.getSecurityDefinitions();
    }

}
