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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.springframework.vault.core.VaultTemplate;

@UriParams
public class HashicorpVaultConfiguration implements Cloneable {

    @UriPath
    private String secretsEngine;
    @UriParam
    @Metadata(autowired = true)
    private VaultTemplate vaultTemplate;
    @UriParam
    private String host;
    @UriParam(defaultValue = "8200")
    private String port = "8200";
    @UriParam(defaultValue = "https")
    private String scheme = "https";
    @UriParam
    private String secretPath;
    @UriParam(label = "security", defaultValue = "token", enums = "token,approle,kubernetes,jwt,cert,aws-iam,azure-msi,gcp-gce")
    private String authentication = "token";
    @UriParam(label = "security", security = "secret")
    private String token;
    @UriParam(label = "security", security = "secret")
    private String roleId;
    @UriParam(label = "security", security = "secret")
    private String secretId;
    @UriParam(label = "security")
    private String role;
    @UriParam(label = "security", security = "secret")
    private String jwt;
    @UriParam(label = "security")
    private String jwtFile;
    @UriParam(label = "security")
    private String authMountPath;
    @UriParam(label = "security")
    private String trustStore;
    @UriParam(label = "security", security = "secret")
    private String trustStorePassword;
    @UriParam(label = "security")
    private String keyStore;
    @UriParam(label = "security", security = "secret")
    private String keyStorePassword;
    @UriParam(label = "security")
    private String awsIamServerId;
    @UriParam(label = "security")
    private String awsRegion;
    @UriParam(label = "security")
    private String awsStsEndpoint;
    @UriParam(label = "security", defaultValue = "https://management.azure.com/")
    private String azureResource = "https://management.azure.com/";
    @UriParam(label = "security")
    private String azureClientId;
    @UriParam
    private boolean cloud;
    @UriParam
    private String namespace;

    @UriParam(label = "producer")
    private HashicorpVaultOperation operation = HashicorpVaultOperation.createSecret;

    /**
     * Instance of Vault template
     */
    public VaultTemplate getVaultTemplate() {
        return vaultTemplate;
    }

    public void setVaultTemplate(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }

    /**
     * Vault Name to be used
     */
    public String getSecretsEngine() {
        return secretsEngine;
    }

    public void setSecretsEngine(String secretsEngine) {
        this.secretsEngine = secretsEngine;
    }

    /**
     * How to log in to HashiCorp Vault: token (a fixed token), approle (a role ID and a secret ID), kubernetes (the
     * service account token of the pod), jwt (a JWT, such as a workload identity token), cert (the TLS client
     * certificate of the keyStore), aws-iam (the AWS credentials of the application), azure-msi (the Azure managed
     * identity) or gcp-gce (the service account of the Google Compute Engine instance). The token is renewed before it
     * expires, and the endpoint logs in again when the token can no longer be renewed.
     */
    public String getAuthentication() {
        return authentication;
    }

    public void setAuthentication(String authentication) {
        this.authentication = authentication;
    }

    /**
     * Token to be used, for the token login
     */
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /**
     * The role ID of the AppRole, for the approle login
     */
    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    /**
     * The secret ID of the AppRole, for the approle login
     */
    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    /**
     * The HashiCorp Vault role to log in as, for the kubernetes, jwt, cert, aws-iam, azure-msi and gcp-gce logins. The
     * jwt login can leave it out when the login method has a default role, and the cert login to let Vault pick the
     * certificate role that trusts the certificate.
     */
    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    /**
     * The JWT to log in with, for the jwt and kubernetes logins. For a JWT that is replaced over time, such as a
     * projected service account token, use jwtFile instead.
     */
    public String getJwt() {
        return jwt;
    }

    public void setJwt(String jwt) {
        this.jwt = jwt;
    }

    /**
     * The file to read the JWT from at every login, for the jwt and kubernetes logins. The kubernetes login reads
     * /var/run/secrets/kubernetes.io/serviceaccount/token when neither jwt nor jwtFile is set.
     */
    public String getJwtFile() {
        return jwtFile;
    }

    public void setJwtFile(String jwtFile) {
        this.jwtFile = jwtFile;
    }

    /**
     * The path the login method is mounted at in HashiCorp Vault (without auth/), when it is not the default path of
     * the login method: approle, kubernetes, jwt, cert, aws (for aws-iam), azure (for azure-msi) or gcp (for gcp-gce)
     */
    public String getAuthMountPath() {
        return authMountPath;
    }

