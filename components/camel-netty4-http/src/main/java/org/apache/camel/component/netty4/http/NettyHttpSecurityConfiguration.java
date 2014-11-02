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
package org.apache.camel.component.netty4.http;

import org.apache.camel.LoggingLevel;

/**
 * Security configuration for the {@link NettyHttpConsumer}.
 */
public class NettyHttpSecurityConfiguration {

    private boolean authenticate = true;
    private String constraint = "Basic";
    private String realm;
    private SecurityConstraint securityConstraint;
    private SecurityAuthenticator securityAuthenticator;
    private LoggingLevel loginDeniedLoggingLevel = LoggingLevel.DEBUG;
    private String roleClassName;

    public boolean isAuthenticate() {
        return authenticate;
    }

    /**
     * Whether to enable authentication
     * <p/>
     * This is by default enabled.
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

    public String getRoleClassName() {
        return roleClassName;
    }

    public void setRoleClassName(String roleClassName) {
        this.roleClassName = roleClassName;
    }
}
