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
package org.apache.camel.component.netty.http;

/**
 * Security configuration for the {@link NettyHttpConsumer}.
 */
public class NettyHttpSecurityConfiguration {

    private boolean authenticate = true;
    private String constraint = "BASIC";
    private String realm;
    private ContextPathMatcher contextPathMatcher;
    private SecurityAuthenticator securityAuthenticator;

    public boolean isAuthenticate() {
        return authenticate;
    }

    public void setAuthenticate(boolean authenticate) {
        this.authenticate = authenticate;
    }

    public String getConstraint() {
        return constraint;
    }

    public void setConstraint(String constraint) {
        this.constraint = constraint;
    }

    public String getRealm() {
        return realm;
    }

    public void setRealm(String realm) {
        this.realm = realm;
    }

    public ContextPathMatcher getContextPathMatcher() {
        return contextPathMatcher;
    }

    public void setContextPathMatcher(ContextPathMatcher contextPathMatcher) {
        this.contextPathMatcher = contextPathMatcher;
    }

    public SecurityAuthenticator getSecurityAuthenticator() {
        return securityAuthenticator;
    }

    public void setSecurityAuthenticator(SecurityAuthenticator securityAuthenticator) {
        this.securityAuthenticator = securityAuthenticator;
    }
}
