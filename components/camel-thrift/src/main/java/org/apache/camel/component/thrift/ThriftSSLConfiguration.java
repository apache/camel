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
package org.apache.camel.component.thrift;

import javax.net.ssl.TrustManagerFactory;

import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

/**
 * Configuration parameters for SSL/TLS security negotiation
 */
@UriParams
public class ThriftSSLConfiguration {
    
    @UriParam(label = "security", defaultValue = ThriftConstants.THRIFT_DEFAULT_SECURITY_PROTOCOL)
    private String securityProtocol = ThriftConstants.THRIFT_DEFAULT_SECURITY_PROTOCOL;
    
    @UriParam(label = "security")
    private String[] cipherSuites;
    
    @UriParam(label = "consumer,security")
    private String keyStorePath;
    
    @UriParam(label = "consumer,security", secret = true)
    private String keyStorePassword;
    
    @UriParam(label = "consumer,security")
    private String keyManagerType = TrustManagerFactory.getDefaultAlgorithm();
    
    @UriParam(label = "consumer,security", defaultValue = ThriftConstants.THRIFT_DEFAULT_SECURITY_STORE_TYPE)
    private String keyStoreType = ThriftConstants.THRIFT_DEFAULT_SECURITY_STORE_TYPE;
    
    @UriParam(label = "producer,security")
    private String trustStorePath;
    
    @UriParam(label = "producer,security", secret = true)
    private String trustPassword;
    
    @UriParam(label = "producer,security")
    private String trustManagerType = TrustManagerFactory.getDefaultAlgorithm();
    
    @UriParam(label = "producer,security", defaultValue = ThriftConstants.THRIFT_DEFAULT_SECURITY_STORE_TYPE)
    private String trustStoreType = ThriftConstants.THRIFT_DEFAULT_SECURITY_STORE_TYPE;
    
    @UriParam(label = "consumer,security", defaultValue = "false")
    private boolean requireClientAuth;
    
    /**
     * Security negotiation protocol
     */
    public String getSecurityProtocol() {
        return securityProtocol;
    }
    
    public void setSecurityProtocol(String protocol) {
        this.securityProtocol = protocol;
    }
    
    /**
     * Cipher suites array
     */
    public String[] getCipherSuites() {
        return cipherSuites;
    }
    
    public void setCipherSuites(String[] cipherSuites) {
        this.cipherSuites = cipherSuites;
    }
    
    /**
     * Path to the key store file
     */
    public String getKeyStorePath() {
        return keyStorePath;
    }
    
    public void setKeyStorePath(String keyStorePath) {
        this.keyStorePath = keyStorePath;
    }
    
    /**
     * Key store password
     */
    public String getKeyStorePassword() {
        return keyStorePassword;
    }
    
    public void setKeyStorePassword(String keyStorePassword) {
        this.keyStorePassword = keyStorePassword;
    }
    
    /**
     * Key store manager type
     */
    public String getKeyManagerType() {
        return keyManagerType;
    }
    
    public void setKeyManagerType(String keyManagerType) {
        this.keyManagerType = keyManagerType;
    }
    
    /**
     * Key store type
     */
    public String getKeyStoreType() {
        return keyStoreType;
    }
    
    public void setKeyStoreType(String keyStoreType) {
        this.keyStoreType = keyStoreType;
    }
    
    /**
     * Path to the trust store file
     */
    public String getTrustStorePath() {
        return trustStorePath;
    }
    
    public void setTrustStorePath(String trustStorePath) {
        this.trustStorePath = trustStorePath;
    }
    
    /**
     * Trust store password
     */
    public String getTrustPassword() {
        return trustPassword;
    }
    
    public void setTrustPassword(String trustPassword) {
        this.trustPassword = trustPassword;
    }
    
    /**
     * Trust store manager type
     */
    public String getTrustManagerType() {
        return trustManagerType;
    }
    
    public void setTrustManagerType(String trustManagerType) {
        this.trustManagerType = trustManagerType;
    }
    
    /**
     * Trust store type
     */
    public String getTrustStoreType() {
        return trustStoreType;
    }
    
    public void setTrustStoreType(String trustStoreType) {
        this.trustStoreType = trustStoreType;
    }
    
    /**
     * Set if client authentication is required
     */
    public boolean isRequireClientAuth() {
        return requireClientAuth;
    }
    
    public void setRequireClientAuth(boolean requireClientAuth) {
        this.requireClientAuth = requireClientAuth;
    }
}