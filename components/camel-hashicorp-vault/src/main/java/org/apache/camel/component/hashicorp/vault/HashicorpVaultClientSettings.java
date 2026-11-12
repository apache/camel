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
package org.apache.camel.component.hashicorp.vault;

import java.util.List;
import java.util.function.UnaryOperator;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.vault.HashicorpVaultConfiguration;

/**
 * Where HashiCorp Vault is and how to log in to it: the settings of a {@link HashicorpVaultClient}.
 */
public final class HashicorpVaultClientSettings {

    static final String TOKEN_ENV = "CAMEL_HASHICORP_VAULT_TOKEN";
    static final String HOST_ENV = "CAMEL_HASHICORP_VAULT_HOST";
    static final String PORT_ENV = "CAMEL_HASHICORP_VAULT_PORT";
    static final String SCHEME_ENV = "CAMEL_HASHICORP_VAULT_SCHEME";
    static final String CLOUD_ENV = "CAMEL_HASHICORP_VAULT_CLOUD";
    static final String NAMESPACE_ENV = "CAMEL_HASHICORP_VAULT_NAMESPACE";
    static final String AUTHENTICATION_ENV = "CAMEL_HASHICORP_VAULT_AUTHENTICATION";
    static final String ROLE_ID_ENV = "CAMEL_HASHICORP_VAULT_ROLE_ID";
    static final String SECRET_ID_ENV = "CAMEL_HASHICORP_VAULT_SECRET_ID";
    static final String ROLE_ENV = "CAMEL_HASHICORP_VAULT_ROLE";
    static final String JWT_ENV = "CAMEL_HASHICORP_VAULT_JWT";
    static final String JWT_FILE_ENV = "CAMEL_HASHICORP_VAULT_JWT_FILE";
    static final String AUTH_MOUNT_PATH_ENV = "CAMEL_HASHICORP_VAULT_AUTH_MOUNT_PATH";
    static final String TRUST_STORE_ENV = "CAMEL_HASHICORP_VAULT_TRUST_STORE";
    static final String TRUST_STORE_PASSWORD_ENV = "CAMEL_HASHICORP_VAULT_TRUST_STORE_PASSWORD";
    static final String KEY_STORE_ENV = "CAMEL_HASHICORP_VAULT_KEY_STORE";
    static final String KEY_STORE_PASSWORD_ENV = "CAMEL_HASHICORP_VAULT_KEY_STORE_PASSWORD";
    static final String AWS_IAM_SERVER_ID_ENV = "CAMEL_HASHICORP_VAULT_AWS_IAM_SERVER_ID";
    static final String AWS_REGION_ENV = "CAMEL_HASHICORP_VAULT_AWS_REGION";
    static final String AWS_STS_ENDPOINT_ENV = "CAMEL_HASHICORP_VAULT_AWS_STS_ENDPOINT";
    static final String AZURE_RESOURCE_ENV = "CAMEL_HASHICORP_VAULT_AZURE_RESOURCE";
    static final String AZURE_CLIENT_ID_ENV = "CAMEL_HASHICORP_VAULT_AZURE_CLIENT_ID";

    // any of these set means the settings come from the environment variables (the cloud variable alone does not)
    private static final List<String> ENVIRONMENT_SETTINGS = List.of(
            TOKEN_ENV, HOST_ENV, PORT_ENV, SCHEME_ENV, NAMESPACE_ENV, AUTHENTICATION_ENV, ROLE_ID_ENV, SECRET_ID_ENV,
            ROLE_ENV, JWT_ENV, JWT_FILE_ENV, AUTH_MOUNT_PATH_ENV, TRUST_STORE_ENV, TRUST_STORE_PASSWORD_ENV, KEY_STORE_ENV,
            KEY_STORE_PASSWORD_ENV, AWS_IAM_SERVER_ID_ENV, AWS_REGION_ENV, AWS_STS_ENDPOINT_ENV, AZURE_RESOURCE_ENV,
            AZURE_CLIENT_ID_ENV);

    private String host;
    private String port;
    private String scheme;
    private boolean cloud;
    private String namespace;
    private String authentication;
    private String token;
    private String roleId;
    private String secretId;
    private String role;
    private String jwt;
    private String jwtFile;
    private String authMountPath;
    private String trustStore;
    private String trustStorePassword;
    private String keyStore;
    private String keyStorePassword;
    private String awsIamServerId;
    private String awsRegion;
    private String awsStsEndpoint;
    private String azureResource;
    private String azureClientId;

    /**
     * The settings of the properties function and the secret refresh task: from the CAMEL_HASHICORP_VAULT_* environment
     * variables when one of them is set, else from the vault configuration.
     *
     * @param configuration the vault configuration (camel.vault.hashicorp), may be null
     */
    public static HashicorpVaultClientSettings fromEnvironmentOrConfiguration(HashicorpVaultConfiguration configuration) {
        return fromEnvironmentOrConfiguration(configuration, System::getenv);
    }

