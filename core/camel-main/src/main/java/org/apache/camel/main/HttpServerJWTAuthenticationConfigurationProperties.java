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
package org.apache.camel.main;

import org.apache.camel.spi.BootstrapCloseable;
import org.apache.camel.spi.Configurer;

/**
 * JWT HTTP authentication for embedded server.
 */
@Configurer(bootstrap = true)
public class HttpServerJWTAuthenticationConfigurationProperties
        implements ConfigurationPropertiesWithMandatoryFields, BootstrapCloseable {

    private String keystoreType;

    private String keystorePath;

    private String keystorePassword;

    private HttpServerAuthenticationConfigurationProperties parent;

    public HttpServerJWTAuthenticationConfigurationProperties(HttpServerAuthenticationConfigurationProperties parent) {
        this.parent = parent;
    }

    public String getKeystoreType() {
        return keystoreType;
    }

    /**
     * Type of the keystore used for JWT tokens validation (jks, pkcs12, etc.).
     */
    public void setKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
    }

    /**
     * Type of the keystore used for JWT tokens validation (jks, pkcs12, etc.).
     */
    public HttpServerJWTAuthenticationConfigurationProperties withKeystoreType(String keystoreType) {
        this.keystoreType = keystoreType;
        return this;
    }

    public String getKeystorePath() {
        return keystorePath;
    }

    /**
     * Path to the keystore file used for JWT tokens validation.
     */
    public void setKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
    }

    /**
     * Path to the keystore file used for JWT tokens validation.
     */
    public HttpServerJWTAuthenticationConfigurationProperties withKeystorePath(String keystorePath) {
        this.keystorePath = keystorePath;
        return this;
    }

    public String getKeystorePassword() {
        return keystorePassword;
    }

    /**
     * Password from the keystore used for JWT tokens validation.
     */
    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }

    /**
     * Password from the keystore used for JWT tokens validation.
     */
    public HttpServerJWTAuthenticationConfigurationProperties withKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
        return this;
    }

    @Override
    public void close() {
        parent = null;
    }

    @Override
    public boolean areMandatoryFieldsFilled() {
        boolean keyStoreTypeNotEmpty = keystoreType != null && !"".equals(keystoreType);
        boolean keystorePathNotEmpty = keystorePath != null && !"".equals(keystorePath);
        boolean keystorePasswordNotEmpty = keystorePassword != null && !"".equals(keystorePassword);
        return keyStoreTypeNotEmpty && keystorePathNotEmpty && keystorePasswordNotEmpty;
    }
}
