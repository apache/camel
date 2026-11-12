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
package org.apache.camel.vault;

import org.apache.camel.spi.Metadata;
import org.jspecify.annotations.Nullable;

/**
 * Configuration for access to Hashicorp Vault Secrets.
 *
 * @since 3.19
 */
public class HashicorpVaultConfiguration extends VaultConfiguration {

    @Metadata(defaultValue = "token", enums = "token,approle,kubernetes,jwt,cert,aws-iam,azure-msi,gcp-gce")
    private @Nullable String authentication = "token";
    @Metadata(security = "secret")
    private @Nullable String token;
    @Metadata(security = "secret")
    private @Nullable String roleId;
    @Metadata(security = "secret")
    private @Nullable String secretId;
    @Metadata
    private @Nullable String role;
    @Metadata(security = "secret")
    private @Nullable String jwt;
    @Metadata
    private @Nullable String jwtFile;
    @Metadata
    private @Nullable String authMountPath;
    @Metadata
    private @Nullable String trustStore;
    @Metadata(security = "secret")
    private @Nullable String trustStorePassword;
    @Metadata
    private @Nullable String keyStore;
    @Metadata(security = "secret")
    private @Nullable String keyStorePassword;
    @Metadata
    private @Nullable String awsIamServerId;
    @Metadata
    private @Nullable String awsRegion;
    @Metadata
    private @Nullable String awsStsEndpoint;
    @Metadata(defaultValue = "https://management.azure.com/")
    private @Nullable String azureResource = "https://management.azure.com/";
    @Metadata
    private @Nullable String azureClientId;
    @Metadata(required = true)
    private @Nullable String host;
    @Metadata(required = true)
    private @Nullable String port;
    @Metadata(required = true)
    private @Nullable String scheme;
    @Metadata
    private boolean cloud;
    @Metadata
    private @Nullable String namespace;
    @Metadata
    private boolean refreshEnabled;
    @Metadata(defaultValue = "60000")
    private long refreshPeriod = 60000;
    @Metadata
    private @Nullable String secrets;

    public @Nullable String getToken() {
        return token;
    }

