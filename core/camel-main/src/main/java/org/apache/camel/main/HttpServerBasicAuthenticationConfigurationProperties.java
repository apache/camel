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
 * Basic HTTP authentication for embedded server.
 */
@Configurer(bootstrap = true)
public class HttpServerBasicAuthenticationConfigurationProperties
        implements ConfigurationPropertiesWithMandatoryFields, BootstrapCloseable {

    private String authenticationPropertiesFile;

    private HttpServerAuthenticationConfigurationProperties parent;

    public HttpServerBasicAuthenticationConfigurationProperties(HttpServerAuthenticationConfigurationProperties parent) {
        this.parent = parent;
    }

    public String getAuthenticationPropertiesFile() {
        return authenticationPropertiesFile;
    }

    /**
     * Name of the file that contains authentication info for Vert.x <a
     * href=https://vertx.io/docs/vertx-auth-properties/java/>property file auth provider</a>.
     */
    public void setAuthenticationPropertiesFile(String authenticationPropertiesFile) {
        this.authenticationPropertiesFile = authenticationPropertiesFile;
    }

    /**
     * Name of the file that contains authentication info for Vert.x <a
     * href=https://vertx.io/docs/vertx-auth-properties/java/>property file auth provider</a>.
     */
    public HttpServerBasicAuthenticationConfigurationProperties withAuthenticationPropertiesFile(
            String authenticationPropertiesFile) {
        this.authenticationPropertiesFile = authenticationPropertiesFile;
        return this;
    }

    @Override
    public void close() {
        parent = null;
    }

    @Override
    public boolean areMandatoryFieldsFilled() {
        return authenticationPropertiesFile != null && !"".equals(authenticationPropertiesFile);
    }
}