    public void setAuthMountPath(String authMountPath) {
        this.authMountPath = authMountPath;
    }

    /**
     * The file with the certificates to trust for the TLS connection to HashiCorp Vault, such as the CA certificate of
     * a private CA: a PKCS12 or JKS trust store, or PEM certificates. When not set, the JVM trusts the certificate as
     * for any other TLS connection.
     */
    public String getTrustStore() {
        return trustStore;
    }

    public void setTrustStore(String trustStore) {
        this.trustStore = trustStore;
    }

    /**
     * The password of the trust store, when it is a PKCS12 or JKS trust store
     */
    public String getTrustStorePassword() {
        return trustStorePassword;
    }

    public void setTrustStorePassword(String trustStorePassword) {
        this.trustStorePassword = trustStorePassword;
    }

    /**
     * The file with the TLS client certificate and its private key, which the endpoint presents to HashiCorp Vault: for
     * the cert login, or for a Vault that requires client certificates. A PKCS12 or JKS key store, or PEM with the
     * certificate and its unencrypted private key, in PKCS#8 or, for an RSA key, in PKCS#1.
     */
    public String getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(String keyStore) {
        this.keyStore = keyStore;
    }

    /**
     * The password of the key store and of its private key, when it is a PKCS12 or JKS key store
     */
    public String getKeyStorePassword() {
        return keyStorePassword;
    }

    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }

    /**
     * The value of the X-Vault-AWS-IAM-Server-ID header of the aws-iam login, when the AWS login of HashiCorp Vault
     * requires one (iam_server_id_header_value)
     */
    public String getAwsIamServerId() {
        return awsIamServerId;
    }

    public void setAwsIamServerId(String awsIamServerId) {
        this.awsIamServerId = awsIamServerId;
    }

    /**
     * The AWS region the aws-iam login signs its request for, which must be the region of awsStsEndpoint: us-east-1 for
     * the global endpoint. When not set, the region of the AWS SDK default region chain (such as the AWS_REGION
     * environment variable).
     */
    public String getAwsRegion() {
        return awsRegion;
    }

    public void setAwsRegion(String awsRegion) {
        this.awsRegion = awsRegion;
    }

    /**
     * The AWS STS endpoint of the aws-iam login, which the AWS login of HashiCorp Vault must accept (its sts_endpoint).
     * When not set, the global endpoint https://sts.amazonaws.com/.
     */
    public String getAwsStsEndpoint() {
        return awsStsEndpoint;
    }

    public void setAwsStsEndpoint(String awsStsEndpoint) {
        this.awsStsEndpoint = awsStsEndpoint;
    }

    /**
     * The resource (audience) of the managed identity token of the azure-msi login, which must be the resource of the
     * Azure login of HashiCorp Vault
     */
    public String getAzureResource() {
        return azureResource;
    }

    public void setAzureResource(String azureResource) {
        this.azureResource = azureResource;
    }

    /**
     * The client ID of a user-assigned managed identity, for the azure-msi login. When not set, the system-assigned
     * managed identity.
     */
    public String getAzureClientId() {
        return azureClientId;
    }

    public void setAzureClientId(String azureClientId) {
        this.azureClientId = azureClientId;
    }

    /**
     * Hashicorp Vault instance host to be used
     */
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    /**
     * Hashicorp Vault instance port to be used
     */
    public String getPort() {
        return port;
    }

    public void setPort(String port) {
        this.port = port;
    }

    /**
     * Hashicorp Vault instance scheme to be used
     */
    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Determine if the Hashicorp Vault is deployed on Hashicorp Cloud or not
     */
    public boolean isCloud() {
        return cloud;
    }

    public void setCloud(boolean cloud) {
        this.cloud = cloud;
    }

    /**
     * If the Hashicorp Vault instance is deployed on Hashicorp Cloud, this field will determine the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Hashicorp Vault instance secret Path to be used
     */
    public String getSecretPath() {
        return secretPath;
    }

    public void setSecretPath(String secretPath) {
        this.secretPath = secretPath;
    }

    /**
     * Operation to be performed
     */
    public HashicorpVaultOperation getOperation() {
        return operation;
    }

    public void setOperation(HashicorpVaultOperation operation) {
        this.operation = operation;
    }

    // *************************************************
    //
    // *************************************************

    public HashicorpVaultConfiguration copy() {
        try {
            return (HashicorpVaultConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
