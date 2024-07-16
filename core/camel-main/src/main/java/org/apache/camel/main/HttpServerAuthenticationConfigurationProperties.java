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
import org.apache.camel.spi.Metadata;

/**
 * Authentication configuration for embedded HTTP server for standalone Camel applications (not Spring Boot / Quarkus).
 */
@Configurer(bootstrap = true)
public class HttpServerAuthenticationConfigurationProperties implements BootstrapCloseable {

    public static final String[] SUPPORTED_AUTHENTICATION_TYPES = { "Basic", "JWT" };

    private HttpServerConfigurationProperties parent;

    @Metadata
    private boolean enabled;

    private String path;

    private HttpServerBasicAuthenticationConfigurationProperties basic;

    private HttpServerJWTAuthenticationConfigurationProperties jwt;

    public HttpServerAuthenticationConfigurationProperties(HttpServerConfigurationProperties parent) {
        basic = new HttpServerBasicAuthenticationConfigurationProperties(this);
        jwt = new HttpServerJWTAuthenticationConfigurationProperties(this);
        this.parent = parent;
    }

    public HttpServerConfigurationProperties end() {
        return parent;
    }

    @Override
    public void close() {
        if (basic != null) {
            basic.close();
            basic = null;
        }
        if (jwt != null) {
            jwt.close();
            jwt = null;
        }
        parent = null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Whether to enable HTTP authentication for embedded server.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Whether to enable HTTP authentication for embedded server.
     */
    public HttpServerAuthenticationConfigurationProperties withEnabled(boolean enabled) {
        this.enabled = enabled;
        return this;
    }

    /**
     * To configure Basic HTTP authentication for embedded server.
     */
    public HttpServerBasicAuthenticationConfigurationProperties basic() {
        if (basic == null) {
            basic = new HttpServerBasicAuthenticationConfigurationProperties(this);
        }
        return basic;
    }

    public HttpServerBasicAuthenticationConfigurationProperties getBasic() {
        return basic;
    }

    /**
     * To configure Basic HTTP authentication for embedded server.
     */
    public void setBasic(
            HttpServerBasicAuthenticationConfigurationProperties basic) {
        this.basic = basic;
    }

    /**
     * To configure JWT HTTP authentication for embedded server.
     */
    public HttpServerJWTAuthenticationConfigurationProperties jwt() {
        if (jwt == null) {
            jwt = new HttpServerJWTAuthenticationConfigurationProperties(this);
        }
        return jwt;
    }

    public HttpServerJWTAuthenticationConfigurationProperties getJWT() {
        return jwt;
    }

    /**
     * To configure JWT HTTP authentication for embedded server.
     */
    public void setJWT(
            HttpServerJWTAuthenticationConfigurationProperties jwt) {
        this.jwt = jwt;
    }

    public String getPath() {
        return path;
    }

    /**
     * Set HTTP url path of embedded server that is protected by authentication configuration.
     */
    public void setPath(String path) {
        this.path = path;
    }

    /**
     * Set HTTP url path of embedded server that is protected by authentication configuration.
     */
    public HttpServerAuthenticationConfigurationProperties withPath(String path) {
        this.path = path;
        return this;
    }
}
