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
package org.apache.camel.component.box;

import java.util.Map;

import com.box.sdk.EncryptionAlgorithm;
import com.box.sdk.IAccessTokenCache;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.box.internal.BoxApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.jsse.SSLContextParameters;

/**
 * Component configuration for Box component.
 */
@UriParams
public class BoxConfiguration {

    /**
     * Authentication Types for Connection
     */
    public static final String APP_ENTERPRISE_AUTHENTICATION = "APP_ENTERPRISE_AUTHENTICATION";
    public static final String APP_USER_AUTHENTICATION = "APP_USER_AUTHENTICATION";
    public static final String STANDARD_AUTHENTICATION = "STANDARD_AUTHENTICATION";

    /**
     * Encryption Algorithm Types for Server Authentication.
     */
    public static final String RSA_SHA_512 = "RSA_SHA_512";
    public static final String RSA_SHA_384 = "RSA_SHA_384";
    public static final String RSA_SHA_256 = "RSA_SHA_256";

    @UriPath
    @Metadata(required = true)
    private BoxApiName apiName;

    @UriPath
    @Metadata(required = true)
    private String methodName;

    @UriParam
    private String enterpriseId;

    @UriParam
    private String userId;

    @UriParam
    private String clientId;

    @UriParam(label = "security", secret = true)
    private String publicKeyId;

    @UriParam(label = "security", secret = true)
    private String privateKeyFile;

    @UriParam(label = "security", secret = true)
    private String privateKeyPassword;

    @UriParam(label = "security", secret = true)
    private String clientSecret;
    @UriParam(label = "security", secret = true)
    private String userName;
    @UriParam(label = "security", secret = true)
    private String userPassword;

    @UriParam(label = "advanced,security")
    private IAccessTokenCache accessTokenCache;

    @UriParam(label = "advanced,security", defaultValue = "100")
    private int maxCacheEntries = 100;

    @UriParam(label = "advanced,security", defaultValue = RSA_SHA_256)
    private EncryptionAlgorithm encryptionAlgorithm = EncryptionAlgorithm.RSA_SHA_256;

    @UriParam(label = "authentication", defaultValue = APP_USER_AUTHENTICATION)
    private String authenticationType = APP_USER_AUTHENTICATION;

    @UriParam(label = "advanced")
    private Map<String, Object> httpParams;
    @UriParam(label = "security")
    private SSLContextParameters sslContextParameters;

    /**
     * What kind of operation to perform
     *
     * @return the API Name
     */
    public BoxApiName getApiName() {
        return apiName;
    }

    /**
     * What kind of operation to perform
     *
     * @param apiName
     *            the API Name to set
     */
    public void setApiName(BoxApiName apiName) {
        this.apiName = apiName;
    }

    /**
     * What sub operation to use for the selected operation
     *
     * @return the methodName
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * What sub operation to use for the selected operation
     *
     * @param methodName
     *            the methodName to set
     */
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    /**
     * The enterprise ID to use for an App Enterprise.
     *
     * @return the enterpriseId
     */
    public String getEnterpriseId() {
        return enterpriseId;
    }

