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
    @UriParam(label = "security", defaultValue = "token", enums = "token,approle,kubernetes,jwt,cert")
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
     * service account token of the pod), jwt (a JWT, such as a workload identity token) or cert (the TLS client
     * certificate of the keyStore). The token is renewed before it expires, and the endpoint logs in again when the
     * token can no longer be renewed.
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
     * The HashiCorp Vault role to log in as, for the kubernetes and jwt logins. The jwt login can leave it out when the
     * login method has a default role.
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
     * the login method: approle, kubernetes or jwt
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
     * certificate and the PKCS#8 private key.
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