    static HashicorpVaultClientSettings fromEnvironmentOrConfiguration(
            HashicorpVaultConfiguration configuration, UnaryOperator<String> environment) {
        HashicorpVaultClientSettings settings = new HashicorpVaultClientSettings();
        if (ENVIRONMENT_SETTINGS.stream().anyMatch(name -> ObjectHelper.isNotEmpty(environment.apply(name)))) {
            settings.setHost(environment.apply(HOST_ENV));
            settings.setPort(environment.apply(PORT_ENV));
            settings.setScheme(environment.apply(SCHEME_ENV));
            settings.setCloud(Boolean.parseBoolean(environment.apply(CLOUD_ENV)));
            settings.setNamespace(environment.apply(NAMESPACE_ENV));
            settings.setAuthentication(environment.apply(AUTHENTICATION_ENV));
            settings.setToken(environment.apply(TOKEN_ENV));
            settings.setRoleId(environment.apply(ROLE_ID_ENV));
            settings.setSecretId(environment.apply(SECRET_ID_ENV));
            settings.setRole(environment.apply(ROLE_ENV));
            settings.setJwt(environment.apply(JWT_ENV));
            settings.setJwtFile(environment.apply(JWT_FILE_ENV));
            settings.setAuthMountPath(environment.apply(AUTH_MOUNT_PATH_ENV));
            settings.setTrustStore(environment.apply(TRUST_STORE_ENV));
            settings.setTrustStorePassword(environment.apply(TRUST_STORE_PASSWORD_ENV));
            settings.setKeyStore(environment.apply(KEY_STORE_ENV));
            settings.setKeyStorePassword(environment.apply(KEY_STORE_PASSWORD_ENV));
            settings.setAwsIamServerId(environment.apply(AWS_IAM_SERVER_ID_ENV));
            settings.setAwsRegion(environment.apply(AWS_REGION_ENV));
            settings.setAwsStsEndpoint(environment.apply(AWS_STS_ENDPOINT_ENV));
            settings.setAzureResource(environment.apply(AZURE_RESOURCE_ENV));
            settings.setAzureClientId(environment.apply(AZURE_CLIENT_ID_ENV));
        } else if (configuration != null) {
            settings.setHost(configuration.getHost());
            settings.setPort(configuration.getPort());
            settings.setScheme(configuration.getScheme());
            settings.setCloud(configuration.isCloud());
            settings.setNamespace(configuration.getNamespace());
            settings.setAuthentication(configuration.getAuthentication());
            settings.setToken(configuration.getToken());
            settings.setRoleId(configuration.getRoleId());
            settings.setSecretId(configuration.getSecretId());
            settings.setRole(configuration.getRole());
            settings.setJwt(configuration.getJwt());
            settings.setJwtFile(configuration.getJwtFile());
            settings.setAuthMountPath(configuration.getAuthMountPath());
            settings.setTrustStore(configuration.getTrustStore());
            settings.setTrustStorePassword(configuration.getTrustStorePassword());
            settings.setKeyStore(configuration.getKeyStore());
            settings.setKeyStorePassword(configuration.getKeyStorePassword());
            settings.setAwsIamServerId(configuration.getAwsIamServerId());
            settings.setAwsRegion(configuration.getAwsRegion());
            settings.setAwsStsEndpoint(configuration.getAwsStsEndpoint());
            settings.setAzureResource(configuration.getAzureResource());
            settings.setAzureClientId(configuration.getAzureClientId());
        }
        return settings;
    }

    /**
     * The namespace on HashiCorp Cloud: the namespace when deployed on HashiCorp Cloud and a namespace is set, else
     * null. The secrets engines and the login methods are in this namespace.
     */
    public String getCloudNamespace() {
        return cloud && ObjectHelper.isNotEmpty(namespace) ? namespace : null;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public boolean isCloud() {
        return cloud;
    }

    public void setCloud(boolean cloud) {
        this.cloud = cloud;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getAuthentication() {
        return authentication;
    }

    /**
     * How to log in: token (the default), approle, kubernetes, jwt, cert, aws-iam, azure-msi or gcp-gce
     */
    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
    }

    public String getJwtFile() {
        return jwtFile;
    }

    public void setJwtFile(String jwtFile) {
        this.jwtFile = jwtFile;
    }

    public String getAuthMountPath() {
        return authMountPath;
    }

    public void setAuthMountPath(String authMountPath) {
        this.authMountPath = authMountPath;
    }

    public String getTrustStore() {
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    public String getAwsIamServerId() {
        return awsIamServerId;
    }

    public void setAwsIamServerId(String awsIamServerId) {
        this.awsIamServerId = awsIamServerId;
    }

    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    public String getAwsStsEndpoint() {
        return awsStsEndpoint;
    }

    public void setAwsStsEndpoint(String awsStsEndpoint) {
        this.awsStsEndpoint = awsStsEndpoint;
    }

    public String getAzureResource() {
        return azureResource;
    }

    public void setAzureResource(String azureResource) {
        this.azureResource = azureResource;
    }

    public String getAzureClientId() {
        return azureClientId;
    }

    public void setAzureClientId(String azureClientId) {
        this.azureClientId = azureClientId;
    }
}