    /**
     * The enterprise ID to use for an App Enterprise.
     *
     * @param enterpriseId
     *            the enterpriseId to set
     */
    public void setEnterpriseId(String enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    /**
     * The user ID to use for an App User.
     *
     * @return the userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * The user ID to use for an App User.
     *
     * @param userId
     *            the userId to set
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * The ID for public key for validating the JWT signature.
     *
     * @return the publicKeyId
     */
    public String getPublicKeyId() {
        return publicKeyId;
    }

    /**
     * The ID for public key for validating the JWT signature.
     *
     * @param publicKeyId
     *            the publicKeyId to set
     */
    public void setPublicKeyId(String publicKeyId) {
        this.publicKeyId = publicKeyId;
    }

    /**
     * The private key for generating the JWT signature.
     *
     * @return the privateKey
     */
    public String getPrivateKeyFile() {
        return privateKeyFile;
    }

    /**
     * The private key for generating the JWT signature.
     *
     * @param privateKey
     *            the privateKey to set
     */
    public void setPrivateKeyFile(String privateKey) {
        this.privateKeyFile = privateKey;
    }

    /**
     * The password for the private key.
     *
     * @return the privateKeyPassword
     */
    public String getPrivateKeyPassword() {
        return privateKeyPassword;
    }

    /**
     * The password for the private key.
     *
     * @param privateKeyPassword
     *            the privateKeyPassword to set
     */
    public void setPrivateKeyPassword(String privateKeyPassword) {
        this.privateKeyPassword = privateKeyPassword;
    }

    /**
     * The maximum number of access tokens in cache.
     *
     * @return the maxCacheEntries
     */
    public int getMaxCacheEntries() {
        return maxCacheEntries;
    }

    /**
     * The maximum number of access tokens in cache.
     *
     * @param maxCacheEntries
     *            the maxCacheEntries to set
     */
    public void setMaxCacheEntries(int maxCacheEntries) {
        this.maxCacheEntries = maxCacheEntries;
    }

    public void setMaxCacheEntries(String maxCacheEntries) {
        try {
            this.maxCacheEntries = Integer.decode(maxCacheEntries);
        } catch (NumberFormatException e) {
            throw new RuntimeCamelException(String.format("Invalid 'maxCacheEntries' value: %s", maxCacheEntries), e);
        }
    }

    /**
     * The type of encryption algorithm for JWT.
     *
     * @return the encryptionAlgorithm
     */
    public EncryptionAlgorithm getEncryptionAlgorithm() {
        return encryptionAlgorithm;
    }

    /**
     * The type of encryption algorithm for JWT.
     *
     * <p>
     * Supported Algorithms:
     * <ul>
     * <li>RSA_SHA_256</li>
     * <li>RSA_SHA_384</li>
     * <li>RSA_SHA_512</li>
     * </ul>
     *
     * @param encryptionAlgorithm
     *            the encryptionAlgorithm to set
     */
    public void setEncryptionAlgorithm(EncryptionAlgorithm encryptionAlgorithm) {
        this.encryptionAlgorithm = encryptionAlgorithm;
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        switch (encryptionAlgorithm) {
            case RSA_SHA_256:
                this.encryptionAlgorithm = EncryptionAlgorithm.RSA_SHA_256;
                return;
            case RSA_SHA_384:
                this.encryptionAlgorithm = EncryptionAlgorithm.RSA_SHA_384;
                return;
            case RSA_SHA_512:
                this.encryptionAlgorithm = EncryptionAlgorithm.RSA_SHA_512;
                return;
            default:
                throw new RuntimeCamelException(String.format("Invalid Encryption Algorithm: %s", encryptionAlgorithm));
        }
    }

    /**
     * The type of authentication for connection.
     *
     * <p>
     * Types of Authentication:
     * <ul>
     * <li>STANDARD_AUTHENTICATION - OAuth 2.0 (3-legged)</li>
     * <li>SERVER_AUTHENTICATION - OAuth 2.0 with JSON Web Tokens</li>
     * </ul>
     *
     * @return the authenticationType
     */
    public String getAuthenticationType() {
        return authenticationType;
    }

    /**
     * The type of authentication for connection.
     *
     * <p>
     * Types of Authentication:
     * <ul>
     * <li>STANDARD_AUTHENTICATION - OAuth 2.0 (3-legged)</li>
     * <li>SERVER_AUTHENTICATION - OAuth 2.0 with JSON Web Tokens</li>
     * </ul>
     *
     * @param authenticationType
     *            the authenticationType to set
     */
    public void setAuthenticationType(String authenticationType) {
        switch (authenticationType) {
            case STANDARD_AUTHENTICATION:
            case APP_USER_AUTHENTICATION:
            case APP_ENTERPRISE_AUTHENTICATION:
                this.authenticationType = authenticationType;
                return;
            default:
                throw new RuntimeCamelException(String.format("Invalid Authentication Type: %s", authenticationType));
        }
    }

    /**
     * Box application client ID
     *
     * @return the clientId
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Box application client ID
     *
     * @param clientId
     *            the clientId to set
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Box application client secret
     *
     * @return the clientSecret
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Box application client secret
     *
     * @param clientSecret
     *            the clientSecret to set
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * Box user name, MUST be provided
     *
     * @return the userName
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Box user name, MUST be provided
     *
     * @param userName
     *            the userName to set
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    /**
     * Box user password, MUST be provided if authSecureStorage is not set, or
     * returns null on first call
     *
     * @return the userPassword
     */
    public String getUserPassword() {
        return userPassword;
    }

    /**
     * Box user password, MUST be provided if authSecureStorage is not set, or
     * returns null on first call
     *
     * @param userPassword
     *            the userPassword to set
     */
    public void setUserPassword(String userPassword) {
        this.userPassword = userPassword;
    }

    /**
     * Custom HTTP params for settings like proxy host
     *
     * @return the httpParams
     */
    public Map<String, Object> getHttpParams() {
        return httpParams;
    }

    /**
     * Custom HTTP params for settings like proxy host
     *
     * @param httpParams
     *            the httpParams to set
     */
    public void setHttpParams(Map<String, Object> httpParams) {
        this.httpParams = httpParams;
    }

    /**
     * To configure security using SSLContextParameters.
     *
     * @return the sslContextParameters
     */
    public SSLContextParameters getSslContextParameters() {
        return sslContextParameters;
    }

    /**
     * To configure security using SSLContextParameters.
     *
     * @param sslContextParameters
     *            the sslContextParameters to set
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        this.sslContextParameters = sslContextParameters;
    }

    /**
     * Custom Access Token Cache for storing and retrieving access tokens.
     *
     * @return Custom Access Token Cache
     */
    public IAccessTokenCache getAccessTokenCache() {
        return accessTokenCache;
    }

    /**
     * Custom Access Token Cache for storing and retrieving access tokens.
     *
     * @param accessTokenCache
     *            - the Custom Access Token Cache
     */
    public void setAccessTokenCache(IAccessTokenCache accessTokenCache) {
        this.accessTokenCache = accessTokenCache;
    }
}
