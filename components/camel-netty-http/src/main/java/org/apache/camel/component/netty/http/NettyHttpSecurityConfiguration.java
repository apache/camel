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
package org.apache.camel.component.netty.http;

import org.apache.camel.LoggingLevel;
import org.apache.camel.spi.Metadata;

/**
 * Security configuration for the {@link NettyHttpConsumer}.
 */
public class NettyHttpSecurityConfiguration {

    @Metadata(label = "security", defaultValue = "true", description = "Whether to enable authentication")
    private boolean authenticate = true;
    @Metadata(label = "security", defaultValue = "Basic", description = "Security constraint. Currently only Basic is supported.")
    private String constraint = "Basic";
    @Metadata(label = "security", description = "Name of security realm")
    private String realm;
    @Metadata(label = "security", description = "Sets a SecurityConstraint to use for checking if a web resource is restricted or not."
            + " By default this is null, which means all resources is restricted.")
    private SecurityConstraint securityConstraint;
    @Metadata(label = "security", description = " Sets the SecurityAuthenticator to use for authenticating the HttpPrincipal.")
    private SecurityAuthenticator securityAuthenticator;
    @Metadata(label = "security", defaultValue = "DEBUG", description = "Sets a logging level to use for logging denied login attempts (incl stacktraces)")
    private LoggingLevel loginDeniedLoggingLevel = LoggingLevel.DEBUG;

    public boolean isAuthenticate() {
        return authenticate;
    }

    /**
     * Whether to enable authentication
     */
    public void setAuthenticate(boolean authenticate) {
        this.authenticate = authenticate;
    }

    public String getConstraint() {
        return constraint;
    }

    /**
     * The supported restricted.
     * <p/>
     * Currently only Basic is supported.
     */
    public void setConstraint(String constraint) {
        this.constraint = constraint;
    }

    public String getRealm() {
        return realm;
    }

    /**
     * Sets the name of the realm to use.
     */
    public void setRealm(String realm) {
        this.realm = realm;
    }

    public SecurityConstraint getSecurityConstraint() {
        return securityConstraint;
    }

    /**
     * Sets a {@link SecurityConstraint} to use for checking if a web resource is restricted or not
     * <p/>
     * By default this is <tt>null</tt>, which means all resources is restricted.
     */
    public void setSecurityConstraint(SecurityConstraint securityConstraint) {
        this.securityConstraint = securityConstraint;
    }

    public SecurityAuthenticator getSecurityAuthenticator() {
        return securityAuthenticator;
    }

    /**
     * Sets the {@link SecurityAuthenticator} to use for authenticating the {@link HttpPrincipal}.
     */
    public void setSecurityAuthenticator(SecurityAuthenticator securityAuthenticator) {
        this.securityAuthenticator = securityAuthenticator;
    }

    public LoggingLevel getLoginDeniedLoggingLevel() {
        return loginDeniedLoggingLevel;
    }

    /**
     * Sets a logging level to use for logging denied login attempts (incl stacktraces)
     * <p/>
     * This level is by default DEBUG.
     */
    public void setLoginDeniedLoggingLevel(LoggingLevel loginDeniedLoggingLevel) {
        this.loginDeniedLoggingLevel = loginDeniedLoggingLevel;
    }

}