    /**
     * Token to access hashicorp vault, for the token login
     */
    public void setToken(@Nullable String token) {
        this.token = token;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getAuthentication() {
        return authentication;
    }

    /**
     * How to log in to hashicorp vault: token (a fixed token), approle (a role ID and a secret ID), kubernetes (the
     * service account token of the pod), jwt (a JWT, such as a workload identity token), cert (the TLS client
     * certificate of the keyStore), aws-iam (the AWS credentials of the application), azure-msi (the Azure managed
     * identity) or gcp-gce (the service account of the Google Compute Engine instance). Camel renews the token before
     * it expires, and logs in again when the token can no longer be renewed.
     *
     * @since 4.24
     */
    public void setAuthentication(@Nullable String authentication) {
        this.authentication = authentication;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getRoleId() {
        return roleId;
    }

    /**
     * The role ID of the AppRole, for the approle login
     *
     * @since 4.24
     */
    public void setRoleId(@Nullable String roleId) {
        this.roleId = roleId;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getSecretId() {
        return secretId;
    }

    /**
     * The secret ID of the AppRole, for the approle login
     *
     * @since 4.24
     */
    public void setSecretId(@Nullable String secretId) {
        this.secretId = secretId;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getRole() {
        return role;
    }

    /**
     * The hashicorp vault role to log in as, for the kubernetes, jwt, cert, aws-iam, azure-msi and gcp-gce logins. The
     * jwt login can leave it out when the login method has a default role, the aws-iam login to log in as the role
     * named as the IAM principal, and the cert login to let Vault pick the certificate role that trusts the
     * certificate.
     *
     * @since 4.24
     */
    public void setRole(@Nullable String role) {
        this.role = role;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getJwt() {
        return jwt;
    }

    /**
     * The JWT to log in with, for the jwt and kubernetes logins. For a JWT that is replaced over time, such as a
     * projected service account token, use jwtFile instead.
     *
     * @since 4.24
     */
    public void setJwt(@Nullable String jwt) {
        this.jwt = jwt;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getJwtFile() {
        return jwtFile;
    }

    /**
     * The file to read the JWT from at every login, for the jwt and kubernetes logins. The kubernetes login reads
     * /var/run/secrets/kubernetes.io/serviceaccount/token when neither jwt nor jwtFile is set.
     *
     * @since 4.24
     */
    public void setJwtFile(@Nullable String jwtFile) {
        this.jwtFile = jwtFile;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getAuthMountPath() {
        return authMountPath;
    }

    /**
     * The path the login method is mounted at in hashicorp vault (without auth/), when it is not the default path of
     * the login method: approle, kubernetes, jwt, cert, aws (for aws-iam), azure (for azure-msi) or gcp (for gcp-gce)
     *
     * @since 4.24
     */
    public void setAuthMountPath(@Nullable String authMountPath) {
        this.authMountPath = authMountPath;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getTrustStore() {
        return trustStore;
    }

    /**
     * The file with the certificates to trust for the TLS connection to hashicorp vault, such as the CA certificate of
     * a private CA: a PKCS12 or JKS trust store, or PEM certificates. When not set, the JVM trusts the certificate as
     * for any other TLS connection.
     *
     * @since 4.24
     */
    public void setTrustStore(@Nullable String trustStore) {
        this.trustStore = trustStore;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getTrustStorePassword() {
        return trustStorePassword;
    }

    /**
     * The password of the trust store, when it is a PKCS12 or JKS trust store
     *
     * @since 4.24
     */
    public void setTrustStorePassword(@Nullable String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getKeyStore() {
        return keyStore;
    }

    /**
     * The file with the TLS client certificate and its private key, which Camel presents to hashicorp vault: for the
     * cert login, or for a Vault that requires client certificates. A PKCS12 or JKS key store, or PEM with the
     * certificate and its unencrypted private key, in PKCS#8 or, for an RSA key, in PKCS#1.
     *
     * @since 4.24
     */
    public void setKeyStore(@Nullable String keyStore) {
        this.keyStore = keyStore;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getKeyStorePassword() {
        return keyStorePassword;
    }

    /**
     * The password of the key store and of its private key, when it is a PKCS12 or JKS key store
     *
     * @since 4.24
     */
    public void setKeyStorePassword(@Nullable String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getAwsIamServerId() {
        return awsIamServerId;
    }

    /**
     * The value of the X-Vault-AWS-IAM-Server-ID header of the aws-iam login, when the AWS login of hashicorp vault
     * requires one (iam_server_id_header_value)
     *
     * @since 4.24
     */
    public void setAwsIamServerId(@Nullable String awsIamServerId) {
        this.awsIamServerId = awsIamServerId;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getAwsRegion() {
        return awsRegion;
    }

    /**
     * The AWS region the aws-iam login signs its request for, which must be the region of awsStsEndpoint. When not set:
     * us-east-1 with the global endpoint, else the region of the AWS SDK default region chain (such as the AWS_REGION
     * environment variable).
     *
     * @since 4.24
     */
    public void setAwsRegion(@Nullable String awsRegion) {
        this.awsRegion = awsRegion;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getAwsStsEndpoint() {
        return awsStsEndpoint;
    }

    /**
     * The AWS STS endpoint of the aws-iam login, which the AWS login of hashicorp vault must accept (its sts_endpoint).
     * When not set, the global endpoint https://sts.amazonaws.com/.
     *
     * @since 4.24
     */
    public void setAwsStsEndpoint(@Nullable String awsStsEndpoint) {
        this.awsStsEndpoint = awsStsEndpoint;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getAzureResource() {
        return azureResource;
    }

    /**
     * The resource (audience) of the managed identity token of the azure-msi login, which must be the resource of the
     * Azure login of hashicorp vault
     *
     * @since 4.24
     */
    public void setAzureResource(@Nullable String azureResource) {
        this.azureResource = azureResource;
    }

    /**
     * @since 4.24
     */
    public @Nullable String getAzureClientId() {
        return azureClientId;
    }

    /**
     * The client ID of a user-assigned managed identity, for the azure-msi login. When not set, the system-assigned
     * managed identity.
     *
     * @since 4.24
     */
    public void setAzureClientId(@Nullable String azureClientId) {
        this.azureClientId = azureClientId;
    }

    public @Nullable String getHost() {
        return host;
    }

    /**
     * Host to access hashicorp vault
     */
    public void setHost(@Nullable String host) {
        this.host = host;
    }

    public @Nullable String getPort() {
        return port;
    }

    /**
     * Port to access hashicorp vault
     */
    public void setPort(@Nullable String port) {
        this.port = port;
    }

    public @Nullable String getScheme() {
        return scheme;
    }

    /**
     * Scheme to access hashicorp vault
     */
    public void setScheme(@Nullable String scheme) {
        this.scheme = scheme;
    }

    public boolean isCloud() {
        return cloud;
    }

    /**
     * Determine if the Hashicorp Vault is deployed on Hashicorp Cloud or not
     */
    public void setCloud(boolean cloud) {
        this.cloud = cloud;
    }

    public @Nullable String getNamespace() {
        return namespace;
    }

    /**
     * If the Hashicorp Vault instance is deployed on Hashicorp Cloud, this field will determine the namespace
     */
    public void setNamespace(@Nullable String namespace) {
        this.namespace = namespace;
    }

    public boolean isRefreshEnabled() {
        return refreshEnabled;
    }

    /**
     * Whether to automatically reload Camel upon secrets being updated in Hashicorp Vault.
     */
    public void setRefreshEnabled(boolean refreshEnabled) {
        this.refreshEnabled = refreshEnabled;
    }

    public long getRefreshPeriod() {
        return refreshPeriod;
    }

    /**
     * The period (millis) between checking Hashicorp Vault for updated secrets.
     */
    public void setRefreshPeriod(long refreshPeriod) {
        this.refreshPeriod = refreshPeriod;
    }

    public @Nullable String getSecrets() {
        return secrets;
    }

    /**
     * Specify the secret names (or pattern) to check for updates. Multiple secrets can be separated by comma.
     */
    public void setSecrets(@Nullable String secrets) {
        this.secrets = secrets;
    }
}
