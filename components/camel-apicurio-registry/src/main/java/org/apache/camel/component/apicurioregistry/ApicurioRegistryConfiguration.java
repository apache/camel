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
package org.apache.camel.component.apicurioregistry;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class ApicurioRegistryConfiguration implements Cloneable {

    @UriParam(label = "common", description = "The Apicurio Registry base URL")
    @Metadata(required = true)
    private String registryUrl;

    @UriParam(label = "producer",
              enums = "createArtifact,updateArtifact,deleteArtifact,getArtifactContent,getArtifactMetadata,searchArtifacts,listVersions,createGroup,testCompatibility,validate",
              description = "The operation to perform")
    private String operation;

    @UriParam(label = "producer", description = "The default artifact type", defaultValue = "JSON")
    private String artifactType = "JSON";

    @UriParam(label = "security", enums = "none,basic,oidc", defaultValue = "none",
              description = "The authentication type to use")
    private String authType = "none";

    @UriParam(label = "security", security = "secret", description = "Username for basic authentication")
    private String username;

    @UriParam(label = "security", security = "secret", description = "Password for basic authentication")
    private String password;

    @UriParam(label = "security", description = "OAuth2 token endpoint URL")
    private String tokenEndpoint;

    @UriParam(label = "security", security = "secret", description = "OAuth2 client ID")
    private String clientId;

    @UriParam(label = "security", security = "secret", description = "OAuth2 client secret")
    private String clientSecret;

    @UriParam(label = "security", description = "OAuth2 scope")
    private String scope;

    @UriParam(label = "producer",
              description = "Behavior when artifact already exists",
              enums = "FAIL,CREATE_VERSION,FIND_OR_CREATE_VERSION",
              defaultValue = "FAIL")
    private String ifExists = "FAIL";

    @UriParam(label = "consumer",
              description = "Whether to fetch the content for each new version found by the consumer",
              defaultValue = "false")
    private boolean fetchContent;

    @UriParam(label = "producer",
              description = "Whether to throw an exception on validation failure (validate operation). When false, sets result headers instead.",
              defaultValue = "true")
    private boolean failOnValidation = true;

    @UriParam(label = "producer",
              description = "Schema cache TTL in milliseconds for the validate operation. 0 means no caching.",
              defaultValue = "300000")
    private long cacheTtl = 300000;

    public String getRegistryUrl() {
        return registryUrl;
    }

    public void setRegistryUrl(String registryUrl) {
        this.registryUrl = registryUrl;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getArtifactType() {
        return artifactType;
    }

    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getAuthType() {
        return authType;
    }

    public void setAuthType(String authType) {
        this.authType = authType;
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

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public void setTokenEndpoint(String tokenEndpoint) {
        this.tokenEndpoint = tokenEndpoint;
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

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getIfExists() {
        return ifExists;
    }

    public void setIfExists(String ifExists) {
        this.ifExists = ifExists;
    }

    public boolean isFetchContent() {
        return fetchContent;
    }

    public void setFetchContent(boolean fetchContent) {
        this.fetchContent = fetchContent;
    }

    public boolean isFailOnValidation() {
        return failOnValidation;
    }

    public void setFailOnValidation(boolean failOnValidation) {
        this.failOnValidation = failOnValidation;
    }

    public long getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(long cacheTtl) {
        this.cacheTtl = cacheTtl;
    }

    public ApicurioRegistryConfiguration copy() {
        try {
            return (ApicurioRegistryConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
